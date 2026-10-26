package com.chessplatform.billing;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class QuotaService {
  // INCR and EXPIRE in one atomic step, so a crash cannot leave a counter without a TTL.
  private static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>(
      "local n = redis.call('INCR', KEYS[1]) "
          + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
          + "return n", Long.class);

  private final StringRedisTemplate redis;
  private final BillingProperties props;

  public QuotaService(StringRedisTemplate redis, BillingProperties props) {
    this.redis = redis;
    this.props = props;
  }

  /** Counts one use; throws when the free daily limit is exceeded. Days are UTC. */
  public void consumeOrThrow(UUID userId, QuotaKey key) {
    int limit = props.freeDailyLimits().getOrDefault(key, Integer.MAX_VALUE);
    Long used = redis.execute(INCR_WITH_TTL, List.of(counterKey(userId, key)), "172800");
    if (used != null && used > limit) {
      redis.opsForValue().decrement(counterKey(userId, key)); // do not count rejected attempts
      throw new QuotaExceededException(key, limit);
    }
  }

  /** Give a use back when the work was rejected afterwards (for example enqueue failed). */
  public void refund(UUID userId, QuotaKey key) {
    redis.opsForValue().decrement(counterKey(userId, key));
  }

  private static String counterKey(UUID userId, QuotaKey key) {
    return "quota:%s:%s:%s".formatted(key.name().toLowerCase(), userId,
        LocalDate.now(ZoneOffset.UTC));
  }
}
