package com.myroutine.member.application;

import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRoleService {
    private final MemberRepository memberRepository;
    private final ShopApi shopApi;

    @Transactional
    public void syncSellerRole(UUID memberId) {
        Optional<Member> member = memberRepository.findByIdForUpdate(memberId);
        if (member.isEmpty()) {
            log.warn("회원을 찾을 수 없습니다. memberId={}", memberId);
            return;
        }
        int count = shopApi.countActiveShops(memberId);
        if (count > 0) {
            member.get().promoteToSeller();
        } else {
            member.get().demoteToUser();
        }
    }
}
