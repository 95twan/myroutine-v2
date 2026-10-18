CREATE TABLE orders.refund
(
    id               uuid         NOT NULL,
    order_id         uuid         NOT NULL,
    shop_order_id    uuid         NOT NULL,
    order_line_id    uuid         NOT NULL,
    member_id        uuid         NOT NULL,
    reason_type      varchar(20)  NOT NULL,
    reason           varchar(200),
    amount           bigint       NOT NULL,
    status           varchar(20)  NOT NULL,
    reject_reason    varchar(200),
    pg_attempts      int          NOT NULL DEFAULT 0,
    completed_at     timestamptz,
    version          bigint       NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT pk_refund PRIMARY KEY (id),
    CONSTRAINT fk_refund_orders FOREIGN KEY (order_id) REFERENCES orders.orders (id),
    CONSTRAINT fk_refund_shop_order FOREIGN KEY (shop_order_id) REFERENCES orders.shop_order (id),
    CONSTRAINT fk_refund_order_line FOREIGN KEY (order_line_id) REFERENCES orders.order_line (id),
    CONSTRAINT ck_refund_reason_type CHECK (reason_type IN ('BUYER_CANCEL','RETURN')),
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED','APPROVED','PG_CANCELLED','COMPLETED','REJECTED','FAILED'))
);

CREATE UNIQUE INDEX uk_refund_order_line_active ON orders.refund (order_line_id) WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED');
CREATE INDEX idx_refund_order_id ON orders.refund (order_id);
CREATE INDEX idx_refund_shop_order_id_status ON orders.refund (shop_order_id, status);
CREATE INDEX idx_refund_status_updated_at ON orders.refund (status, updated_at);
