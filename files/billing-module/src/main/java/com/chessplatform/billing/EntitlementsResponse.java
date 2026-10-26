package com.chessplatform.billing;

import java.time.Instant;
import java.util.Set;

public record EntitlementsResponse(
    Plan plan, Set<Feature> features, Instant periodEnd, boolean autoRenewing) {}
