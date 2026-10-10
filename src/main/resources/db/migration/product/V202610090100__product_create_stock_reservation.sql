CREATE SCHEMA IF NOT EXISTS product;

CREATE TABLE product.stock_reservation
(
    id              uuid         NOT NULL,
    order_id        uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    quantity        int          NOT NULL,
    status          varchar(20)  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    CONSTRAINT pk_stock_reservation PRIMARY KEY (id),
    CONSTRAINT fk_stock_reservation_product FOREIGN KEY (product_id) REFERENCES product.product (id),
    CONSTRAINT ck_stock_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_reservation_status CHECK (status IN ('HELD','COMMITTED','RELEASED','EXPIRED')),
    CONSTRAINT uk_stock_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_stock_reservation_status_expires_at ON product.stock_reservation (status, expires_at);
