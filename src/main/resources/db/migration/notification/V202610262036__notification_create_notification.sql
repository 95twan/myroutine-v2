CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notification (
    id              uuid         NOT NULL,
    member_id       uuid         NOT NULL,
    type            varchar(40)  NOT NULL,
    channel         varchar(20)  NOT NULL DEFAULT 'EMAIL',
    recipient       varchar(255) NOT NULL,
    title           varchar(200) NOT NULL,
    body            text         NOT NULL,
    dedup_key       varchar(200) NOT NULL,
    status          varchar(20)  NOT NULL,
    attempts        int          NOT NULL DEFAULT 0,
    last_error      varchar(500),
    next_attempt_at timestamptz  NOT NULL,
    sent_at         timestamptz,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT uk_notification_dedup_key UNIQUE (dedup_key),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING','SENT','FAILED'))
);

CREATE INDEX idx_notification_pending ON notification.notification (next_attempt_at) WHERE status = 'PENDING';
