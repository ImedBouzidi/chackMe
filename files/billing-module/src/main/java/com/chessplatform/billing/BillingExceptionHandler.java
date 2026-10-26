package com.chessplatform.billing;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BillingExceptionHandler {

  @ExceptionHandler(PremiumRequiredException.class)
  ResponseEntity<ProblemDetail> premiumRequired(PremiumRequiredException e) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.PAYMENT_REQUIRED, "This feature needs a premium plan.");
    pd.setProperty("code", "PREMIUM_REQUIRED");
    pd.setProperty("feature", e.feature().name());
    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(pd);
  }

  @ExceptionHandler(QuotaExceededException.class)
  ResponseEntity<ProblemDetail> quotaExceeded(QuotaExceededException e) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.TOO_MANY_REQUESTS, "Daily free limit reached.");
    pd.setProperty("code", "QUOTA_EXCEEDED");
    pd.setProperty("quota", e.key().name());
    pd.setProperty("limit", e.limit());
    pd.setProperty("resetsAt", LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay()
        .toInstant(ZoneOffset.UTC).toString());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(pd);
  }

  @ExceptionHandler(BillingException.class)
  ResponseEntity<ProblemDetail> billing(BillingException e) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
        e.getMessage());
    pd.setProperty("code", "BILLING_ERROR");
    return ResponseEntity.unprocessableEntity().body(pd);
  }
}
