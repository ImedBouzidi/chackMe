package com.chessplatform.billing;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** One call that every limited endpoint makes. */
@Component
public class AccessGuard {
  private final EntitlementService entitlements;
  private final QuotaService quotas;

  public AccessGuard(EntitlementService entitlements, QuotaService quotas) {
    this.entitlements = entitlements;
    this.quotas = quotas;
  }

  /** Premium users with the feature pass; everyone else consumes free quota. */
  public boolean consumeUnlessPremium(UUID userId, Feature unlimited, QuotaKey quota) {
    if (entitlements.has(userId, unlimited)) return true;
    quotas.consumeOrThrow(userId, quota);
    return false;
  }
}
