package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.outbox.OutboxPublisher;
import com.myroutine.member.api.WithdrawalPrecondition;
import com.myroutine.member.api.event.MemberTopics;
import com.myroutine.member.api.event.MemberWithdrawnEvent;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberAddressRepository;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.shop.api.ShopApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawService {
    private final MemberRepository memberRepository;
    private final MemberAddressRepository memberAddressRepository;
    private final ShopApi shopApi;
    private final List<WithdrawalPrecondition> preconditions;
    private final OutboxPublisher publisher;
    private final Clock clock;

    @Transactional
    public void withdraw(UUID memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

        List<String> reasons = new ArrayList<>();
        int shopCount = shopApi.countActiveShops(memberId);
        if (shopCount > 0) {
            reasons.add("ACTIVE_SHOP");
        }
        preconditions.forEach(p -> reasons.addAll(p.violations(memberId)));

        if (!reasons.isEmpty()) {
            throw new BusinessException(MemberErrorCode.MEMBER_HAS_ACTIVE_RESOURCES, Map.of("reasons", reasons));
        }

        memberAddressRepository.deleteAllByMemberId(memberId);
        member.withdraw(clock.instant());
        publisher.publish(MemberTopics.MEMBER_WITHDRAWN, "MEMBER", memberId, new MemberWithdrawnEvent(memberId));
    }
}
