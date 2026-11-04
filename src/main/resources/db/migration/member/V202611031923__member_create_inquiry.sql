CREATE TABLE member.inquiry (
    id            uuid         NOT NULL,
    member_id     uuid         NOT NULL,
    title         varchar(100) NOT NULL,
    content       text         NOT NULL,
    status        varchar(20)  NOT NULL,
    version       bigint       NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT pk_inquiry PRIMARY KEY (id),
    CONSTRAINT fk_inquiry_member FOREIGN KEY (member_id) REFERENCES member.member (id),
    CONSTRAINT ck_inquiry_status CHECK (status IN ('OPEN','ANSWERED'))
);

CREATE INDEX idx_inquiry_member_id_created_at ON member.inquiry (member_id, created_at DESC, id DESC);
CREATE INDEX idx_inquiry_status_created_at ON member.inquiry (status, created_at, id);

CREATE TABLE member.inquiry_answer (
    id            uuid         NOT NULL,
    inquiry_id    uuid         NOT NULL,
    admin_id      uuid         NOT NULL,
    content       text         NOT NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT pk_inquiry_answer PRIMARY KEY (id),
    CONSTRAINT uk_inquiry_answer_inquiry UNIQUE (inquiry_id),
    CONSTRAINT fk_inquiry_answer_inquiry FOREIGN KEY (inquiry_id) REFERENCES member.inquiry (id)
);
