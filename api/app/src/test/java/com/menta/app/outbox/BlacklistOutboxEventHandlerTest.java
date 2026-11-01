package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression coverage for #88.
 *
 * <p>This handler used to blacklist {@code row.getAggregateId()} for four
 * event types. None of the four was correct:</p>
 *
 * <ul>
 *   <li>{@code REFRESH_REVOKED} carries a {@code familyId} — wrote
 *       {@code blacklist:jti:{familyId}}, a key no jti can ever match.</li>
 *   <li>{@code USER_LOGGED_OUT} carries the <em>refresh token id</em>, not a
 *       jti — same dead key.</li>
 *   <li>{@code auth.AuthUserLoggedIn} carried a real jti, but it is the token
 *       that was just minted: had anything read the blacklist, every
 *       successful login would have produced a born-revoked access token.</li>
 *   <li>{@code auth.RefreshRotated} likewise.</li>
 * </ul>
 *
 * <p>No jti reaches any event today — {@code LogoutCommand} does not even
 * carry one — so jti-based revocation cannot work without threading the jti
 * from the controller through to the event. {@code tokenVersion} already
 * covers logout, refresh-reuse and password reset, so this handler no longer
 * claims any event type.</p>
 */
@ExtendWith(MockitoExtension.class)
class BlacklistOutboxEventHandlerTest {

    @Mock private TokenBlacklistPort tokenBlacklistPort;

    private BlacklistOutboxEventHandler handler() {
        return new BlacklistOutboxEventHandler(tokenBlacklistPort, 900);
    }

    @Test
    void no_longer_claims_any_event_whose_aggregate_id_is_not_a_jti() {
        BlacklistOutboxEventHandler handler = handler();

        assertThat(handler.supports(AuthOutboxEventTypes.REFRESH_REVOKED)).isFalse();
        assertThat(handler.supports(AuthOutboxEventTypes.USER_LOGGED_OUT)).isFalse();
        // Retired by #672 (no longer produced); still must not be claimed for legacy rows.
        assertThat(handler.supports("auth.RefreshRotated")).isFalse();
    }

    @Test
    void no_longer_blacklists_a_freshly_minted_access_token() {
        // The worst of the four: auth.AuthUserLoggedIn carried the jti of the
        // token issued by that very login (event retired in #672).
        assertThat(handler().supports("auth.AuthUserLoggedIn")).isFalse();
    }

    @Test
    void does_not_claim_password_reset_events() {
        assertThat(handler().supports(AuthOutboxEventTypes.PASSWORD_RESET_REQUESTED)).isFalse();
    }
}
