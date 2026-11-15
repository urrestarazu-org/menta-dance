-- V31 (#767): index for the student's own payments, GET /api/v1/billing/payments/me.
--
-- V30 is the head migration this builds on.
--
-- The query filters billing_payments by user_id and orders by created_at DESC, id DESC with a LIMIT. V14's
-- idx_billing_payments_user_id (user_id) finds the user's rows but not in that order, so MySQL reads every
-- payment of the user and sorts them before it can apply the limit. InnoDB appends the primary key (id) to every
-- secondary index, so (user_id, created_at) is in effect (user_id, created_at, id): the exact filter and order of
-- the query, read backwards and stopped at the limit, with no sort.
--
-- The composite index replaces the single-column one instead of sitting next to it: a lookup by user_id alone
-- uses its leftmost column just as well, and no foreign key depends on idx_billing_payments_user_id. No data is
-- touched.
ALTER TABLE billing_payments
    DROP KEY idx_billing_payments_user_id,
    ADD KEY idx_billing_payments_user_created (user_id, created_at);
