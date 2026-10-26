-- Billing schema (Flyway). Keep it in its own schema/folder per service (section 6.3).
create table subscription (
  id             uuid primary key,
  user_id        uuid        not null,          -- Keycloak "sub" claim
  plan           text        not null,          -- PREMIUM (FREE has no row)
  product_id     text        not null,          -- store product / price id
  status         text        not null
                 check (status in ('ACTIVE','GRACE','ON_HOLD','EXPIRED','REFUNDED')),
  provider       text        not null
                 check (provider in ('GOOGLE_PLAY','STRIPE','MANUAL')),
  provider_ref   text        not null,          -- purchase token / Stripe sub id
  auto_renewing  boolean     not null default true,
  period_end     timestamptz not null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  unique (provider, provider_ref)               -- one row per purchase, upsert-safe
);
create index idx_subscription_user on subscription (user_id);
create index idx_subscription_period_end on subscription (period_end);

-- Idempotency: every webhook / Pub/Sub message is recorded once.
create table billing_event (
  event_id    text primary key,                 -- "<PROVIDER>:<provider event id>"
  provider    text        not null,
  received_at timestamptz not null default now()
);
