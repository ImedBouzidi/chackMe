package com.chessplatform.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Decoded payload of a Google Play real-time developer notification. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeveloperNotification(
    String packageName,
    SubscriptionNotification subscriptionNotification,
    VoidedPurchaseNotification voidedPurchaseNotification) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SubscriptionNotification(int notificationType, String purchaseToken,
                                         String subscriptionId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VoidedPurchaseNotification(String purchaseToken, String orderId,
                                           int productType) {}
}
