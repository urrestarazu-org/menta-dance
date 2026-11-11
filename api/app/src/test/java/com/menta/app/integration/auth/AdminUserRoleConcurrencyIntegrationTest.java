package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
import com.menta.auth.domain.exception.LastAdminCannotBeDemotedException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The "at least one active administrator" rule under concurrency (#378): two administrators demoting each other at
 * the same time must never leave the system with none.
 *
 * <p>Without the row locks both transactions would count two active administrators, both would demote, and none
 * would remain. With them the second transaction waits for the first, then sees only itself and is rejected. The
 * outcome is therefore deterministic whatever the interleaving: exactly one demotion succeeds. The race is
 * repeated so that a regression (a missing lock) shows up as a failure instead of passing by luck.</p>
 */
class AdminUserRoleConcurrencyIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final int ROUNDS = 25;

    @Autowired private ChangeUserRoleUseCase changeUserRole;

    @Test
    void two_admins_demoting_each_other_at_the_same_time_leave_exactly_one_admin() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                jdbcTemplate.update("DELETE FROM auth_user_audit");
                jdbcTemplate.update("DELETE FROM auth_users");
                User first = newUser(Role.ADMIN);
                User second = newUser(Role.ADMIN);
                CyclicBarrier startTogether = new CyclicBarrier(2);

                Future<Boolean> firstDemotesSecond = executor.submit(demote(startTogether, first, second));
                Future<Boolean> secondDemotesFirst = executor.submit(demote(startTogether, second, first));
                List<Boolean> outcomes = List.of(firstDemotesSecond.get(30, TimeUnit.SECONDS),
                    secondDemotesFirst.get(30, TimeUnit.SECONDS));

                assertThat(outcomes).as("round %d: exactly one demotion may succeed", round)
                    .containsExactlyInAnyOrder(true, false);
                assertThat(countActiveAdmins()).as("round %d: active admins left", round).isEqualTo(1);
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_user_audit", Integer.class))
                    .as("round %d: only the successful change is audited", round).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void overlapping_demotions_among_three_admins_never_remove_the_last_one() throws Exception {
        // Three admins; a demotes b while c demotes a. Both may succeed (c remains), but the system must never
        // end up with none.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                jdbcTemplate.update("DELETE FROM auth_user_audit");
                jdbcTemplate.update("DELETE FROM auth_users");
                User a = newUser(Role.ADMIN);
                User b = newUser(Role.ADMIN);
                User c = newUser(Role.ADMIN);
                CyclicBarrier startTogether = new CyclicBarrier(2);

                Future<Boolean> firstDemotion = executor.submit(demote(startTogether, a, b));
                Future<Boolean> secondDemotion = executor.submit(demote(startTogether, c, a));
                firstDemotion.get(30, TimeUnit.SECONDS);
                secondDemotion.get(30, TimeUnit.SECONDS);

                assertThat(countActiveAdmins()).as("round %d: active admins left", round).isGreaterThanOrEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> demote(CyclicBarrier startTogether, User actor, User target) {
        return () -> {
            startTogether.await(10, TimeUnit.SECONDS);
            try {
                changeUserRole.changeRole(new ChangeUserRoleCommand(
                    actor.getId().getValue(), target.getId().getValue(), Role.STUDENT
                ));
                return true;
            } catch (LastAdminCannotBeDemotedException rejected) {
                return false;
            }
        };
    }

    private int countActiveAdmins() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auth_users WHERE role = 'ADMIN' AND status = 'ACTIVE'", Integer.class
        );
        return count == null ? 0 : count;
    }
}
