CREATE INDEX idx_shop_order_shop_id_status_created_at ON orders.shop_order (shop_id, status, created_at DESC, id DESC);
CREATE INDEX idx_shop_order_shop_id_created_at ON orders.shop_order (shop_id, created_at DESC, id DESC)
