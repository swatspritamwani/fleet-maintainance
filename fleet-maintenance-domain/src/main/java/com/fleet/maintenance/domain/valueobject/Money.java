package com.fleet.maintenance.domain.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "USD";

    private static final int CURRENCY_CODE_LENGTH = 3;

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (currency.length() != CURRENCY_CODE_LENGTH) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code");
        }
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
