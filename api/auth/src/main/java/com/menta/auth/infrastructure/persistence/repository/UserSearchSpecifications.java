package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.domain.model.Role;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Optional filters of the admin user listing, combined with AND. */
public final class UserSearchSpecifications {

    /** Escape character of the LIKE patterns built by {@link #escapeLike}. */
    static final char LIKE_ESCAPE = '\\';

    private UserSearchSpecifications() {
    }

    /**
     * Builds the filter: users with the role (when given) whose email, first name or last name contains the
     * search text (when given), ignoring case.
     *
     * @param role the role to match, or {@code null} for any
     * @param search the text to look for, or {@code null} for no text filter; taken literally, never as a pattern
     */
    public static Specification<UserJpaEntity> matching(Role role, String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            if (search != null) {
                String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
                List<Predicate> anyField = new ArrayList<>();
                for (String field : List.of("email", "firstName", "lastName")) {
                    Expression<String> lowered = builder.lower(root.<String>get(field));
                    anyField.add(builder.like(lowered, pattern, LIKE_ESCAPE));
                }
                predicates.add(builder.or(anyField.toArray(Predicate[]::new)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escapes the characters that mean something inside a LIKE pattern: the escape character, {@code %}, {@code _}. */
    static String escapeLike(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 4);
        for (char character : text.toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
