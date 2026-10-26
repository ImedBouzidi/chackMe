package com.chessplatform.billing;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionRecord(
    UUID id, UUID userId, Plan plan, String productId, SubscriptionStatus status,
    Provider provider, String providerRef, boolean autoRenewing, Instant periodEnd) {}
