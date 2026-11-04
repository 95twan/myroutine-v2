CREATE SCHEMA IF NOT EXISTS review;

CREATE TABLE review.review
(
    id                 uuid         NOT NULL,
    order_line_id      uuid         NOT NULL,
    product_id         uuid         NOT NULL,
    member_id          uuid         NOT NULL,
    rating             int          NOT NULL,
    content            text         NOT NULL,
    like_count         int          NOT NULL DEFAULT 0,
    status             varchar(20)  NOT NULL,
    version            bigint       NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_review PRIMARY KEY (id),
    CONSTRAINT uk_review_order_line UNIQUE (order_line_id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_review_like_count CHECK (like_count >= 0),
    CONSTRAINT ck_review_status CHECK (status IN ('ACTIVE','DELETED'))
);
CREATE INDEX idx_review_product_id_created_at ON review.review (product_id, created_at DESC, id DESC) WHERE status = 'ACTIVE';
CREATE INDEX idx_review_product_id_like_count ON review.review (product_id, like_count DESC, created_at DESC, id DESC) WHERE status = 'ACTIVE';

CREATE TABLE review.review_like
(
    review_id          uuid         NOT NULL,
    member_id          uuid         NOT NULL,
    created_at         timestamptz  NOT NULL,
    CONSTRAINT pk_review_like PRIMARY KEY (review_id, member_id)
);

CREATE TABLE review.product_review_stat
(
    product_id         uuid         NOT NULL,
    review_count       int          NOT NULL,
    rating_sum         bigint       NOT NULL,
    r1                 int          NOT NULL DEFAULT 0,
    r2                 int          NOT NULL DEFAULT 0,
    r3                 int          NOT NULL DEFAULT 0,
    r4                 int          NOT NULL DEFAULT 0,
    r5                 int          NOT NULL DEFAULT 0,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT pk_product_review_stat PRIMARY KEY (product_id),
    CONSTRAINT ck_product_review_stat_review_count CHECK (review_count >= 0),
    CONSTRAINT ck_product_review_stat_rating_sum CHECK (rating_sum >= 0),
    CONSTRAINT ck_product_review_stat_r1 CHECK (r1 >= 0),
    CONSTRAINT ck_product_review_stat_r2 CHECK (r2 >= 0),
    CONSTRAINT ck_product_review_stat_r3 CHECK (r3 >= 0),
    CONSTRAINT ck_product_review_stat_r4 CHECK (r4 >= 0),
    CONSTRAINT ck_product_review_stat_r5 CHECK (r5 >= 0)
);

