CREATE TABLE orders.orders
(
    id               uuid         NOT NULL,
    member_id        uuid         NOT NULL,
    type             varchar(20)  NOT NULL DEFAULT 'NORMAL',
    status           varchar(30)  NOT NULL,
    total_amount     bigint       NOT NULL,
    shipping_address jsonb        NOT NULL,
    expires_at       timestamptz  NOT NULL,
    paid_at          timestamptz,
    failure_reason   varchar(200),
    version          bigint       NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_type CHECK (type IN ('NORMAL','SUBSCRIPTION')),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT','PAYMENT_IN_PROGRESS','PAID', 'CANCELLED', 'PAYMENT_FAILED','EXPIRED')),
    CONSTRAINT ck_orders_total CHECK (total_amount > 0)
);

CREATE INDEX idx_orders_member_id_created_at ON orders.orders (member_id, created_at DESC, id DESC);

CREATE TABLE orders.shop_order
(
    id              uuid         NOT NULL,
    order_id        uuid         NOT NULL,
    shop_id         uuid         NOT NULL,
    shop_name       varchar(50)  NOT NULL,
    status          varchar(20)  NOT NULL,
    carrier         varchar(30),
    tracking_number varchar(50),
    shipped_at      timestamptz,
    delivered_at    timestamptz,
    completed_at    timestamptz,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_shop_order PRIMARY KEY (id),
    CONSTRAINT fk_shop_order_orders FOREIGN KEY (order_id) REFERENCES orders.orders (id),
    CONSTRAINT uk_shop_order_order_shop UNIQUE (order_id, shop_id),
    CONSTRAINT ck_shop_order_status CHECK (status IN ('PENDING','PAID','SHIPPED','DELIVERED','COMPLETED','CANCELLED'))
);

CREATE TABLE orders.order_line
(
    id              uuid         NOT NULL,
    shop_order_id   uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    product_name    varchar(100) NOT NULL,
    thumbnail_key   varchar(255),
    unit_price      bigint       NOT NULL,
    quantity        int          NOT NULL,
    line_amount     bigint       NOT NULL,
    refunded_amount bigint       NOT NULL DEFAULT 0,
    status          varchar(20)  NOT NULL,
    confirmed_at    timestamptz,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_order_line PRIMARY KEY (id),
    CONSTRAINT fk_order_line_shop_order FOREIGN KEY (shop_order_id) REFERENCES orders.shop_order (id),
    CONSTRAINT ck_order_line_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_line_amount CHECK (line_amount = unit_price * quantity),
    CONSTRAINT ck_order_line_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= line_amount),
    CONSTRAINT ck_order_line_status CHECK (status IN ('PENDING','PAID','SHIPPED','DELIVERED','CONFIRMED','CANCELLED','RETURN_REQUESTED','RETURNED'))
);

CREATE INDEX idx_order_line_shop_order_id ON orders.order_line (shop_order_id);
