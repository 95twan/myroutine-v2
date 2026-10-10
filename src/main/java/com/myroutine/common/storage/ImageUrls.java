package com.myroutine.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUrls {
    private final StorageProperties properties;

    public String toUrl(String objectKey) {
        return objectKey == null ? null : properties.publicBaseUrl() + "/" + objectKey;
    }

}
