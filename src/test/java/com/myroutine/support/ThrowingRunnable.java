package com.myroutine.support;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}
