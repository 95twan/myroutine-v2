package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface JpaMemberRepository extends JpaRepository<Member, UUID>, MemberRepository {
}
