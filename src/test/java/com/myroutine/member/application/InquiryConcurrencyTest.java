package com.myroutine.member.application;

import com.myroutine.common.error.BusinessException;
import com.myroutine.common.model.Ids;
import com.myroutine.member.domain.MemberErrorCode;
import com.myroutine.support.ConcurrencyRunner;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class InquiryConcurrencyTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final InquiryService inquiryService;
    private final ObjectMapper objectMapper;

    @Autowired
    public InquiryConcurrencyTest(TestFixtures testFixtures, InquiryService inquiryService, ObjectMapper objectMapper) {
        this.testFixtures = testFixtures;
        this.inquiryService = inquiryService;
        this.objectMapper = objectMapper;
    }

    @RepeatedTest(10)
    @DisplayName("문의를 삭제하는 요청과 답변하는 요청이 동시에 와도 한쪽만 성공한다.")
    void deleteAndAnswerConcurrently() throws Exception {
        UUID user1Id = testFixtures.signup("user1@test.com");

        UUID inquiry1Id = inquiryService.write(user1Id, "배송이 늦어요", "주문한 상품이 아직 도착하지 않았습니다.");

        UUID adminId = Ids.newId();

        Callable<Void> answerInquiry = () -> {
            inquiryService.answer(adminId, inquiry1Id, "확인 후 오늘 중 안내드리겠습니다.");
            return null;
        };

        Callable<Void> deleteInquiry = () -> {
            inquiryService.delete(user1Id, inquiry1Id);
            return null;
        };

        // When
        List<ConcurrencyRunner.Result<Void>> results = ConcurrencyRunner.run(List.of(answerInquiry, deleteInquiry));

        // Then
        assertThat(results).filteredOn(ConcurrencyRunner.Result::ok).hasSize(1);

        ConcurrencyRunner.Result<Void> answerResult = results.getFirst();
        ConcurrencyRunner.Result<Void> deleteResult = results.getLast();

        if (!answerResult.ok()) {
            assertThat(answerResult.error()).isInstanceOfSatisfying(BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.INQUIRY_NOT_FOUND));
        }
        if (!deleteResult.ok()) {
            assertThat(deleteResult.error()).satisfiesAnyOf(
                    e -> assertThat(e).isInstanceOf(OptimisticLockingFailureException.class),
                    e -> assertThat(e).isInstanceOfSatisfying(BusinessException.class,
                            be -> assertThat(be.getErrorCode()).isEqualTo(MemberErrorCode.INQUIRY_ALREADY_ANSWERED)));
        }

        if (answerResult.ok()) {
            assertThat(testFixtures.inquiryOf(inquiry1Id).status()).isEqualTo("ANSWERED");
            assertThat(testFixtures.inquiryOf(inquiry1Id).answers()).hasSize(1);
        }

        if (deleteResult.ok()) {
            assertThat(testFixtures.inquiryOf(inquiry1Id)).isNull();
            assertThat(testFixtures.countOf("member.inquiry_answer")).isEqualTo(0);
        }
    }
}
