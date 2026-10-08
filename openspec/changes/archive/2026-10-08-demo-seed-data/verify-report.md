# Verify Report: demo-seed-data (#379)

Verified at `origin/develop` af0fd4f (all 6 slices merged). Mode: hybrid, Strict TDD.

**Verdict: PASS WITH WARNINGS** (CRITICAL 0, WARNING 4, SUGGESTION 3)

## Completeness

- `tasks.md`: 40/40 tasks `[x]`.
- Specs: 14 requirements and 32 scenarios, each mapped to an implementation and a test that passed at runtime.

## Commands run

- `./gradlew :api:shared:build :api:auth:build :api:virtual:build :api:physical:build :api:billing:build :api:app:build :bff:build`: exit 0. All tasks were up-to-date, so this alone was weak evidence.
- Same build with `--no-build-cache` and `cleanTest` on every module: exit 0, BUILD SUCCESSFUL in 4m28s. The 6 `api` modules report 2869 tests, 0 failures, 0 errors, 0 skipped. Checkstyle, SpotBugs and JaCoCo gates are part of `build`. The `bff` test results were not counted.

## Manual walkthrough

Run with throwaway containers `verify379-mysql` (port 33999) and `verify379-redis` (port 63999), removed afterwards. The local `menta-*` containers were not touched.

- First boot, `demo` profile, empty DB: login returned 200 for the 4 users with the README passwords. Rows: 4 users, 2 virtual courses, 4 modules, 4 lessons (2 free), 2 physical courses, 13 sessions (12 regular + 1 rolling), 2 confirmed seats, 2 plans, 6 plan-course links, 4 payment-method links, 2 physical prices, 1 ACTIVE TRIAL subscription, 0 devices.
- Second boot with `DEMO_QR_READER_SECRET=s1`: all counts identical except `physical_devices` 0 -> 1. The stored hash does not contain the plaintext. The session count stayed at 13 (rolling session reused).
- Boot with `demo,prod`: exit 1, but the failure came from `AuthConfiguration` rejecting the default JWT secret in prod, not from `DemoProfileGuard`.

## Warnings

- **W1**: the `demo` + `prod` abort is not attributable to `DemoProfileGuard` at runtime. The guard is only unit-tested with `MockEnvironment`.
- **W2**: every fixture test is a Mockito unit test. There is no Spring/DB test of idempotency; it is covered only by the manual run.
- **W3**: apply-progress holds a detailed RED/GREEN table only for slice 6. Slices 1 to 5 are one-line summaries, so triangulation and safety net cannot be audited from the artifact.
- **W4**: slice 3 was 432 lines, over the 400-line budget (flagged during apply).

## Suggestions

- Add a Spring-context test for `DemoProfileGuard` with `demo` + `prod`.
- Add a Testcontainers idempotency test.
- Keep a per-slice TDD evidence table in apply-progress.

## Not verified

- The real `demo` + `prod` abort by `DemoProfileGuard`.
- QR secret rotation (s1 to s2) at runtime.
- The rolling-session replace path after its window closed.
- A free-lesson stream request over HTTP and the local signed URL.
- Exact 90-day trial expiry (only checked that the row is an ACTIVE TRIAL).
- Full `./gradlew build` including Android (SDK missing in the worktree, environmental).
