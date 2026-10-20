CREATE SCHEMA IF NOT EXISTS payment;

CREATE TABLE payment.payment
(
    id                 uuid         NOT NULL,
    order_id           uuid         NOT NULL,
    member_id          uuid         NOT NULL,
    amount             bigint       NOT NULL,
    cancelled_amount   bigint       NOT NULL DEFAULT 0,
    pg_order_id        varchar(64)  NOT NULL,
    payment_key        varchar(200) NOT NULL,
    billing_key_id     uuid,
    method             varchar(30),
    status             varchar(30)  NOT NULL,
    failure_code       varchar(100),
    approved_at        timestamptz,
    reconcile_attempts int          NOT NULL DEFAULT 0,
    version            bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uk_payment_order UNIQUE (order_id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_cancelled CHECK (cancelled_amount >= 0 AND cancelled_amount <= amount),
    CONSTRAINT uk_payment_pg_order_id UNIQUE (pg_order_id),
    CONSTRAINT uk_payment_payment_key UNIQUE (payment_key),
    CONSTRAINT ck_payment_status CHECK (status IN ('IN_PROGRESS','APPROVED','FAILED','UNKNOWN','PARTIAL_CANCELLED','CANCELLED'))
);
