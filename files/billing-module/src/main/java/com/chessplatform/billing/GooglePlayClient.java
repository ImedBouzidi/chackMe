package com.chessplatform.billing;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.google.api.services.androidpublisher.model.SubscriptionPurchasesAcknowledgeRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Thin wrapper around the Google Play Developer API (androidpublisher v3). */
@Component
public class GooglePlayClient {
  private final AndroidPublisher publisher;
  private final String packageName;

  public GooglePlayClient(BillingProperties props) throws Exception {
    this.packageName = props.google().packageName();
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
        .createScoped(AndroidPublisherScopes.ANDROIDPUBLISHER);
    this.publisher = new AndroidPublisher.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance(),
        new HttpCredentialsAdapter(credentials))
        .setApplicationName("chess-platform-billing")
        .build();
  }

  /** Current state of a subscription purchase, straight from Google (never trust the client). */
  public SubscriptionPurchaseV2 fetch(String purchaseToken) {
    try {
      return publisher.purchases().subscriptionsv2().get(packageName, purchaseToken).execute();
    } catch (IOException e) {
      throw new BillingException("Google Play lookup failed", e);
    }
  }

  /** Purchases not acknowledged within 3 days are refunded automatically by Google. */
  public void acknowledge(String productId, String purchaseToken) {
    try {
      publisher.purchases().subscriptions()
          .acknowledge(packageName, productId, purchaseToken,
              new SubscriptionPurchasesAcknowledgeRequest())
          .execute();
    } catch (IOException e) {
      throw new BillingException("Google Play acknowledge failed", e);
    }
  }
}
