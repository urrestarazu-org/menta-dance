# Delta for Bank Transfer Subscription

## MODIFIED Requirements

### Requirement: Creating a bank-transfer subscription or physical purchase shares one daily budget

A successful `BANK_TRANSFER` subscription request (see the `billing-subscriptions`
capability) MUST return the bank details needed to complete the transfer: CBU,
alias, account holder name, CUIT, the exact amount, and a payment reference tying
the transfer to that `Payment`. The system MUST reject an 11th `BANK_TRANSFER`
creation request from the same user within the same calendar day with
`429 application/problem+json`, creating nothing — counting subscription
creations and physical-purchase creations (see `bank-transfer-physical-purchase`)
together against one shared per-user daily budget (D4), not two separate
counters.

(Previously: described subscription creation only, against a subscription-only
daily budget. The budget, its 10/day limit, its status code, and its response
shape are unchanged; it now also counts bank-transfer physical purchase
creations.)

#### Scenario: Response carries usable bank details

- GIVEN a successful `BANK_TRANSFER` subscription request
- WHEN the response is read
- THEN it includes CBU, alias, holder, CUIT, amount, and a reference identifying
  the `Payment`

#### Scenario: An 11th daily bank-transfer request is rejected

- GIVEN a user who already made 10 `BANK_TRANSFER` subscription requests today
- WHEN they make an 11th
- THEN the response is `429 application/problem+json`
- AND no `Subscription` or `Payment` is created

#### Scenario: The budget counts subscriptions and physical purchases together

- GIVEN a user who already made a mix of 10 `BANK_TRANSFER` subscription and
  physical-purchase creations today
- WHEN they make an 11th `BANK_TRANSFER` creation of either kind
- THEN the response is `429 application/problem+json`
- AND nothing is created, regardless of which kind was attempted
