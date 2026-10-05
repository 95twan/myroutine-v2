CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.member_address (
    id            uuid         NOT NULL,
    member_id     uuid         NOT NULL,
    recipient     varchar(50)  NOT NULL,
    phone         varchar(20)  NOT NULL,
    zipcode       varchar(10)  NOT NULL,
    address1      varchar(200) NOT NULL,
    address2      varchar(200),
    is_default    boolean      NOT NULL DEFAULT false,
    version       bigint       NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_member_address PRIMARY KEY (id),
    CONSTRAINT fk_member_address_member FOREIGN KEY (member_id) REFERENCES member.member (id)
);

CREATE INDEX idx_member_address_member_id ON member.member_address (member_id);
CREATE UNIQUE INDEX uk_member_address_default ON member.member_address (member_id) WHERE is_default;
