package com.fleet.maintenance.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void constructsValidMoney() {
        Money money = new Money(BigDecimal.ONE, "USD");
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    void factoryOfUsesDefaultCurrency() {
        Money money = Money.of(BigDecimal.ZERO);
        assertThat(money.currency()).isEqualTo(Money.DEFAULT_CURRENCY);
    }

    @Test
    void factoryOfWithCurrency() {
        Money money = Money.of(BigDecimal.ONE, "EUR");
        assertThat(money.currency()).isEqualTo("EUR");
    }

    @Test
    void throwsWhenAmountNegative() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE.negate(), "USD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(">= 0");
    }

    @Test
    void throwsWhenAmountNull() {
        assertThatThrownBy(() -> new Money(null, "USD"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsWhenCurrencyNull() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void throwsWhenCurrencyNotThreeChars() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "US"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroAmountIsValid() {
        Money money = Money.of(BigDecimal.ZERO);
        assertThat(money.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
