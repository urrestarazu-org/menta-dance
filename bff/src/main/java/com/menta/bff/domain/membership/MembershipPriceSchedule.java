package com.menta.bff.domain.membership;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Works out the state of every membership price tier for a given date.
 *
 * <p>A tier is expired when its deadline is before today (the deadline day is inclusive). Among the others,
 * every tier that shares the nearest deadline is current ({@link PriceRowState#CURRENT}); the rest are future
 * ones ({@link PriceRowState#LATER}).
 * </p>
 */
public final class MembershipPriceSchedule {

    private MembershipPriceSchedule() {
    }

    /**
     * Builds the rows of the price table for a date.
     *
     * @param tiers tiers in the order they are shown
     * @param today today's date in Argentina
     * @return one row per tier, in the same order
     */
    public static List<PriceRow> rowsAt(List<PriceTier> tiers, LocalDate today) {
        LocalDate currentDeadline = tiers.stream()
                .map(PriceTier::deadline)
                .filter(deadline -> !deadline.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);

        return tiers.stream()
                .map(tier -> new PriceRow(
                        tier.label(),
                        formatMoney(tier.price()),
                        tier.installments(),
                        formatMoney((long) tier.price() * tier.installments()),
                        stateOf(tier, today, currentDeadline),
                        tier.payUrl(),
                        tier.exclusive(),
                        tier.deadline()))
                .toList();
    }

    /**
     * Whether no price is in force any more (sales are over).
     *
     * @param rows rows built with {@link #rowsAt(List, LocalDate)}
     * @return {@code true} when no row is current
     */
    public static boolean isSalesClosed(List<PriceRow> rows) {
        return rows.stream().noneMatch(PriceRow::current);
    }

    private static PriceRowState stateOf(PriceTier tier, LocalDate today, LocalDate currentDeadline) {
        if (tier.deadline().isBefore(today)) {
            return PriceRowState.EXPIRED;
        }
        if (tier.deadline().equals(currentDeadline)) {
            return PriceRowState.CURRENT;
        }
        return PriceRowState.LATER;
    }

    /** Formats pesos the Argentine way ({@code "$ 25.000"}). DecimalFormat is not thread safe: one per call. */
    private static String formatMoney(long amount) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));
        return "$ " + new DecimalFormat("#,###", symbols).format(amount);
    }
}
