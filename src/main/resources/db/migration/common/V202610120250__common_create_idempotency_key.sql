CREATE TABLE common.idempotency_key (
    member_id       uuid         NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    request_hash    varchar(64)  NOT NULL,
    status          varchar(20)  NOT NULL,
    response_status int,
    response_body   jsonb,
    expires_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_idempotency_key PRIMARY KEY (member_id, idempotency_key),
    CONSTRAINT ck_idempotency_key_status CHECK (status IN ('PROCESSING','COMPLETED'))
);

CREATE INDEX idx_idempotency_key_expires_at ON common.idempotency_key (expires_at);
