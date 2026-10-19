-- V12: versioned physical course pricing and its append-only audit trail
-- (US-BILLING-009, #37). course_id is a reference by VALUE into Physical's
-- UUID space -- deliberately no FK, same discipline as
-- billing_plan_courses.course_id (docs/25-ARCHITECTURE-RULES.md).

CREATE TABLE billing_physical_course_pricing (
    course_id VARCHAR(64) NOT NULL,
    monthly_price DECIMAL(10,2) NOT NULL,
    monthly_price_currency CHAR(3) NOT NULL,
    individual_surcharge_percent DECIMAL(5,2) NOT NULL,
    version INT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (course_id),
    CONSTRAINT chk_billing_physical_pricing_price_non_negative CHECK (monthly_price >= 0),
    CONSTRAINT chk_billing_physical_pricing_surcharge_positive CHECK (individual_surcharge_percent > 0)
);

-- Append-only: no UPDATE/DELETE is ever issued against this table from the
-- application (US-BILLING-009: "historial inmutable... no se permiten UPDATE
-- o DELETE sobre revisiones"). previous_value is NULL only for a course's
-- first-ever published version -- there is no "before" to snapshot.
CREATE TABLE billing_physical_course_pricing_revisions (
    id BINARY(16) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    reason TEXT NOT NULL,
    version INT NOT NULL,
    previous_value TEXT NULL,
    new_value TEXT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_physical_pricing_revisions_course_id (course_id)
);
