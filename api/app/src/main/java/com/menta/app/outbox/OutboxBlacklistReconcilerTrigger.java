package com.menta.app.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The periodic trigger for {@link OutboxBlacklistReconciler}, split out so the schedule can be
 * turned off without taking the reconciler itself with it. Mirrors billing's
 * {@code SubscriptionExpiryReconciler} / {@code SubscriptionExpiryWorker} pair.
 *
 * <p>{@code @ConditionalOnProperty} sits on this CLASS, not on {@link #tick()} — Spring evaluates
 * that condition on a component/configuration class or a {@code @Bean} method; on a plain method
 * it is inert and the job would run unconditionally. {@code auth.outbox.reconcile-rate-ms} only
 * controls the <em>interval</em>: {@code @Scheduled(fixedRate)} declares no initial delay, so its
 * first tick fires as soon as the context is up no matter how large that interval is. Raising the
 * rate is not an off switch; this property is.</p>
 *
 * <p>Why that matters beyond tidiness (#172): {@link OutboxBlacklistReconciler#processBatch()}
 * calls the <em>void</em> {@code TokenBlacklistPort.writeHeartbeat()}, and that port is a
 * {@code @MockBean} in nearly every integration context. Mockito's ongoing-stubbing state is
 * per-mock rather than per-thread, so a scheduler thread landing between a test's own invocation
 * on that mock and the {@code when(...)} wrapping it makes Mockito believe the void method is the
 * one being stubbed — surfacing as {@code CannotStubVoidMethodWithReturnValue} from a
 * {@code @BeforeEach} that never mentioned the reconciler.</p>
 *
 * <p>Keeping the worker unconditional is the other half of the fix: tests such as
 * {@code AuthRevocationIntegrationTest} need the reconciler bean to drive a deterministic pass by
 * hand, and would fail to start if disabling the schedule also removed it.</p>
 */
@Component
@ConditionalOnProperty(
    name = "auth.outbox.reconcile.enabled", havingValue = "true", matchIfMissing = true
)
public class OutboxBlacklistReconcilerTrigger {

    private final OutboxBlacklistReconciler reconciler;

    public OutboxBlacklistReconcilerTrigger(OutboxBlacklistReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @Scheduled(fixedRateString = "${auth.outbox.reconcile-rate-ms:5000}")
    public void tick() {
        reconciler.tick();
    }
}
