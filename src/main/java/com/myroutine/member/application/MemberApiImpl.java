package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.api.MemberApi;
import com.myroutine.member.api.ShippingAddressInfo;
import com.myroutine.member.domain.MemberAddress;
import com.myroutine.member.domain.MemberAddressRepository;
import com.myroutine.member.domain.MemberErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class MemberApiImpl implements MemberApi {

    private final MemberAddressRepository memberAddressRepository;

    @Override
    @Transactional(readOnly = true)
    public ShippingAddressInfo getAddress(UUID memberId, UUID addressId) {
        MemberAddress memberAddress = memberAddressRepository.findByIdAndMemberId(addressId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_ADDRESS_NOT_FOUND)
        );

        return ShippingAddressInfo.from(memberAddress);
    }
}
