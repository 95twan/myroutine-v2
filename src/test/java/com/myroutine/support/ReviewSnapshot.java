package com.myroutine.support;

import java.util.List;
import java.util.UUID;

public record ReviewSnapshot(
        UUID orderLineId,
        UUID productId,
        String status,
        int rating,
        String content,
        int likeCount,
        List<UUID> likerIds
) {
}
