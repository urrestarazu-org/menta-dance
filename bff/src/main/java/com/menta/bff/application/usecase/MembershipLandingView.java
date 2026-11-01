package com.menta.bff.application.usecase;

import com.menta.bff.domain.membership.PriceRow;
import java.util.List;

/**
 * What the temporary membership landing shows.
 *
 * @param priceRows   rows of the price table, in display order
 * @param salesClosed whether no price is in force any more
 * @param serverNow   server instant (ISO-8601 UTC): the browser countdown corrects its own clock with it
 */
public record MembershipLandingView(List<PriceRow> priceRows, boolean salesClosed, String serverNow) {
}
