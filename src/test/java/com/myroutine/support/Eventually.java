package com.myroutine.support;

import java.time.Duration;

public class Eventually {
    public static void await(Duration timeout, ThrowingRunnable assertion) {
        long deadline = System.nanoTime() + timeout.toNanos();   // ① 마감 시각
        Throwable last;                                           // ② 마지막 실패를 담을 곳

        while (true) {
            try {
                assertion.run();                                  // ③ 확인 코드 실행
                return;                                           // ④ 예외 없이 끝나면 성공 → 끝
            } catch (Throwable t) {
                last = t;                                         // ⑤ 실패하면 기록하고 계속
            }

            if (System.nanoTime() >= deadline) {                  // ⑥ 마감이 지났으면 반복 종료
                break;
            }

            try {
                Thread.sleep(100);                                // ⑦ 100ms 쉬었다가 다시
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();               // 중단 요청이면 표시를 남기고 종료
                break;
            }
        }

        if (last instanceof RuntimeException re) throw re;        // ⑧ 마지막 실패를 던진다
        if (last instanceof Error err) throw err;
        throw new AssertionError("마지막 실패: " + last, last);   // 검사 예외는 감싸서
    }
}
