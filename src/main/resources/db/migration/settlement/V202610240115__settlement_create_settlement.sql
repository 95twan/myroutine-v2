CREATE SCHEMA IF NOT EXISTS settlement;

CREATE TABLE settlement.settlement
(
    id               uuid         NOT NULL,
    shop_id          uuid         NOT NULL,
    period_start     date         NOT NULL,
    period_end       date         NOT NULL,
    gross_amount     bigint       NOT NULL DEFAULT 0,
    fee_amount       bigint       NOT NULL DEFAULT 0,
    net_amount       bigint       NOT NULL DEFAULT 0,
    fee_rate_bp      int          NOT NULL,
    item_count       int          NOT NULL DEFAULT 0,
    status           varchar(20)  NOT NULL,
    payout_attempts  int          NOT NULL DEFAULT 0,
    payout_reference varchar(100),
    last_error       varchar(500),
    paid_at          timestamptz,
    version          bigint       NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT pk_settlement PRIMARY KEY (id),
    CONSTRAINT uk_settlement_shop_period UNIQUE (shop_id, period_start),
    CONSTRAINT ck_settlement_net CHECK (net_amount = gross_amount - fee_amount),
    CONSTRAINT ck_settlement_status CHECK (status IN ('CALCULATED','PAID','PAYOUT_FAILED'))
);

CREATE INDEX idx_settlement_status ON settlement.settlement (status);
CREATE INDEX idx_settlement_shop_id_period_start ON settlement.settlement (shop_id, period_start DESC);

CREATE TABLE settlement.settlement_item
(
    id              uuid         NOT NULL,
    order_line_id   uuid         NOT NULL,
    shop_id         uuid         NOT NULL,
    order_id        uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    amount          bigint       NOT NULL,
    confirmed_at    timestamptz  NOT NULL,
    settlement_id   uuid,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_settlement_item PRIMARY KEY (id),
    CONSTRAINT uk_settlement_item_order_line UNIQUE (order_line_id),
    CONSTRAINT ck_settlement_item_amount CHECK (amount >= 0),
    CONSTRAINT fk_settlement_item_settlement FOREIGN KEY (settlement_id) REFERENCES settlement.settlement (id)
);

CREATE INDEX idx_settlement_item_unsettled ON settlement.settlement_item (shop_id, confirmed_at) WHERE settlement_id IS NULL;
CREATE INDEX idx_settlement_item_settlement_id ON settlement.settlement_item (settlement_id);
