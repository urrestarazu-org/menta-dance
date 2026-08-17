package com.menta.billing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object pairing an amount with its ISO-4217 currency code.
 *
 * <p>Never negative — a plan price of zero is legitimate (a free tier), a
 * negative one is always a bug, never a valid business state.</p>
 */
public final class Money {

    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        if (currency == null || !ISO_4217.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code");
        }
        return new Money(amount, currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount + " " + currency;
    }
}
