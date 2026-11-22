package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ListManagedVirtualCoursesUseCase}: the API decides which courses the caller sees. */
@RequiredArgsConstructor
public class ListManagedVirtualCoursesUseCaseImpl implements ListManagedVirtualCoursesUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualCoursesView execute(String accessToken) {
        List<ManagedVirtualCourseRow> rows = courseApiClient.listManagedCourses(VirtualCourseSupport.token(accessToken))
                .stream().map(ManagedVirtualCourseRow::of).toList();
        return new ManagedVirtualCoursesView(rows, false);
    }
}
