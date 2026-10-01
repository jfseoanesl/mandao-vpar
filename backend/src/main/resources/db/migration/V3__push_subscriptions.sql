-- Spec 012: suscripciones Web Push, una por dispositivo (endpoint).
CREATE TABLE push_subscriptions (
    id           uuid PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint     varchar(1000) NOT NULL UNIQUE,
    p256dh       varchar(200)  NOT NULL,
    auth         varchar(100)  NOT NULL,
    created_at   timestamptz   NOT NULL,
    last_used_at timestamptz
);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
