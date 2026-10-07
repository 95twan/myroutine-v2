package com.myroutine.common.web;

import java.util.List;

public record CursorPage<T>(
        List<T> items,
        String nextCursor
) {
}
