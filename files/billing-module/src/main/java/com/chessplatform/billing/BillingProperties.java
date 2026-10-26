package com.chessplatform.billing;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
    Map<QuotaKey, Integer> freeDailyLimits,   // e.g. REVIEW=3, PUZZLE_ATTEMPT=20
    int graceDays,                            // access kept while a renewal payment is retried
    Map<String, Plan> productPlans,           // store product id / Stripe price id -> plan
    Google google,
    Stripe stripe) {

  public record Google(String packageName, String rtdnAudience, String rtdnServiceAccount) {}

  public record Stripe(String webhookSecret, String successUrl, String cancelUrl,
                       Map<String, String> prices) {}   // "monthly"/"yearly" -> price id
}
