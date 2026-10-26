package com.chessplatform.billing;

import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.stripe.model.Event;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
  private static final Logger log = LoggerFactory.getLogger(BillingService.class);

  private final SubscriptionRepository repo;
  private final EntitlementService entitlements;
  private final GooglePlayClient google;
  private final BillingProperties props;

  public BillingService(SubscriptionRepository repo, EntitlementService entitlements,
                        GooglePlayClient google, BillingProperties props) {
    this.repo = repo;
    this.entitlements = entitlements;
    this.google = google;
    this.props = props;
  }

  // ---------------------------------------------------------------- Google Play

  /** Called by the app right after a purchase (and on "restore purchases"). */
  @Transactional
  public EntitlementsResponse verifyGooglePurchase(UUID userId, String purchaseToken) {
    SubscriptionPurchaseV2 purchase = google.fetch(purchaseToken);
    if (!userId.equals(ownerOf(purchase))) {
      throw new BillingException("This purchase belongs to a different account.");
    }
    repo.findByRef(Provider.GOOGLE_PLAY, purchaseToken)
        .filter(existing -> !existing.userId().equals(userId))
        .ifPresent(existing -> { throw new BillingException("Purchase already linked."); });

    SubscriptionRecord record = fromGoogle(userId, purchaseToken, purchase);
    repo.upsert(record);
    acknowledgeIfPending(purchase, record);
    entitlements.evict(userId);
    return entitlements.snapshot(userId);
  }

  /** Called for every Pub/Sub notification: re-read the state instead of trusting the message. */
  @Transactional
  public void refreshGoogle(String purchaseToken) {
    SubscriptionPurchaseV2 purchase = google.fetch(purchaseToken);
    UUID owner = ownerOf(purchase);
    SubscriptionRecord record = fromGoogle(owner, purchaseToken, purchase);
    repo.upsert(record);
    acknowledgeIfPending(purchase, record);
    entitlements.evict(owner);
  }

  @Transactional
  public void handleGoogleNotification(String messageId, DeveloperNotification n) {
    if (!repo.recordEvent(Provider.GOOGLE_PLAY, messageId)) return;   // duplicate delivery
    if (n.subscriptionNotification() != null) {
      refreshGoogle(n.subscriptionNotification().purchaseToken());
    }
    if (n.voidedPurchaseNotification() != null) {
      markRefunded(Provider.GOOGLE_PLAY, n.voidedPurchaseNotification().purchaseToken());
    }
  }

  private UUID ownerOf(SubscriptionPurchaseV2 purchase) {
    var ids = purchase.getExternalAccountIdentifiers();
    if (ids == null || ids.getObfuscatedExternalAccountId() == null) {
      throw new BillingException("Purchase carries no account id.");
    }
    try {
      return UUID.fromString(ids.getObfuscatedExternalAccountId());
    } catch (IllegalArgumentException e) {
      throw new BillingException("Purchase account id is malformed.");
    }
  }

  private SubscriptionRecord fromGoogle(UUID userId, String token, SubscriptionPurchaseV2 p) {
    SubscriptionPurchaseLineItem line = p.getLineItems().get(0);
    Plan plan = props.productPlans().get(line.getProductId());
    if (plan == null) throw new BillingException("Unknown product " + line.getProductId());
    boolean autoRenewing = line.getAutoRenewingPlan() != null
        && Boolean.TRUE.equals(line.getAutoRenewingPlan().getAutoRenewEnabled());
    return new SubscriptionRecord(UUID.randomUUID(), userId, plan, line.getProductId(),
        mapGoogleState(p.getSubscriptionState()), Provider.GOOGLE_PLAY, token, autoRenewing,
        Instant.parse(line.getExpiryTime()));
  }

  /** CANCELED still has access until expiry; PENDING and unknown states grant nothing. */
  static SubscriptionStatus mapGoogleState(String state) {
    return switch (state) {
      case "SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_CANCELED" -> SubscriptionStatus.ACTIVE;
      case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionStatus.GRACE;
      case "SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED" -> SubscriptionStatus.ON_HOLD;
      default -> SubscriptionStatus.EXPIRED;
    };
  }

  private void acknowledgeIfPending(SubscriptionPurchaseV2 p, SubscriptionRecord record) {
    boolean pending = "ACKNOWLEDGEMENT_STATE_PENDING".equals(p.getAcknowledgementState());
    if (pending && record.status() != SubscriptionStatus.EXPIRED) {
      google.acknowledge(record.productId(), record.providerRef());
    }
  }

  // --------------------------------------------------------------------- Stripe

  @Transactional
  public void handleStripeEvent(Event event) {
    if (!repo.recordEvent(Provider.STRIPE, event.getId())) return;
    switch (event.getType()) {
      case "customer.subscription.created",
           "customer.subscription.updated",
           "customer.subscription.deleted" ->
          event.getDataObjectDeserializer().getObject()
              .ifPresent(o -> applyStripe((com.stripe.model.Subscription) o));
      default -> { }   // other events are ignored
    }
  }

  private void applyStripe(com.stripe.model.Subscription s) {
    String userId = s.getMetadata().get("userId");     // set at checkout time
    if (userId == null) {
      log.warn("Stripe subscription {} has no userId metadata", s.getId());
      return;
    }
    var item = s.getItems().getData().get(0);
    Plan plan = props.productPlans().get(item.getPrice().getId());
    if (plan == null) throw new BillingException("Unknown price " + item.getPrice().getId());

    // Stripe API versions before 2025-03-31 expose current_period_end on the subscription;
    // newer versions expose it on each item. Match the version you pin.
    Instant end = Instant.ofEpochSecond(item.getCurrentPeriodEnd());
    boolean autoRenewing = !Boolean.TRUE.equals(s.getCancelAtPeriodEnd());

    repo.upsert(new SubscriptionRecord(UUID.randomUUID(), UUID.fromString(userId), plan,
        item.getPrice().getId(), mapStripeStatus(s.getStatus()), Provider.STRIPE, s.getId(),
        autoRenewing, end));
    entitlements.evict(UUID.fromString(userId));
  }

  static SubscriptionStatus mapStripeStatus(String status) {
    return switch (status) {
      case "active", "trialing" -> SubscriptionStatus.ACTIVE;
      case "past_due" -> SubscriptionStatus.GRACE;
      case "paused" -> SubscriptionStatus.ON_HOLD;
      default -> SubscriptionStatus.EXPIRED;   // canceled, unpaid, incomplete, ...
    };
  }

  // ------------------------------------------------------------ shared / support

  @Transactional
  public void markRefunded(Provider provider, String providerRef) {
    repo.findByRef(provider, providerRef).ifPresent(s -> {
      repo.upsert(new SubscriptionRecord(s.id(), s.userId(), s.plan(), s.productId(),
          SubscriptionStatus.REFUNDED, s.provider(), s.providerRef(), false, Instant.now()));
      entitlements.evict(s.userId());
    });
  }

  /** Support tool and test helper: grant premium without any payment provider. */
  @Transactional
  public EntitlementsResponse grantManual(UUID userId, int days) {
    String ref = "manual:" + userId + ":" + Instant.now().toEpochMilli();
    repo.upsert(new SubscriptionRecord(UUID.randomUUID(), userId, Plan.PREMIUM, "manual",
        SubscriptionStatus.ACTIVE, Provider.MANUAL, ref, false,
        Instant.now().plus(Duration.ofDays(days))));
    entitlements.evict(userId);
    return entitlements.snapshot(userId);
  }
}
