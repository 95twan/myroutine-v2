CREATE SCHEMA IF NOT EXISTS member;

CREATE TABLE member.social_account (
    id               uuid          NOT NULL,
    member_id        uuid          NOT NULL,
    provider         varchar(20)   NOT NULL,
    provider_user_id varchar(100)  NOT NULL,
    provider_email   varchar(255),
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_social_account PRIMARY KEY (id),
    CONSTRAINT fk_social_account_member FOREIGN KEY (member_id) REFERENCES member.member (id),
    CONSTRAINT uk_social_account_member_provider UNIQUE (member_id, provider),
    CONSTRAINT ck_social_account_provider CHECK (provider IN ('KAKAO','GOOGLE','NAVER')),
    CONSTRAINT uk_social_account_provider_user UNIQUE (provider, provider_user_id)
);
