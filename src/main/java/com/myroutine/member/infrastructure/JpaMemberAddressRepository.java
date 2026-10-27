package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.MemberAddress;
import com.myroutine.member.domain.MemberAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface JpaMemberAddressRepository extends JpaRepository<MemberAddress, UUID>, MemberAddressRepository {
    @Modifying
    @Query("delete from MemberAddress a where a.memberId = :memberId")
    void deleteAllByMemberId(UUID memberId);
}
