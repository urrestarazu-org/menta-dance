-- V32 (#771): billing_payments stores the payment method (MERCADO_PAGO or BANK_TRANSFER) instead of leaving
-- the read side to deduce it.
--
-- V31 is the head migration this builds on.
--
-- Until now GET /api/v1/billing/payments/me worked the method out from how each checkout happened to write its
-- rows, which breaks on hand-loaded rows and on any new method. The column holds the PaymentMethod enum name
-- (VARCHAR, like billing_plan_payment_methods.payment_method).
--
-- Backward compatible, in three steps:
--   1. add the column NULLABLE, so the code that does not know it yet keeps inserting;
--   2. backfill every existing row with EXACTLY the rule the read side applied until now;
--   3. require NOT NULL, once no row is left without a value.
--
-- The backfill rule, one case per kind of payment:
--   * physical course purchase (target_modality = 'PHYSICAL'): BANK_TRANSFER when the external reference starts
--     with 'PHY-BT-' (the bank-transfer checkout's prefix), MERCADO_PAGO otherwise ('PHY-' is Checkout Pro's);
--   * subscription (any other target_modality): both rails share the 'SUB-' reference, so the subscription that
--     points at the payment decides. A provider_preference_id means Checkout Pro stored a preference, MERCADO_PAGO;
--     no preference means BANK_TRANSFER, which never stores one.
--     EDGE, kept on purpose: a subscription payment with NO subscription row at all (the LEFT JOIN finds nothing,
--     so the preference is NULL) is BANK_TRANSFER, the same answer the read side gave.
ALTER TABLE billing_payments
    ADD COLUMN payment_method VARCHAR(30) NULL AFTER expected_merchant_account_id;

UPDATE billing_payments p
    LEFT JOIN billing_subscriptions s ON s.payment_id = p.id
SET p.payment_method = CASE
    WHEN p.target_modality = 'PHYSICAL' AND p.expected_external_reference LIKE BINARY 'PHY-BT-%'
        THEN 'BANK_TRANSFER'
    WHEN p.target_modality = 'PHYSICAL'
        THEN 'MERCADO_PAGO'
    WHEN s.provider_preference_id IS NULL
        THEN 'BANK_TRANSFER'
    ELSE 'MERCADO_PAGO'
END;

ALTER TABLE billing_payments
    MODIFY COLUMN payment_method VARCHAR(30) NOT NULL;
