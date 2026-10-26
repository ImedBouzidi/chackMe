package com.chessplatform.billing;

public class PremiumRequiredException extends RuntimeException {
  private final Feature feature;
  public PremiumRequiredException(Feature feature) {
    super("Premium required: " + feature);
    this.feature = feature;
  }
  public Feature feature() { return feature; }
}
