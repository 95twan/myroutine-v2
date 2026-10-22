CREATE TABLE common.outbox_event (
    id              uuid         NOT NULL,
    aggregate_type  varchar(50)  NOT NULL,
    aggregate_id    uuid         NOT NULL,
    event_type      varchar(100) NOT NULL,
    topic           varchar(100) NOT NULL,
    payload         jsonb        NOT NULL,
    trace_id        varchar(64),
    status          varchar(20)  NOT NULL,
    attempts        int          NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL,
    locked_until    timestamptz,
    sent_at         timestamptz,
    last_error      varchar(500),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('READY','PROCESSING','SENT','DEAD'))
);

CREATE INDEX idx_outbox_event_ready ON common.outbox_event (next_attempt_at) WHERE status = 'READY';
CREATE INDEX idx_outbox_event_processing ON common.outbox_event (locked_until) WHERE status = 'PROCESSING';
