DROP INDEX orders.uk_refund_order_line_active;
CREATE UNIQUE INDEX uk_refund_order_line_active ON orders.refund (order_line_id) WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED','FAILED');
