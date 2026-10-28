package com.myroutine.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SessionSnapshot(
        Map<String, String> fields,
        long ttlSeconds
) {
    public boolean exists() {
        return !fields.isEmpty();
    }

    public UUID memberId() {
        String memberId = fields.get("memberId");
        if (memberId == null) {
            return null;
        }
        return UUID.fromString(memberId);
    }

    public String refreshHash() {
        return fields.get("refreshHash");
    }

    public List<String> fieldNames() {
        return List.copyOf(fields.keySet());
    }

    public List<String> values() {
        return List.copyOf(fields.values());
    }
}
