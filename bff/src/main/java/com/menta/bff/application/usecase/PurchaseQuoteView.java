package com.menta.bff.application.usecase;

/**
 * View model for the quote page of a physical class.
 *
 * @param quoteId       quote identifier, posted back to pay
 * @param courseId      course identifier, used for the way back to the class page
 * @param courseTitle   course title
 * @param modalityLabel Spanish wording of what is being bought
 * @param amountLabel   price formatted for es-AR with its currency
 * @param sessionCount  sessions in the quoted period
 * @param sessionLabel  the chosen session of an individual quote, {@code null} when unknown or monthly
 * @param expiresLabel  when the quote stops being payable, in the academy's zone
 * @param unavailable   whether the capacity projection says there is no room (informative)
 * @param idempotencyKey key that identifies this rendering of the page; reposting the same form reuses it
 */
public record PurchaseQuoteView(
        String quoteId,
        String courseId,
        String courseTitle,
        String modalityLabel,
        String amountLabel,
        int sessionCount,
        String sessionLabel,
        String expiresLabel,
        boolean unavailable,
        String idempotencyKey) {

    /** Key of the bank transfer attempt of this rendering: distinct from the card one, stable on repost. */
    public String transferIdempotencyKey() {
        return idempotencyKey + "-transfer";
    }
}
