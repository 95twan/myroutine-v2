package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.api.MemberApi;
import com.myroutine.member.api.MemberContact;
import com.myroutine.member.api.ShippingAddressInfo;
import com.myroutine.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberApiImpl implements MemberApi {

    private final MemberAddressRepository memberAddressRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public ShippingAddressInfo getAddress(UUID memberId, UUID addressId) {
        MemberAddress memberAddress = memberAddressRepository.findByIdAndMemberId(addressId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_ADDRESS_NOT_FOUND)
        );

        return ShippingAddressInfo.from(memberAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberContact getContact(UUID memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

        return new MemberContact(member.getEmail(), Set.of(MemberStatus.ACTIVE, MemberStatus.BANNED).contains(member.getStatus()));
    }
}
