# Apply Progress: demo-seed-data (#379)

All 6 slices are done and merged into `develop`.

| Slice | Tasks | Content | PR |
|---|---|---|---|
| 1 | 1.1-1.8 | `DemoProfileGuard`, `DemoIds`, `DemoSeedOrder`, `DemoUserFixture`, `application-demo.yml` | #464 |
| 2 | 2.1-2.5 | `DemoVirtualCatalogFixture`, `VirtualConfiguration` profile `e2e-bunny-net \| demo` | #465 |
| 3 | 3.1-3.9 | `DemoPhysicalCatalogFixture` (432 lines, over budget, flagged) | #469 |
| 4 | 4.1-4.3 | `DemoQrReaderFixture` (invented device id `...5a1`, names "Demo QR reader" / "Demo entrance") | #469 |
| 5 | 5.1-5.7 | `DemoBillingFixture`: plans Virtual 30000 / Completo 60000 ARS (30 days), physical 25000 / 20000 ARS, 20% surcharge, 90-day TRIAL | #468 |
| 6 | 6.1-6.6 | ArchUnit rules, `NoDemoSeedMigrationTest`, README and docs/26 | #470 |

Slices 3 and 4 were first merged into intermediate stacked branches by mistake (#466, #467) and landed in `develop` through the recovery PR #469.

## Slice 6 TDD evidence

- RED for rules (a) and (b): temporary `com.menta.app.TempViolationAb` in `api/app` main referencing `DemoIds` and `DemoUserFixture`.
- RED for rule (c): temporary `com.menta.auth.infrastructure.demo.TempViolationC` in `api/app` main referencing `DemoBillingFixture` (it lives in `api/app` because `auth` cannot compile against `billing`).
- Result: 3 of 10 `ArchitectureTest` tests failed with exactly 1 violation each.
- RED for 6.3: temporary `V9999__temp_red_demo.sql` containing `admin@menta.dance` made `NoDemoSeedMigrationTest` fail.
- GREEN: temporary files removed, 10/10 and 1/1 pass with no production change.
- REFACTOR: clearer `BUSINESS_MODULES` loop; fixed a Checkstyle `CustomImportOrder` error.

## Notes

- Password env vars are `DEMO_ADMIN_PASSWORD`, `DEMO_INSTRUCTOR_PASSWORD`, `DEMO_STUDENT_PASSWORD`, `DEMO_RECEPTIONIST_PASSWORD`.
- Migrations live only in `api/app`.
- Slices 1 to 5 recorded a single compile-failure RED per class rather than one per test; per-task RED/GREEN tables were not kept (verify warning W3).
