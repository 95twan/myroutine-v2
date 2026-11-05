package com.myroutine.common.web;

import com.myroutine.common.dlt.DltTopicResult;

public record DltTopicResponse(
        String topic,
        long recordCount
) {
    public static DltTopicResponse from(DltTopicResult result) {
        return new DltTopicResponse(result.topic(), result.recordCount());
    }
}
