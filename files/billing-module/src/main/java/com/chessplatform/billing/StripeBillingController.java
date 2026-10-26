package com.chessplatform.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Web checkout. Stripe is shown as the example; the module is provider-agnostic. */
@RestController
@RequestMapping("/api/billing/stripe")
public class StripeBillingController {
  record CheckoutRequest(String plan) {}   // "monthly" or "yearly"

  private final BillingService billing;
  private final BillingProperties props;

  public StripeBillingController(BillingService billing, BillingProperties props) {
    this.billing = billing;
    this.props = props;
  }

  @PostMapping("/checkout")
  public Map<String, String> checkout(@RequestBody CheckoutRequest req,
                                      @AuthenticationPrincipal Jwt jwt) throws StripeException {
    String priceId = props.stripe().prices().get(req.plan());
    if (priceId == null) throw new BillingException("Unknown plan");
    SessionCreateParams params = SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .addLineItem(SessionCreateParams.LineItem.builder()
            .setPrice(priceId).setQuantity(1L).build())
        .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
            .putMetadata("userId", jwt.getSubject()).build())   // read back by the webhook
        .setClientReferenceId(jwt.getSubject())
        .setSuccessUrl(props.stripe().successUrl())
        .setCancelUrl(props.stripe().cancelUrl())
        .build();
    return Map.of("url", Session.create(params).getUrl());
  }

  /** Public path; authenticated by the Stripe signature over the raw body. */
  @PostMapping("/webhook")
  public ResponseEntity<Void> webhook(@RequestBody String payload,
                                      @RequestHeader("Stripe-Signature") String signature) {
    Event event;
    try {
      event = Webhook.constructEvent(payload, signature, props.stripe().webhookSecret());
    } catch (SignatureVerificationException e) {
      return ResponseEntity.badRequest().build();
    }
    billing.handleStripeEvent(event);
    return ResponseEntity.ok().build();
  }
}
