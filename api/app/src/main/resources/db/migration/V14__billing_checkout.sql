-- V14: subscription checkout (US-BILLING-010, #116). Closes the payment
-- circuit: until now only the VERIFICATION half existed (webhook -> inbox ->
-- PaymentVerificationService) and nothing ever created a billing_payments row.
--
-- Why billing_payments.provider_payment_id becomes nullable
-- --------------------------------------------------------
-- V8 assumed a Payment is born with the provider's payment id already known.
-- That is false for Mercado Pago's Checkout Pro: creating a preference and
-- redirecting the buyer yields a preference id and an init_point, never a
-- payment.id -- that identifier only comes into existence once the buyer
-- actually pays, and reaches us through the signed webhook. The only
-- identifier both sides share at checkout time is the external reference we
-- generate ourselves, so THAT becomes the correlation key and gets a UNIQUE
-- constraint of its own.
--
-- The existing unique index on provider_payment_id is kept as-is: MySQL
-- UNIQUE indexes admit any number of NULL values, so uniqueness still holds
-- for every bound (non-null) id while unbound checkouts coexist freely.
-- (SQL standard behaviour, and MySQL's documented behaviour for both InnoDB
-- BTREE unique indexes -- verified, not assumed.)
--
-- Why NOT NULL columns need no backfill
-- -------------------------------------
-- billing_payments, billing_purchases and billing_subscriptions are empty by
-- construction: no code path in the repository has ever inserted into them
-- (the Payment javadoc declared its own creator "out of scope" of
-- US-BILLING-002, and the webhook flow only ever UPDATEs a pre-existing row).
-- So user_id can be added NOT NULL without a default and without an
-- intermediate nullable step, and billing_subscriptions can be recreated
-- outright rather than reshaped through eight ALTERs.

-- ---------------------------------------------------------------------------
-- billing_payments: whose payment it is, and the new correlation key.
-- ---------------------------------------------------------------------------
ALTER TABLE billing_payments
    ADD COLUMN user_id BINARY(16) NOT NULL AFTER id;

ALTER TABLE billing_payments
    MODIFY COLUMN provider_payment_id VARCHAR(64) NULL;

ALTER TABLE billing_payments
    ADD UNIQUE KEY uq_billing_payments_external_reference (expected_external_reference);

ALTER TABLE billing_payments
    ADD KEY idx_billing_payments_user_id (user_id);

-- ---------------------------------------------------------------------------
-- billing_plan_payment_methods: which rails a plan accepts (escenario 4b).
-- Its own table, like billing_plan_courses -- a plan accepts a SET, and a
-- comma-joined column could neither be constrained nor indexed.
-- ---------------------------------------------------------------------------
CREATE TABLE billing_plan_payment_methods (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BINARY(16) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_plan_payment_methods (plan_id, payment_method),
    CONSTRAINT fk_billing_plan_payment_methods_plan
        FOREIGN KEY (plan_id) REFERENCES billing_plans (id)
);

-- Every plan that already exists was published under the assumption that
-- online payment is available, so backfill it rather than leaving live plans
-- accepting nothing.
INSERT INTO billing_plan_payment_methods (plan_id, payment_method)
SELECT id, 'MERCADO_PAGO' FROM billing_plans;

-- ---------------------------------------------------------------------------
-- billing_subscriptions: rebuilt around user + plan + vigencia.
--
-- V8's shape (payment_id, virtual_course_id, FulfillmentStatus) modelled a
-- subscription as access to ONE course with no owner and no term. The
-- business is per plan, per user, for durationDays.
--
-- Two unique constraints carry the concurrency guarantees, both decided by
-- the database rather than by an exists-then-insert check -- the same
-- discipline as billing_webhook_inbox.dedupe_key:
--
--   * (user_id, idempotency_key): escenario 5, replaying a key returns the
--     first result instead of opening a second charge.
--   * active_user_id: at most one subscription may occupy a user's slot.
--     MySQL has no partial indexes, so this column is an application-
--     maintained projection of user_id -- set while the status is
--     PENDING/ACTIVE, NULL once the subscription is EXPIRED or CANCELLED.
--     A UNIQUE index admits many NULLs, so terminated subscriptions stop
--     blocking a new checkout without being deleted (escenario 6).
--     SubscriptionJpaMapper is its single writer and derives it from status.
--
-- start_date/end_date stay NULL until the payment settles: a PENDING
-- subscription has no vigencia to report, and defaulting them to "now" would
-- invent a term nobody paid for.
-- ---------------------------------------------------------------------------
DROP TABLE billing_subscriptions;

CREATE TABLE billing_subscriptions (
    id BINARY(16) NOT NULL,
    payment_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    plan_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    active_user_id BINARY(16) NULL,
    status VARCHAR(20) NOT NULL,
    fulfillment_status VARCHAR(30) NOT NULL,
    start_date DATETIME(3) NULL,
    end_date DATETIME(3) NULL,
    provider_preference_id VARCHAR(128) NULL,
    checkout_url VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_subscriptions_payment_id (payment_id),
    UNIQUE KEY uq_billing_subscriptions_idempotency (user_id, idempotency_key),
    UNIQUE KEY uq_billing_subscriptions_active_user (active_user_id),
    KEY idx_billing_subscriptions_user_status (user_id, status),
    CONSTRAINT fk_billing_subscriptions_payment
        FOREIGN KEY (payment_id) REFERENCES billing_payments (id),
    CONSTRAINT fk_billing_subscriptions_plan
        FOREIGN KEY (plan_id) REFERENCES billing_plans (id)
);

-- ---------------------------------------------------------------------------
-- billing_subscription_courses: the course snapshot (escenario 2b).
--
-- Cannot be derived from billing_plan_courses, which reflects the plan's
-- CURRENT composition: an admin removing a course from a plan would then cut
-- off a student who already paid for it, in the middle of their term. The
-- snapshot is written once, at activation, and never revised.
--
-- course_id is a reference BY VALUE into Virtual's UUID space -- no FK, same
-- rule as billing_plan_courses (docs/25-ARCHITECTURE-RULES.md).
-- ---------------------------------------------------------------------------
CREATE TABLE billing_subscription_courses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subscription_id BINARY(16) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_billing_subscription_courses (subscription_id, course_id),
    CONSTRAINT fk_billing_subscription_courses_subscription
        FOREIGN KEY (subscription_id) REFERENCES billing_subscriptions (id)
);
