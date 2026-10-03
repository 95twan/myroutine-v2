CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.member (
    id            uuid         NOT NULL,
    email         varchar(255) NOT NULL,
    nickname      varchar(20)  NOT NULL,
    name          varchar(50)  NOT NULL,
    phone         varchar(20),
    password_hash varchar(100),
    status        varchar(20)  NOT NULL,
    role          varchar(20)  NOT NULL,
    version       bigint       NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_member PRIMARY KEY (id),
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname UNIQUE (nickname),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'BANNED', 'WITHDRAWN')),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'SELLER', 'ADMIN'))
);
