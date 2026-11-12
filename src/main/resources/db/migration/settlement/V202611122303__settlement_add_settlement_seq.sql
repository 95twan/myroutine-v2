ALTER TABLE settlement.settlement ADD COLUMN seq int NOT NULL DEFAULT 1;
ALTER TABLE settlement.settlement ALTER COLUMN seq DROP DEFAULT;
ALTER TABLE settlement.settlement DROP CONSTRAINT uk_settlement_shop_period;
ALTER TABLE settlement.settlement ADD CONSTRAINT uk_settlement_shop_period_seq UNIQUE (shop_id, period_start, seq);
ALTER TABLE settlement.settlement ADD CONSTRAINT ck_settlement_seq CHECK (seq >= 1);
