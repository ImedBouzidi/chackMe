package com.chessplatform.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EntitlementService {
  private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  private final SubscriptionRepository repo;
  private final StringRedisTemplate redis;
  private final BillingProperties props;

  public EntitlementService(SubscriptionRepository repo, StringRedisTemplate redis,
                            BillingProperties props) {
    this.repo = repo;
    this.redis = redis;
    this.props = props;
  }

  public Optional<SubscriptionRecord> activeSubscription(UUID userId) {
    return repo.findEffective(userId, Instant.now(), props.graceDays());
  }

  /** PostgreSQL is the source of truth; Redis is a 5-minute cache that billing events evict. */
  public Plan currentPlan(UUID userId) {
    String key = cacheKey(userId);
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) return Plan.valueOf(cached);
    } catch (RuntimeException e) {
      log.warn("Entitlement cache read failed, using database", e);
    }
    Plan plan = activeSubscription(userId).map(SubscriptionRecord::plan).orElse(Plan.FREE);
    try {
      redis.opsForValue().set(key, plan.name(), CACHE_TTL);
    } catch (RuntimeException e) {
      log.warn("Entitlement cache write failed", e);
    }
    return plan;
  }

  public boolean has(UUID userId, Feature feature) {
    return currentPlan(userId).features().contains(feature);
  }

  public void require(UUID userId, Feature feature) {
    if (!has(userId, feature)) throw new PremiumRequiredException(feature);
  }

  public void evict(UUID userId) {
    try {
      redis.delete(cacheKey(userId));
    } catch (RuntimeException e) {
      log.warn("Entitlement cache evict failed; entry expires within {}", CACHE_TTL, e);
    }
  }

  public EntitlementsResponse snapshot(UUID userId) {
    Optional<SubscriptionRecord> sub = activeSubscription(userId);
    Plan plan = sub.map(SubscriptionRecord::plan).orElse(Plan.FREE);
    return new EntitlementsResponse(plan, plan.features(),
        sub.map(SubscriptionRecord::periodEnd).orElse(null),
        sub.map(SubscriptionRecord::autoRenewing).orElse(false));
  }

  private static String cacheKey(UUID userId) {
    return "ent:" + userId;
  }
}
