-- V13: immutable physical course quotes (US-BILLING-006). course_id and
-- selected_session_id are references BY VALUE into Physical's space --
-- deliberately no FK, same discipline as billing_physical_course_pricing.course_id
-- (docs/25-ARCHITECTURE-RULES.md). A quote is a single insert -- never
-- UPDATEd or DELETEd; there is no revisions table because nothing ever
-- revises it.

CREATE TABLE billing_physical_course_quotes (
    id CHAR(36) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    purchase_type VARCHAR(20) NOT NULL,
    monthly_price_amount DECIMAL(10,2) NOT NULL,
    monthly_price_currency CHAR(3) NOT NULL,
    individual_surcharge_percent DECIMAL(5,2) NOT NULL,
    pricing_version INT NOT NULL,
    scheduled_session_count INT NOT NULL,
    selected_session_id VARCHAR(64) NULL,
    amount_value DECIMAL(10,2) NOT NULL,
    amount_currency CHAR(3) NOT NULL,
    availability VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_physical_course_quotes_course_id (course_id),
    CONSTRAINT chk_billing_physical_quotes_amount_non_negative CHECK (amount_value >= 0),
    CONSTRAINT chk_billing_physical_quotes_session_count_positive CHECK (scheduled_session_count > 0)
);
