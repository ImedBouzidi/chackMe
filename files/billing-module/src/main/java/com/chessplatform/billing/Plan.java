package com.chessplatform.billing;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum Plan {
  FREE(EnumSet.noneOf(Feature.class)),
  PREMIUM(EnumSet.allOf(Feature.class));   // move to configuration when tiers multiply

  private final Set<Feature> features;

  Plan(Set<Feature> features) {
    this.features = Collections.unmodifiableSet(features);
  }

  public Set<Feature> features() {
    return features;
  }
}
