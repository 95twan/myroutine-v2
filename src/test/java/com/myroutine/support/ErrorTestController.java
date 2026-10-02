package com.myroutine.support;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ErrorTestController {

    @GetMapping("/api/auth/test/unexpected-error")
    public ResponseEntity<Void> unexpectedError() {
        throw new IllegalStateException("SELECT password_hash FROM member.member");
    }
}
