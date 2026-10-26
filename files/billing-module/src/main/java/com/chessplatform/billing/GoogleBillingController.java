package com.chessplatform.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/google")
public class GoogleBillingController {
  record VerifyRequest(@NotBlank String purchaseToken) {}
  record PubSubPush(Message message, String subscription) {
    record Message(String data, String messageId) {}
  }

  private final BillingService billing;
  private final PubSubAuth pubSubAuth;
  private final BillingProperties props;
  private final ObjectMapper mapper;

  public GoogleBillingController(BillingService billing, PubSubAuth pubSubAuth,
                                 BillingProperties props, ObjectMapper mapper) {
    this.billing = billing;
    this.pubSubAuth = pubSubAuth;
    this.props = props;
    this.mapper = mapper;
  }

  /** JWT-protected: the app calls this after every purchase and on "restore purchases". */
  @PostMapping("/verify")
  public EntitlementsResponse verify(@Valid @RequestBody VerifyRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
    return billing.verifyGooglePurchase(UUID.fromString(jwt.getSubject()), req.purchaseToken());
  }

  /** Public path (no user JWT) but authenticated by the Pub/Sub OIDC token. */
  @PostMapping("/rtdn")
  public ResponseEntity<Void> rtdn(
      @RequestBody PubSubPush push,
      @RequestHeader(value = "Authorization", required = false) String authorization)
      throws Exception {
    pubSubAuth.verify(authorization);
    byte[] json = Base64.getDecoder().decode(push.message().data());
    DeveloperNotification n = mapper.readValue(json, DeveloperNotification.class);
    if (props.google().packageName().equals(n.packageName())) {
      billing.handleGoogleNotification(push.message().messageId(), n);
    }
    return ResponseEntity.noContent().build();   // non-2xx makes Pub/Sub retry
  }
}
