CREATE TABLE payment.payment_cancel
(
    id                 uuid         NOT NULL,
    payment_id         uuid         NOT NULL,
    idempotency_key    varchar(100) NOT NULL,
    amount             bigint       NOT NULL,
    reason             varchar(200) NOT NULL,
    status             varchar(20)  NOT NULL,
    failure_code       varchar(100),
    version            bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_payment_cancel PRIMARY KEY (id),
    CONSTRAINT fk_payment_cancel_payment FOREIGN KEY (payment_id) REFERENCES payment.payment (id),
    CONSTRAINT uk_payment_cancel_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_cancel_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_cancel_status CHECK (status IN ('REQUESTED','DONE','FAILED','UNKNOWN'))
);
