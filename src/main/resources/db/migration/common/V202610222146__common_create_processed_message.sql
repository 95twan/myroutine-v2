CREATE TABLE common.processed_message (
    consumer        varchar(100) NOT NULL,
    event_id        uuid         NOT NULL,
    processed_at    timestamptz  NOT NULL,
    CONSTRAINT pk_processed_message PRIMARY KEY (consumer, event_id)
);
