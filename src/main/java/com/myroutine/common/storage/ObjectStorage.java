package com.myroutine.common.storage;


public interface ObjectStorage {
    PresignedUpload presignPut(String key, String contentType, long contentLength);
    boolean exists(String key);
    void delete(String key);
}
