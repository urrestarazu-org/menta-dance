package com.menta.bff.infrastructure.web.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The closed sets of the course form with their Spanish labels (UI copy), in the order the selects show them.
 * Keys are the values the form submits and the API speaks.
 */
public final class CourseFormOptions {

    /** Weekdays in the academy's zone, Monday first. */
    public static final Map<String, String> DAYS = ordered(
            "MONDAY", "Lunes", "TUESDAY", "Martes", "WEDNESDAY", "Miércoles", "THURSDAY", "Jueves",
            "FRIDAY", "Viernes", "SATURDAY", "Sábado", "SUNDAY", "Domingo");

    public static final Map<String, String> LEVELS = ordered(
            "BEGINNER", "Principiante", "INTERMEDIATE", "Intermedio", "ADVANCED", "Avanzado");

    public static final Map<String, String> STATUSES = ordered("ACTIVE", "Activo", "INACTIVE", "Inactivo");

    private CourseFormOptions() {
    }

    /**
     * Words a level.
     *
     * @param level {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
     * @return the Spanish label, or the value as it came when it is not a known one
     */
    public static String levelLabel(String level) {
        return LEVELS.getOrDefault(level, level);
    }

    private static Map<String, String> ordered(String... keysAndLabels) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndLabels.length; i += 2) {
            map.put(keysAndLabels[i], keysAndLabels[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
