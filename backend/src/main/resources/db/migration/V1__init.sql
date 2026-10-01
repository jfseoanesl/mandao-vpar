-- Mandao MVP · esquema inicial
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE companies (
    id           uuid PRIMARY KEY,
    name         varchar(160) NOT NULL,
    nit          varchar(20),
    plan         varchar(20)  NOT NULL,
    monthly_fee  bigint       NOT NULL DEFAULT 0,
    active       boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL
);

CREATE TABLE users (
    id                  uuid PRIMARY KEY,
    full_name           varchar(120) NOT NULL,
    phone               varchar(15)  NOT NULL UNIQUE,
    email               varchar(160),
    password_hash       varchar(100) NOT NULL,
    role                varchar(20)  NOT NULL,
    company_id          uuid REFERENCES companies (id),
    privacy_accepted_at timestamptz  NOT NULL,
    acquisition_source  varchar(60),
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL
);
CREATE INDEX idx_users_company ON users (company_id);

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id),
    token_hash varchar(64) NOT NULL UNIQUE,
    family_id  uuid        NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked    boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

CREATE TABLE courier_profiles (
    user_id          uuid PRIMARY KEY REFERENCES users (id),
    vehicle_type     varchar(20),
    plate            varchar(10),
    payout_method    varchar(20),
    payout_account   varchar(40),
    status           varchar(20) NOT NULL,
    rejection_reason varchar(300),
    available        boolean     NOT NULL DEFAULT false,
    lat              double precision,
    lng              double precision,
    last_ping_at     timestamptz,
    approved_at      timestamptz,
    version          bigint      NOT NULL DEFAULT 0
);
CREATE INDEX idx_courier_geo ON courier_profiles
    USING gist (geography(ST_SetSRID(ST_MakePoint(lng, lat), 4326)))
    WHERE lat IS NOT NULL AND lng IS NOT NULL;

CREATE TABLE courier_documents (
    id           uuid PRIMARY KEY,
    courier_id   uuid        NOT NULL REFERENCES users (id),
    type         varchar(30) NOT NULL,
    object_key   varchar(200) NOT NULL,
    content_type varchar(60) NOT NULL,
    uploaded_at  timestamptz NOT NULL,
    UNIQUE (courier_id, type)
);

CREATE TABLE tariffs (
    id                 uuid PRIMARY KEY,
    version            integer      NOT NULL UNIQUE,
    base_fare          bigint       NOT NULL,
    per_km             bigint       NOT NULL,
    included_wait_min  integer      NOT NULL,
    per_extra_min      bigint       NOT NULL,
    per_extra_stop     bigint       NOT NULL,
    complexity_medium  bigint       NOT NULL,
    complexity_complex bigint       NOT NULL,
    commission_pct     numeric(5, 4) NOT NULL,
    active             boolean      NOT NULL,
    created_by         uuid,
    created_at         timestamptz  NOT NULL
);
INSERT INTO tariffs (id, version, base_fare, per_km, included_wait_min, per_extra_min, per_extra_stop,
                     complexity_medium, complexity_complex, commission_pct, active, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 1, 5000, 900, 10, 250, 2000, 2500, 5000, 0.2250, true, now());

CREATE TABLE errands (
    id                  uuid PRIMARY KEY,
    code                varchar(12)  NOT NULL UNIQUE,
    client_id           uuid         NOT NULL REFERENCES users (id),
    company_id          uuid REFERENCES companies (id),
    courier_id          uuid REFERENCES users (id),
    category            varchar(20)  NOT NULL,
    description         varchar(2000) NOT NULL,
    complexity          varchar(10)  NOT NULL,
    pricing_mode        varchar(12)  NOT NULL,
    status              varchar(20)  NOT NULL,
    estimated_wait_min  integer      NOT NULL DEFAULT 0,
    distance_km         numeric(8, 2) NOT NULL,
    quoted_price        bigint       NOT NULL,
    total_price         bigint       NOT NULL,
    platform_fee        bigint       NOT NULL,
    courier_payout      bigint       NOT NULL,
    tariff_version      integer      NOT NULL,
    purchase_budget     bigint       NOT NULL DEFAULT 0,
    payment_method      varchar(20)  NOT NULL,
    scheduled_for       timestamptz,
    repeat_of           uuid REFERENCES errands (id),
    start_otp_enc       varchar(80)  NOT NULL,
    end_otp_enc         varchar(80)  NOT NULL,
    otp_failed_attempts integer      NOT NULL DEFAULT 0,
    needs_attention     boolean      NOT NULL DEFAULT false,
    attention_reason    varchar(200),
    cancel_reason       varchar(300),
    created_at          timestamptz  NOT NULL,
    assigned_at         timestamptz,
    started_at          timestamptz,
    delivered_at        timestamptz,
    completed_at        timestamptz,
    cancelled_at        timestamptz,
    courier_paid_at     timestamptz,
    version             bigint       NOT NULL DEFAULT 0
);
CREATE INDEX idx_errands_client ON errands (client_id, created_at DESC);
CREATE INDEX idx_errands_courier ON errands (courier_id, created_at DESC);
CREATE INDEX idx_errands_company ON errands (company_id, created_at DESC);
CREATE INDEX idx_errands_status ON errands (status);

CREATE TABLE errand_stops (
    id            uuid PRIMARY KEY,
    errand_id     uuid         NOT NULL REFERENCES errands (id) ON DELETE CASCADE,
    seq           integer      NOT NULL,
    address       varchar(250) NOT NULL,
    lat           double precision NOT NULL,
    lng           double precision NOT NULL,
    instructions  varchar(500),
    contact_name  varchar(120),
    contact_phone varchar(15)
);
CREATE INDEX idx_stops_errand ON errand_stops (errand_id, seq);

CREATE TABLE errand_status_history (
    id          uuid PRIMARY KEY,
    errand_id   uuid        NOT NULL REFERENCES errands (id) ON DELETE CASCADE,
    from_status varchar(20),
    to_status   varchar(20) NOT NULL,
    actor_id    uuid,
    note        varchar(300),
    created_at  timestamptz NOT NULL
);
CREATE INDEX idx_history_errand ON errand_status_history (errand_id, created_at);

CREATE TABLE errand_offers (
    id             uuid PRIMARY KEY,
    errand_id      uuid        NOT NULL REFERENCES errands (id) ON DELETE CASCADE,
    courier_id     uuid        NOT NULL REFERENCES users (id),
    amount         bigint      NOT NULL,
    message        varchar(300),
    counter_amount bigint,
    status         varchar(12) NOT NULL,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL
);
CREATE INDEX idx_offers_errand ON errand_offers (errand_id);

CREATE TABLE evidences (
    id           uuid PRIMARY KEY,
    errand_id    uuid        NOT NULL REFERENCES errands (id) ON DELETE CASCADE,
    uploader_id  uuid        NOT NULL REFERENCES users (id),
    type         varchar(12) NOT NULL,
    object_key   varchar(200),
    content_type varchar(60),
    note         varchar(1000),
    amount       bigint,
    lat          double precision,
    lng          double precision,
    created_at   timestamptz NOT NULL
);
CREATE INDEX idx_evidences_errand ON evidences (errand_id, created_at);

CREATE TABLE location_pings (
    id         bigserial PRIMARY KEY,
    courier_id uuid        NOT NULL REFERENCES users (id),
    errand_id  uuid REFERENCES errands (id) ON DELETE CASCADE,
    lat        double precision NOT NULL,
    lng        double precision NOT NULL,
    accuracy   double precision,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_pings_errand ON location_pings (errand_id, created_at);
CREATE INDEX idx_pings_created ON location_pings (created_at);

CREATE TABLE payments (
    id           uuid PRIMARY KEY,
    errand_id    uuid        NOT NULL UNIQUE REFERENCES errands (id) ON DELETE CASCADE,
    method       varchar(20) NOT NULL,
    amount       bigint      NOT NULL,
    status       varchar(12) NOT NULL,
    reference    varchar(80),
    confirmed_by uuid,
    confirmed_at timestamptz,
    created_at   timestamptz NOT NULL
);

CREATE TABLE ledger_entries (
    id         uuid PRIMARY KEY,
    errand_id  uuid        NOT NULL REFERENCES errands (id) ON DELETE CASCADE,
    type       varchar(20) NOT NULL,
    amount     bigint      NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_ledger_errand ON ledger_entries (errand_id);

CREATE TABLE ratings (
    id         uuid PRIMARY KEY,
    errand_id  uuid        NOT NULL UNIQUE REFERENCES errands (id) ON DELETE CASCADE,
    rater_id   uuid        NOT NULL REFERENCES users (id),
    courier_id uuid        NOT NULL REFERENCES users (id),
    stars      smallint    NOT NULL CHECK (stars BETWEEN 1 AND 5),
    tags       varchar(200),
    comment    varchar(500),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_ratings_courier ON ratings (courier_id);

CREATE TABLE saved_addresses (
    id         uuid PRIMARY KEY,
    company_id uuid         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    label      varchar(60)  NOT NULL,
    address    varchar(250) NOT NULL,
    lat        double precision NOT NULL,
    lng        double precision NOT NULL,
    created_at timestamptz  NOT NULL
);

CREATE TABLE analytics_events (
    id         bigserial PRIMARY KEY,
    name       varchar(40) NOT NULL,
    user_id    uuid,
    errand_id  uuid,
    props      varchar(500),
    utm_source varchar(60),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_events_name_created ON analytics_events (name, created_at);

CREATE TABLE audit_log (
    id         bigserial PRIMARY KEY,
    actor_id   uuid,
    action     varchar(60)  NOT NULL,
    entity     varchar(40)  NOT NULL,
    entity_id  varchar(40),
    detail     varchar(500),
    created_at timestamptz  NOT NULL
);
