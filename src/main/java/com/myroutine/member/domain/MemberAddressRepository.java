package com.myroutine.member.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberAddressRepository {
    MemberAddress save(MemberAddress memberAddress);
    Optional<MemberAddress> findByIdAndMemberId(UUID id, UUID memberId);
    List<MemberAddress> findAllByMemberId(UUID memberId);
    Optional<MemberAddress> findByMemberIdAndIsDefaultTrue(UUID memberId);
    boolean existsByMemberId(UUID memberId);
    void delete(MemberAddress memberAddress);
    void flush();
}
