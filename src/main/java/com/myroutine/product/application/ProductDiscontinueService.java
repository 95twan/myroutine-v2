package com.myroutine.product.application;

import com.myroutine.product.domain.Product;
import com.myroutine.product.domain.ProductRepository;
import com.myroutine.product.domain.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductDiscontinueService {
    private final ProductRepository productRepository;
    private final ProductEventPublisher publisher;

    @Transactional
    public void discontinueAllByShop(UUID shopId) {
        List<Product> products = productRepository.findAllByShopIdAndStatusIn(shopId, List.of(ProductStatus.ON_SALE, ProductStatus.HIDDEN));
        for(Product product : products) {
            product.changeStatus(ProductStatus.DISCONTINUED);
            productRepository.flush();
            publisher.upserted(product);
            publisher.discontinued(product, "SHOP_CLOSED");
        }
    }
}
