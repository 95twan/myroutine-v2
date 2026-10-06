CREATE SCHEMA IF NOT EXISTS shop;

CREATE TABLE shop.shop
(
    id              uuid         NOT NULL,
    member_id       uuid         NOT NULL,
    name            varchar(50)  NOT NULL,
    business_number varchar(10)  NOT NULL,
    email           varchar(255) NOT NULL,
    phone           varchar(20)  NOT NULL,
    address         varchar(255) NOT NULL,
    status          varchar(20)  NOT NULL,
    closed_at       timestamptz,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_shop PRIMARY KEY (id),
    CONSTRAINT uk_shop_business_number UNIQUE (business_number),
    CONSTRAINT ck_shop_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_shop_member_id ON shop.shop (member_id);
