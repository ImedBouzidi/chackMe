package com.chessplatform.billing;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Verifies the OIDC token that Pub/Sub attaches to push requests. */
@Component
public class PubSubAuth {
  private final GoogleIdTokenVerifier verifier;
  private final String expectedServiceAccount;

  public PubSubAuth(BillingProperties props) throws Exception {
    this.expectedServiceAccount = props.google().rtdnServiceAccount();
    this.verifier = new GoogleIdTokenVerifier.Builder(
        GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
        .setAudience(List.of(props.google().rtdnAudience()))
        .build();
  }

  public void verify(String authorizationHeader) {
    try {
      if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        throw new IllegalArgumentException("missing bearer token");
      }
      GoogleIdToken token = verifier.verify(authorizationHeader.substring(7));
      if (token == null
          || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())
          || !expectedServiceAccount.equals(token.getPayload().getEmail())) {
        throw new IllegalArgumentException("unexpected caller");
      }
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }
}
