package com.menta.bff.application.usecase;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link DescribeSessionConflictsUseCase}. */
@RequiredArgsConstructor
public class DescribeSessionConflictsUseCaseImpl implements DescribeSessionConflictsUseCase {

    private final ZoneId displayZone;

    @Override
    public List<String> execute(List<Instant> conflictingDates) {
        return conflictingDates.stream()
                .distinct()
                .sorted()
                .map(instant -> ClassLabels.shortMoment(instant, displayZone))
                .toList();
    }
}
