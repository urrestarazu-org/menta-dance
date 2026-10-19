-- V5: billing subscription plans and their included courses (US-BILLING-001).
-- billing_plan_courses.course_id is a reference by VALUE into Virtual/Physical's
-- UUID space — deliberately no FK: those tables belong to other modules and
-- Billing must never JOIN into their schema (docs/25-ARCHITECTURE-RULES.md).

CREATE TABLE billing_plans (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'ARS',
    duration_days INT NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL,
    terms_and_conditions TEXT NOT NULL,
    cancellation_policy TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_plans_status_price (status, price),
    CONSTRAINT chk_billing_plans_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_billing_plans_duration_positive CHECK (duration_days > 0)
);

CREATE TABLE billing_plan_courses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_id BINARY(16) NOT NULL,
    course_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_billing_plan_courses_plan_id (plan_id),
    CONSTRAINT fk_billing_plan_courses_plan
        FOREIGN KEY (plan_id) REFERENCES billing_plans (id)
);
