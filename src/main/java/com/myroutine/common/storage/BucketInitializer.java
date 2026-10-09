package com.myroutine.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Component
@RequiredArgsConstructor
public class BucketInitializer {

    private final S3Client client;
    private final StorageProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            client.headBucket(b -> b.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            client.createBucket(b -> b.bucket(properties.bucket()));
        }

        client.putBucketPolicy(b -> b.bucket(properties.bucket())
                .policy("{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + properties.bucket() + "/products/*\"]}]}")
        );
    }
}
