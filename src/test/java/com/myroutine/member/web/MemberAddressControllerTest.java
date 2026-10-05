package com.myroutine.member.web;

import com.jayway.jsonpath.JsonPath;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MemberAddressControllerTest extends IntegrationTestSupport {

    private final MockMvc mockMvc;
    private final TestFixtures testFixtures;
    private final ObjectMapper objectMapper;

    @Autowired
    public MemberAddressControllerTest(MockMvc mockMvc, TestFixtures testFixtures, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.testFixtures = testFixtures;
        this.objectMapper = objectMapper;
    }

    @Test
    @DisplayName("첫 배송지를 등록하면 기본 배송지가 된다.")
    void registerFirstAddressBecomesDefault() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddressRequest request = new AddressRequest(
                "recipient",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                false
        );

        // When & Then
        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault").value(true));

    }

    @Test
    @DisplayName("기본 배송지로 등록하면 기존 기본 배송지가 해제된다.")
    void registerDefaultAddressUnmarksPrevious() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddressRequest request1 = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                true
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        AddressRequest request2 = new AddressRequest(
                "recipient2",
                "010-8888-8888",
                "97941",
                "address3",
                "address4",
                true
        );

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        String addressId = JsonPath.read(result.getResponse().getContentAsString(), "$.addressId");

        mockMvc.perform(get("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isDefault == true)]").value(hasSize(1)))
                .andExpect(jsonPath("$[?(@.isDefault == true)].id").value(addressId));
    }

    @Test
    @DisplayName("배송지를 기본으로 변경하면 이전 기본 배송지가 해제된다.")
    void updateAddressToDefault() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddressRequest request = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                true
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        AddressRequest request2 = new AddressRequest(
                "recipient2",
                "010-8888-8888",
                "97941",
                "address3",
                "address4",
                false
        );

        MvcResult result = mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        String addressId = JsonPath.read(result.getResponse().getContentAsString(), "$.addressId");

        UpdateAddressRequest updateRequest = new UpdateAddressRequest(
                null,
                null,
                null,
                null,
                null,
                true
        );

        // When & Then
        mockMvc.perform(patch("/api/members/me/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());


        mockMvc.perform(get("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.isDefault == true)]").value(hasSize(1)))
                .andExpect(jsonPath("$[?(@.isDefault == true)].id").value(addressId));
    }

    @Test
    @DisplayName("배송지 수정을 실패한다. (다른 회원의 배송지)")
    void updateAddressOfOtherMember() throws Exception {
        // Given
        String token1 = testFixtures.token("test1@test.com");
        String token2 = testFixtures.token("test2@test.com");
        AddressRequest request = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                true
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        AddressRequest request2 = new AddressRequest(
                "recipient2",
                "010-8888-8888",
                "97941",
                "address3",
                "address4",
                false
        );

        MvcResult result = mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        String addressId = JsonPath.read(result.getResponse().getContentAsString(), "$.addressId");

        UpdateAddressRequest updateRequest = new UpdateAddressRequest(
                null,
                null,
                "38792",
                "changed address1",
                "changed address2",
                true
        );

        // When & Then
        mockMvc.perform(patch("/api/members/me/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));

    }

    @Test
    @DisplayName("배송지 삭제를 실패한다. (다른 회원의 배송지)")
    void deleteAddressOfOtherMember() throws Exception {
        // Given
        String token1 = testFixtures.token("test1@test.com");
        String token2 = testFixtures.token("test2@test.com");
        AddressRequest request = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                true
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        AddressRequest request2 = new AddressRequest(
                "recipient2",
                "010-8888-8888",
                "97941",
                "address3",
                "address4",
                false
        );

        MvcResult result = mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        String addressId = JsonPath.read(result.getResponse().getContentAsString(), "$.addressId");

        // When & Then
        mockMvc.perform(delete("/api/members/me/addresses/" + addressId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_ADDRESS_NOT_FOUND"));

    }

    @Test
    @DisplayName("배송지 목록은 기본 배송지가 첫 번째다.")
    void getAddressesDefaultFirst() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddressRequest request = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "97940",
                "address1",
                "address2",
                true
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        AddressRequest request2 = new AddressRequest(
                "recipient2",
                "010-8888-8888",
                "97941",
                "address3",
                "address4",
                false
        );

        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated())
                .andReturn();

        // When & Then
        mockMvc.perform(get("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @DisplayName("배송지 등록을 실패한다. (우편번호 형식 오류)")
    void registerWithInvalidZipcode() throws Exception {
        // Given
        String token = testFixtures.token("test@test.com");
        AddressRequest request = new AddressRequest(
                "recipient1",
                "010-9999-9999",
                "9794",
                "address1",
                "address2",
                false
        );

        // When & Then
        mockMvc.perform(post("/api/members/me/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

}
