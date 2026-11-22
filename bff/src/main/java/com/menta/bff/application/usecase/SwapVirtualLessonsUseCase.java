package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualLessonPosition;

/**
 * Use case that moves a lesson one place by swapping its {@code order} with its neighbour's (#831).
 *
 * <p>Part of Clean Architecture application layer. The API has no swap: a lesson is changed one at a time, so the
 * swap is two updates and cannot be atomic. If the second one fails the first is undone as well as it can be, so
 * a failure does not leave two lessons with the same order. Which lesson is the neighbour is the caller's: it
 * reads the structure. Ownership and existence are the API's; its refusals reach the caller as
 * {@code VirtualCourseManagementApiClient.VirtualCourseManagementException}.
 * </p>
 */
public interface SwapVirtualLessonsUseCase {

    /**
     * Swaps the orders of two lessons of the same module.
     *
     * @param accessToken caller's access token
     * @param lesson      the lesson that moves, with the order it holds; an id that is not a UUID is a lesson that
     *                    does not exist
     * @param neighbour   the lesson it trades places with, with the order it holds
     */
    void execute(String accessToken, VirtualLessonPosition lesson, VirtualLessonPosition neighbour);
}
