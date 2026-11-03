package com.myroutine.support;

public record ProductSearchDoc(
        boolean found,
        Long version,
        String name,
        String description,
        String category,
        Long price,
        String status
) {
}
