package com.myroutine.common.mail;

public interface MailSender {
    void send(String to, String title, String body);
}
