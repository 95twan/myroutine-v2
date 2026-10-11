package com.myroutine.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ConcurrencyRunner {

    public static <T> List<Result<T>> run(int threads, Callable<T> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<Result<T>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        return new Result<>(task.call(), null);
                    } catch (Throwable e) {
                        return new Result<>(null, e);
                    }
                }));
            }

            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("스레드가 제때 준비되지 않았습니다.");
            }
            start.countDown();

            List<Result<T>> results = new ArrayList<>();
            for (Future<Result<T>> future : futures) {
                try {
                    results.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException | TimeoutException e) {
                    throw new IllegalStateException("작업 결과를 받지 못했습니다.", e);
                }
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    public record Result<T>(T value, Throwable error) {
        public boolean ok() {
            return error == null;
        }
    }

}
