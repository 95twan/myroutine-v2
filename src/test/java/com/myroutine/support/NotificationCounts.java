package com.myroutine.support;

import java.util.List;

public record NotificationCounts(
        List<NotificationRow> rows
) {
    public int type(String type) {
        return (int) rows.stream().filter(row -> row.type().equals(type)).count();
    }

    public int status(String status) {
        return (int) rows.stream().filter(row -> row.status().equals(status)).count();
    }
}
