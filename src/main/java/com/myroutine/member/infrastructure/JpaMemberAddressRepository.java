package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.MemberAddress;
import com.myroutine.member.domain.MemberAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaMemberAddressRepository extends JpaRepository<MemberAddress, UUID>, MemberAddressRepository {
}
