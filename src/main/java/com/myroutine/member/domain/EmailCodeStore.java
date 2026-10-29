package com.myroutine.member.domain;

public interface EmailCodeStore {
    void checkSendLimit(String email);

    void save(EmailCodePurpose p, String email, String code);

    boolean verify(EmailCodePurpose p, String email, String code);
}
