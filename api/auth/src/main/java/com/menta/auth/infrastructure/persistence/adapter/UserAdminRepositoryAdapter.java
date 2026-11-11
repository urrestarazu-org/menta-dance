package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.out.UserAdminRepository;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.auth.infrastructure.persistence.mapper.UserMapper;
import com.menta.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.UserSearchSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Adapter for {@link UserAdminRepository}: Spring Data stays here, the application only sees plain types.
 *
 * <p>The listing has no {@code sort} parameter on purpose: the order is fixed and deterministic (email, then id as
 * tie-breaker), so paging never repeats or skips a row. There is no index behind the search either: the table is
 * small and the text filter is a contains-match, which no B-tree index would serve.
 */
@Component
@RequiredArgsConstructor
public class UserAdminRepositoryAdapter implements UserAdminRepository {

    private static final Sort LISTING_ORDER = Sort.by("email", "id");

    private final UserJpaRepository jpaRepository;

    @Override
    public UserPage search(ListUsersQuery query) {
        Page<UserJpaEntity> page = jpaRepository.findAll(
            UserSearchSpecifications.matching(query.role(), query.search()),
            PageRequest.of(query.page(), query.size(), LISTING_ORDER)
        );
        return new UserPage(
            page.getContent().stream().map(UserMapper::toDomain).map(UserSummary::from).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
        );
    }
}
