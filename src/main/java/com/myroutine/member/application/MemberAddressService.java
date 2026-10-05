package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.member.domain.MemberAddress;
import com.myroutine.member.domain.MemberAddressRepository;
import com.myroutine.member.domain.MemberErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberAddressService {
    private final MemberAddressRepository memberAddressRepository;

    @Transactional
    public UUID register(UUID memberId, AddressCommand command) {
        boolean first = !memberAddressRepository.existsByMemberId(memberId);
        MemberAddress memberAddress = MemberAddress.register(
                memberId,
                command.recipient(),
                command.phone(),
                command.zipcode(),
                command.address1(),
                command.address2()
        );
        MemberAddress savedMemberAddress = memberAddressRepository.save(memberAddress);

        if (first || Boolean.TRUE.equals(command.isDefault())) {
            changeDefault(memberId, savedMemberAddress);
        }

        return savedMemberAddress.getId();
    }

    @Transactional
    public AddressResult update(UUID memberId, UUID addressId, AddressCommand command) {
        MemberAddress memberAddress = memberAddressRepository.findByIdAndMemberId(addressId, memberId).orElseThrow(
                () -> new BusinessException(MemberErrorCode.MEMBER_ADDRESS_NOT_FOUND)
        );
        memberAddress.update(
                command.recipient(),
                command.phone(),
                command.zipcode(),
                command.address1(),
                command.address2()
        );
        if (Boolean.TRUE.equals(command.isDefault())) {
            changeDefault(memberId, memberAddress);
        } else if (Boolean.FALSE.equals(command.isDefault())) {
            memberAddress.unmarkDefault();
        }

        return AddressResult.from(memberAddress);
    }

    @Transactional
    public void delete(UUID memberId, UUID addressId) {
        MemberAddress memberAddress = memberAddressRepository.findByIdAndMemberId(addressId, memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_ADDRESS_NOT_FOUND));
        memberAddressRepository.delete(memberAddress);
    }

    @Transactional(readOnly = true)
    public List<AddressResult> getAddresses(UUID memberId) {
        List<MemberAddress> memberAddresses = memberAddressRepository.findAllByMemberId(memberId);
        return memberAddresses.stream()
                .sorted(Comparator
                        .comparing(MemberAddress::isDefault, Comparator.reverseOrder())
                        .thenComparing(MemberAddress::getCreatedAt, Comparator.reverseOrder()))
                .map(AddressResult::from)
                .toList();
    }

    private void changeDefault(UUID memberId, MemberAddress target) {
        memberAddressRepository.findByMemberIdAndIsDefaultTrue(memberId).ifPresent(memberAddress -> {
            if (memberAddress != target) {
                memberAddress.unmarkDefault();
                memberAddressRepository.flush();
            }
        });
        target.markDefault();
    }

}
