package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalDaySessionView;
import java.time.LocalDate;
import java.util.List;

/** In-port for the staff listing of every session of one academy day, across all courses (#355). */
public interface ListPhysicalSessionsByDateUseCase {

    /**
     * Lists the sessions that start on {@code date} in the academy's time zone, whatever their
     * status or course, ordered by start time and then by course name.
     */
    List<PhysicalDaySessionView> list(LocalDate date);
}
