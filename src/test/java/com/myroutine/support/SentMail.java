package com.myroutine.support;

public record SentMail(
        String to,
        String title,
        String body
) {
}
