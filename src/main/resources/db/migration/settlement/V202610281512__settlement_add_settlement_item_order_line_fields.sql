ALTER TABLE settlement.settlement_item
    ADD COLUMN shop_order_id uuid         NOT NULL,
    ADD COLUMN product_name  varchar(100) NOT NULL,
    ADD COLUMN quantity      int          NOT NULL;
