package com.chessplatform.billing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {
  private final JdbcClient jdbc;

  public SubscriptionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The subscription that currently grants access, if any. */
  public Optional<SubscriptionRecord> findEffective(UUID userId, Instant now, int graceDays) {
    return jdbc.sql("""
            select * from subscription
            where user_id = :user
              and ((status = 'ACTIVE' and period_end > :now)
                or (status = 'GRACE' and period_end + (:grace * interval '1 day') > :now))
            order by period_end desc
            limit 1""")
        .param("user", userId)
        .param("now", Timestamp.from(now))
        .param("grace", graceDays)
        .query(this::map)
        .optional();
  }

  public Optional<SubscriptionRecord> findByRef(Provider provider, String ref) {
    return jdbc.sql("select * from subscription where provider = :p and provider_ref = :r")
        .param("p", provider.name())
        .param("r", ref)
        .query(this::map)
        .optional();
  }

  /** Insert or update by (provider, provider_ref). user_id is never changed on update. */
  public void upsert(SubscriptionRecord s) {
    jdbc.sql("""
            insert into subscription (id, user_id, plan, product_id, status, provider,
                                      provider_ref, auto_renewing, period_end)
            values (:id, :user, :plan, :product, :status, :provider, :ref, :auto, :end)
            on conflict (provider, provider_ref) do update set
              product_id = excluded.product_id,
              status = excluded.status,
              auto_renewing = excluded.auto_renewing,
              period_end = excluded.period_end,
              updated_at = now()""")
        .param("id", s.id())
        .param("user", s.userId())
        .param("plan", s.plan().name())
        .param("product", s.productId())
        .param("status", s.status().name())
        .param("provider", s.provider().name())
        .param("ref", s.providerRef())
        .param("auto", s.autoRenewing())
        .param("end", Timestamp.from(s.periodEnd()))
        .update();
  }

  /** True the first time an event id is seen. Call inside the handler's transaction. */
  public boolean recordEvent(Provider provider, String eventId) {
    return jdbc.sql("""
            insert into billing_event (event_id, provider) values (:id, :p)
            on conflict do nothing""")
        .param("id", provider.name() + ":" + eventId)
        .param("p", provider.name())
        .update() == 1;
  }

  private SubscriptionRecord map(ResultSet rs, int row) throws SQLException {
    return new SubscriptionRecord(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        Plan.valueOf(rs.getString("plan")),
        rs.getString("product_id"),
        SubscriptionStatus.valueOf(rs.getString("status")),
        Provider.valueOf(rs.getString("provider")),
        rs.getString("provider_ref"),
        rs.getBoolean("auto_renewing"),
        rs.getTimestamp("period_end").toInstant());
  }
}
