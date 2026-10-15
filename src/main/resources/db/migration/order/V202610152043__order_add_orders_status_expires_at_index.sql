CREATE INDEX idx_orders_status_expires_at ON orders.orders (status, expires_at);
