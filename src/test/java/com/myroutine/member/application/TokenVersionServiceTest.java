package com.myroutine.member.application;

import com.myroutine.common.security.TokenVersionProvider;
import com.myroutine.member.domain.Member;
import com.myroutine.member.domain.MemberRepository;
import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenVersionServiceTest extends IntegrationTestSupport {

    private final TestFixtures testFixtures;
    private final TransactionTemplate tx;
    private final TokenVersionService tokenVersionService;
    private final TokenVersionProvider tokenVersionProvider;
    private final MemberRepository memberRepository;

    @Autowired
    public TokenVersionServiceTest(TestFixtures testFixtures, TransactionTemplate tx, TokenVersionService tokenVersionService, TokenVersionProvider tokenVersionProvider, MemberRepository memberRepository) {
        this.testFixtures = testFixtures;
        this.tx = tx;
        this.tokenVersionService = tokenVersionService;
        this.tokenVersionProvider = tokenVersionProvider;
        this.memberRepository = memberRepository;
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 Redis의 토큰 버전은 바뀌지 않는다.")
    void rolledBackBumpLeavesRedisUnchanged() {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
        tokenVersionProvider.currentVersion(targetId);

        // When & Then
        assertThatThrownBy(() -> tx.execute(_ -> {
            Member member = memberRepository.findById(targetId).orElseThrow();
            tokenVersionService.bump(member);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback");

        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).db()).isEqualTo(0);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(0);

        tx.executeWithoutResult(_ -> {
            Member member = memberRepository.findById(targetId).orElseThrow();
            tokenVersionService.bump(member);
        });

        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).db()).isEqualTo(1);
        assertThat(testFixtures.tokenVersionSnapshotOf(targetId).redis()).isEqualTo(1);
    }

    @Test
    @DisplayName("토큰 버전 올리기를 실패한다. (트랜잭션 없음, IllegalTransactionStateException)")
    void bumpWithoutTransactionFails() {
        // Given
        UUID targetId = testFixtures.signup("target@test.com");
        tokenVersionProvider.currentVersion(targetId);

        Member member = memberRepository.findById(targetId).orElseThrow();

        // When & Then
        assertThatThrownBy(() -> tokenVersionService.bump(member)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
