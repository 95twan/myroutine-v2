CREATE SCHEMA IF NOT EXISTS product;

CREATE TABLE product.product
(
    id              uuid         NOT NULL,
    shop_id         uuid         NOT NULL,
    name            varchar(100) NOT NULL,
    description     text         NOT NULL,
    category        varchar(20)  NOT NULL,
    price           bigint       NOT NULL,
    status          varchar(20)  NOT NULL,
    subscribable    boolean      NOT NULL DEFAULT false,
    thumbnail_key   varchar(255),
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT ck_product_category CHECK (category IN ('FOOD', 'HEALTH', 'BEAUTY', 'LIVING', 'PET', 'ETC')),
    CONSTRAINT ck_product_price CHECK (price > 0),
    CONSTRAINT ck_product_status CHECK (status IN ('ON_SALE', 'HIDDEN', 'DISCONTINUED'))
);

CREATE INDEX idx_product_status_created_at ON product.product (status, created_at DESC, id DESC);
CREATE INDEX idx_product_status_category_created_at ON product.product (status, category, created_at DESC, id DESC);
CREATE INDEX idx_product_shop_id_created_at ON product.product (shop_id, created_at DESC, id DESC);

CREATE TABLE product.product_image
(
    id              uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    object_key      varchar(255) NOT NULL,
    sort_order      int          NOT NULL,
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_product_image PRIMARY KEY (id),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product.product (id)
);

CREATE TABLE product.stock
(
    product_id      uuid         NOT NULL,
    available       int          NOT NULL,
    reserved        int          NOT NULL,
    sold            int          NOT NULL,
    received        int          NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_stock PRIMARY KEY (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product.product (id),
    CONSTRAINT ck_stock_available CHECK (available >= 0),
    CONSTRAINT ck_stock_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_stock_sold CHECK (sold >= 0),
    CONSTRAINT ck_stock_balance CHECK (available + reserved + sold = received)
);

CREATE TABLE product.stock_movement
(
    id              uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    type            varchar(20)  NOT NULL,
    quantity        int          NOT NULL,
    ref_type        varchar(30)  NOT NULL,
    ref_id          uuid         NOT NULL,
    reason          varchar(200),
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_stock_movement PRIMARY KEY (id),
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES product.product (id),
    CONSTRAINT ck_stock_movement_type CHECK (type IN ('RECEIVE','ADJUST','RESERVE','RELEASE','COMMIT','RESTORE')),
    CONSTRAINT ck_stock_movement_ref_type CHECK (ref_type IN ('PRODUCT_REGISTER','ADJUSTMENT','ORDER','REFUND')),
    CONSTRAINT uk_stock_movement_type_ref UNIQUE (type, ref_type, ref_id, product_id)
);
