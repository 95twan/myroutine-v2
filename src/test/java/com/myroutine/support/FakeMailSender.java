package com.myroutine.support;

import com.myroutine.common.mail.MailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@Primary
public class FakeMailSender implements MailSender {

    private final List<SentMail> sentMails = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger failCount = new AtomicInteger(0);

    @Override
    public void send(String to, String title, String body) {
        sentMails.add(new SentMail(to, title, body));
        if (failCount.getAndDecrement() > 0) {
            throw new RuntimeException("Mail send failed");
        }
        log.info("send mail to {}, title: {}, body: {}", to, title, body.substring(0, Math.min(body.length(), 100)));
    }

    public List<SentMail> sent() {
        return List.copyOf(sentMails);
    }

    public void failNext(int times) {
        failCount.set(times);
    }

    public void reset() {
        sentMails.clear();
        failCount.set(0);
    }
}
