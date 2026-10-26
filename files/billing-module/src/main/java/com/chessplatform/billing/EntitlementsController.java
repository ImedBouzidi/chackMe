package com.chessplatform.billing;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class EntitlementsController {
  record GrantRequest(UUID userId, int days) {}

  private final EntitlementService entitlements;
  private final BillingService billing;

  public EntitlementsController(EntitlementService entitlements, BillingService billing) {
    this.entitlements = entitlements;
    this.billing = billing;
  }

  /** What the clients use to show or hide premium UI. The server still enforces every limit. */
  @GetMapping("/api/me/entitlements")
  public EntitlementsResponse mine(@AuthenticationPrincipal Jwt jwt) {
    return entitlements.snapshot(UUID.fromString(jwt.getSubject()));
  }

  /** Support and testing: grant premium without a payment. Restricted to the admin role. */
  @PreAuthorize("hasRole('admin')")
  @PostMapping("/api/admin/billing/grant")
  public EntitlementsResponse grant(@RequestBody GrantRequest req) {
    return billing.grantManual(req.userId(), req.days());
  }
}
