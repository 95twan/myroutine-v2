package com.myroutine.support;

import java.util.Map;
import java.util.Set;

public record EmailAuthSnapshot(
        Map<String, String> codeFields,
        long codeTtlSeconds,
        String cooldown,
        long cooldownTtlSeconds,
        Integer hourly,
        long hourlyTtlSeconds
) {
    public boolean codeExists() {
        return !codeFields.isEmpty();
    }

    public String codeHash() {
        return codeFields.get("codeHash");
    }

    public Integer attempts() {
        if (codeFields.containsKey("attempts")) {
            return Integer.valueOf(codeFields.get("attempts"));
        }
        return null;
    }

    public Set<String> fieldNames() {
        return Set.copyOf(codeFields.keySet());
    }
}
