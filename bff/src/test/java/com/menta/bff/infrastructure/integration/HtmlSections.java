package com.menta.bff.infrastructure.integration;

/** Helpers to assert on a rendered page without the site header and footer shared by every view. */
final class HtmlSections {

    private static final String MAIN_CLOSE = "</main>";

    private HtmlSections() {
    }

    /**
     * The content of the {@code <main>} landmark. Assertions about a page's own links, forms or lists use
     * it so the shared header and footer (account links, logout form, footer lists) do not interfere.
     * Returns the whole document when it has no main landmark.
     */
    static String mainOf(String html) {
        int start = html.indexOf("<main");
        int end = html.indexOf(MAIN_CLOSE);
        if (start < 0 || end < start) {
            return html;
        }
        return html.substring(start, end + MAIN_CLOSE.length());
    }
}
