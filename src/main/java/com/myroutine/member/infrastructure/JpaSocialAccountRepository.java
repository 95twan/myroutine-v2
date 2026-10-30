package com.myroutine.member.infrastructure;

import com.myroutine.member.domain.SocialAccount;
import com.myroutine.member.domain.SocialAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface JpaSocialAccountRepository extends JpaRepository<SocialAccount, UUID>, SocialAccountRepository {
    @Modifying
    @Query("delete from SocialAccount s where s.memberId = :memberId")
    void deleteAllByMemberId(UUID memberId);
}
