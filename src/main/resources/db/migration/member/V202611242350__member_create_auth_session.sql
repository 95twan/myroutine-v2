CREATE TABLE member.auth_session (
    id            uuid         NOT NULL,
    member_id     uuid         NOT NULL,
    refresh_hash  varchar(64)  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member.member (id)
);

CREATE INDEX idx_auth_session_member ON member.auth_session (member_id);
CREATE INDEX idx_auth_session_expires_at ON member.auth_session (expires_at);
