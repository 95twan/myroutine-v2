package com.myroutine.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "myroutine.mail", name = "mode", havingValue = "log")
public class LogMailSender implements MailSender{

    @Value("${myroutine.mail.log-delay}")
    private Duration delay;

    @Override
    public void send(String to, String title, String body) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("mail send interrupted", e);
        }
        log.info("send mail to {}, title: {}, body: {}", to, title, body.substring(0, Math.min(body.length(), 100)));
    }
}
