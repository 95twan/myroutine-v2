ALTER TABLE product.product_image ADD CONSTRAINT uk_product_image_object_key UNIQUE (object_key);
CREATE INDEX idx_product_image_product_id ON product.product_image (product_id)
