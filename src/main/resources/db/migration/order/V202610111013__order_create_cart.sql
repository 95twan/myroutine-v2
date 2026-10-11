CREATE SCHEMA IF NOT EXISTS orders;

CREATE TABLE orders.cart
(
    id              uuid         NOT NULL,
    member_id       uuid         NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_cart PRIMARY KEY (id),
    CONSTRAINT uk_cart_member UNIQUE (member_id)
);

CREATE TABLE orders.cart_item
(
    id              uuid         NOT NULL,
    cart_id         uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    quantity        int          NOT NULL,
    version         bigint       NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_cart_item PRIMARY KEY (id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES orders.cart (id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT uk_cart_item_cart_product UNIQUE (cart_id, product_id)
);

