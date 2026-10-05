package com.myroutine.member.web;

import com.myroutine.common.security.CurrentMember;
import com.myroutine.member.application.AddressResult;
import com.myroutine.member.application.MemberAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
public class MemberAddressController {
    private final MemberAddressService memberAddressService;

    @GetMapping
    public ResponseEntity<List<AddressResponse>> getAddresses(@CurrentMember UUID memberId) {
        List<AddressResult> addresses = memberAddressService.getAddresses(memberId);
        return ResponseEntity.ok(addresses.stream()
                .map(AddressResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<AddressIdResponse> register(@CurrentMember UUID memberId, @Valid @RequestBody AddressRequest request) {
        UUID addressId = memberAddressService.register(memberId, request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AddressIdResponse(addressId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AddressResponse> update(
            @CurrentMember UUID memberId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAddressRequest request
    ) {
        AddressResult address = memberAddressService.update(memberId, id, request.toCommand());
        return ResponseEntity.ok(AddressResponse.from(address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentMember UUID memberId,
            @PathVariable UUID id
    ) {
        memberAddressService.delete(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
