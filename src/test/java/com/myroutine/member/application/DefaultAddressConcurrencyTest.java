package com.myroutine.member.application;

import com.myroutine.support.IntegrationTestSupport;
import com.myroutine.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAddressConcurrencyTest extends IntegrationTestSupport {

    private final MemberAddressService memberAddressService;
    private final TestFixtures testFixtures;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DefaultAddressConcurrencyTest(MemberAddressService memberAddressService, TestFixtures testFixtures, JdbcTemplate jdbcTemplate) {
        this.memberAddressService = memberAddressService;
        this.testFixtures = testFixtures;
        this.jdbcTemplate = jdbcTemplate;
    }

    @RepeatedTest(10)
    @DisplayName("두 배송지를 동시에 기본으로 지정해도 기본 배송지는 1개다.")
    void changeDefaultConcurrently() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        AddressCommand command1 = new AddressCommand(
                "test1", "test1", "test1", "test1", "test1", true
        );
        AddressCommand command2 = new AddressCommand(
                "test2", "test2", "test2", "test2", "test2", false
        );
        AddressCommand command3 = new AddressCommand(
                "test3", "test3", "test3", "test3", "test3", false
        );

        memberAddressService.register(memberId, command1);
        UUID[] addressIds = {
                memberAddressService.register(memberId, command2),
                memberAddressService.register(memberId, command3)
        };

        AddressCommand updateAddressCommand = new AddressCommand(null, null, null, null, null, true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);   // 둘 다 준비될 때까지
        CountDownLatch start = new CountDownLatch(1);   // 동시에 출발시키는 신호

        List<Future<AddressResult>> futures = new ArrayList<>();
        for (UUID addressId : addressIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return memberAddressService.update(memberId, addressId, updateAddressCommand);
            }));
        }

        // When
        ready.await();
        start.countDown();

        // Then
        int success = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<AddressResult> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());   // 서비스가 던진 진짜 예외
            }
        }
        pool.shutdown();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member.member_address WHERE is_default", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(success).isGreaterThanOrEqualTo(1);
        for (Throwable failure : failures) {
            assertThat(failure).isInstanceOfAny(OptimisticLockingFailureException.class, DataIntegrityViolationException.class);
        }
    }

    @RepeatedTest(10)
    @DisplayName("기본 배송지가 없는 상태에서 두 배송지를 동시에 기본으로 지정해도 기본 배송지는 1개다.")
    void changeDefaultConcurrentlyWithoutDefault() throws Exception {
        // Given
        UUID memberId = testFixtures.signup("test@test.com");
        AddressCommand command1 = new AddressCommand(
                "test1", "test1", "test1", "test1", "test1", true
        );
        AddressCommand command2 = new AddressCommand(
                "test2", "test2", "test2", "test2", "test2", false
        );
        AddressCommand command3 = new AddressCommand(
                "test3", "test3", "test3", "test3", "test3", false
        );

        UUID addressId1 = memberAddressService.register(memberId, command1);
        UUID[] addressIds = {
                memberAddressService.register(memberId, command2),
                memberAddressService.register(memberId, command3)
        };
        memberAddressService.delete(memberId, addressId1);

        AddressCommand updateAddressCommand = new AddressCommand(null, null, null, null, null, true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);   // 둘 다 준비될 때까지
        CountDownLatch start = new CountDownLatch(1);   // 동시에 출발시키는 신호

        List<Future<AddressResult>> futures = new ArrayList<>();
        for (UUID addressId : addressIds) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return memberAddressService.update(memberId, addressId, updateAddressCommand);
            }));
        }

        // When
        ready.await();
        start.countDown();

        // Then
        int success = 0;
        List<Throwable> failures = new ArrayList<>();
        for (Future<AddressResult> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException e) {
                failures.add(e.getCause());   // 서비스가 던진 진짜 예외
            }
        }
        pool.shutdown();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM member.member_address WHERE is_default", Integer.class);
        assertThat(count).isEqualTo(1);
        assertThat(success).isGreaterThanOrEqualTo(1);
        for (Throwable failure : failures) {
            assertThat(failure).isInstanceOfAny(OptimisticLockingFailureException.class, DataIntegrityViolationException.class);
        }
    }
}
