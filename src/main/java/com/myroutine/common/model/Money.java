package com.myroutine.common.model;

public record Money(
        long amount
) implements Comparable<Money> {
    public static final Money ZERO = new Money(0);
    public static Money of(long amount) {
        return new Money(amount);
    }

    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(amount, other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount - other.amount);
    }

    public Money times(int quantity) {
        return new Money(Math.multiplyExact(amount, quantity));
    }

    public boolean isGreaterThan(Money other) {
        return amount > other.amount;
    }

    @Override
    public int compareTo(Money o) {
        return Long.compare(amount, o.amount);
    }
}
