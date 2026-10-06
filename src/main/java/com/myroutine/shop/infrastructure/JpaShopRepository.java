package com.myroutine.shop.infrastructure;

import com.myroutine.shop.domain.Shop;
import com.myroutine.shop.domain.ShopRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaShopRepository extends JpaRepository<Shop, UUID>, ShopRepository {
}
