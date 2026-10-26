package com.chessplatform.billing;

public class QuotaExceededException extends RuntimeException {
  private final QuotaKey key;
  private final int limit;
  public QuotaExceededException(QuotaKey key, int limit) {
    super("Daily quota reached: " + key);
    this.key = key;
    this.limit = limit;
  }
  public QuotaKey key() { return key; }
  public int limit() { return limit; }
}
