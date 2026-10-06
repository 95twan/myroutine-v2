package com.myroutine.shop.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository {
    Shop save(Shop shop);
    Optional<Shop> findById(UUID id);
    List<Shop> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId);
    List<Shop> findAllByIdIn(Collection<UUID> ids);
    boolean existsByBusinessNumber(String businessNumber);
}
