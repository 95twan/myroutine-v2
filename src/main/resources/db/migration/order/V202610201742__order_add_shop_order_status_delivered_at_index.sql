CREATE INDEX idx_shop_order_status_delivered_at ON orders.shop_order (status, delivered_at, id);
