## MODIFIED Requirements

### Requirement: BFF-assembled sample view on subscription denial

When the lesson endpoint returns a bare `403` (`LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED`),
the BFF MUST NOT attempt to parse lesson metadata from that response body — it has none.
The BFF MUST instead render a sample view built from the course-detail lesson summary
already fetched for this request (title, duration) plus a BFF-owned subscription
call-to-action. This view MUST NOT include a `videoId`, a stream URL, or any call to the
`/stream` endpoint.

That call-to-action MUST link to the BFF's `/plans` route.

(Previously: until issue #177 delivered a BFF plans page, the call-to-action was a
message with no navigable link — no BFF plans page existed, and a placeholder link would
have repeated the debt pattern this change exists to correct. #177 fulfills that
condition, so the CTA now links to a real, BFF-owned route.)

#### Scenario: Non-entitled visitor sees the sample view, no stream leak

- GIVEN a non-entitled visitor (anonymous or authenticated without entitlement) requests a premium lesson
- WHEN the lesson endpoint returns bare `403`
- THEN the BFF renders the lesson title and duration from course detail plus a subscription CTA
- AND the response contains no `videoId` and no stream URL
- AND the BFF makes no request to the `/stream` endpoint

#### Scenario: Subscription CTA links to the plans page

- GIVEN a non-entitled visitor sees the sample view
- WHEN the page renders the subscription call-to-action
- THEN the CTA links to `/plans`
