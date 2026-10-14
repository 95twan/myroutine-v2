package com.myroutine.support;

public record Behavior(
        int status,
        String body,
        long delayMillis,
        boolean dropConnection,
        boolean defaultHandling
) {
    public static Behavior approve() {
        return new Behavior(200, "", 0, false, true);
    }

    public static Behavior reject(String code) {
        return new Behavior(400, "{\"code\":\"%s\",\"message\":\"거절\"}".formatted(code), 0, false, false);
    }

    public static Behavior serverError() {
        return new Behavior(500, "", 0, false, false);
    }

    public static Behavior delay(long ms) {
        return new Behavior(200, "", ms, false, true);
    }

    public static Behavior drop() {
        return new Behavior(0, "", 0, true, false);
    }
}
