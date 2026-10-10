package com.myroutine.product.domain;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;

import java.util.Map;
import java.util.Set;

public enum ProductStatus {
    ON_SALE, HIDDEN, DISCONTINUED;

    private static final Map<ProductStatus, Set<ProductStatus>> ALLOWED = Map.of(
            ON_SALE, Set.of(HIDDEN, DISCONTINUED),
            HIDDEN, Set.of(ON_SALE, DISCONTINUED),
            DISCONTINUED, Set.of()
    );

    public ProductStatus transitTo(ProductStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
