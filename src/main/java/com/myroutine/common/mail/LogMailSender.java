package com.myroutine.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
public class LogMailSender implements MailSender{
    @Override
    public void send(String to, String title, String body) {
        log.info("send mail to {}, title: {}, body: {}", to, title, body.substring(0, Math.min(body.length(), 100)));
    }
}
