package com.menta.auth.application.dto;

import java.util.List;

/** One page of the admin user listing, ordered by email and then id. */
public record UserPage(List<UserSummary> items, int page, int size, long totalElements, int totalPages) {
}
