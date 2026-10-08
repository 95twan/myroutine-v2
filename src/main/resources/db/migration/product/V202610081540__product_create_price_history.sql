CREATE SCHEMA IF NOT EXISTS product;

CREATE TABLE product.price_history
(
    id              uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    old_price       bigint       NOT NULL,
    new_price       bigint       NOT NULL,
    changed_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL,
    CONSTRAINT pk_price_history PRIMARY KEY (id),
    CONSTRAINT fk_price_history_product FOREIGN KEY (product_id) REFERENCES product.product (id)
);

CREATE INDEX idx_price_history_product_id_changed_at ON product.price_history (product_id, changed_at DESC);
