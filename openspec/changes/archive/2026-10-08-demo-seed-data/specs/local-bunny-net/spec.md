# Delta for Local Bunny.net Adapter

## MODIFIED Requirements

### Requirement: Profile-guarded, mutually exclusive adapter selection

`VirtualConfiguration` MUST expose exactly one `BunnyNetSignatureService`
bean: `LocalBunnyNetSignatureService` when profile `e2e-bunny-net` OR
`demo` is active, `StringFormatBunnyNetSignatureService` otherwise.
Startup MUST fail closed when `e2e-bunny-net` or `demo` combines with
`prod`, `production`, or `staging` (case-insensitive).
(Previously: only `e2e-bunny-net` selected the local adapter and only it was guarded.)

#### Scenario: Local profile selects the local adapter

- GIVEN profile `e2e-bunny-net` is active
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN it MUST resolve to `LocalBunnyNetSignatureService`
- AND no outbound request MUST reach Bunny.net

#### Scenario: Demo profile selects the local adapter

- GIVEN profile `demo` is active
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN it MUST resolve to `LocalBunnyNetSignatureService`
- AND exactly one bean of that type MUST exist

#### Scenario: Both local profiles together

- GIVEN profiles `demo` and `e2e-bunny-net` are active
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN exactly one `LocalBunnyNetSignatureService` bean MUST be present

#### Scenario: Default profiles keep the real adapter as sole candidate

- GIVEN the application starts without profile `e2e-bunny-net` or `demo`
- WHEN Spring resolves `BunnyNetSignatureService`
- THEN it MUST resolve to `StringFormatBunnyNetSignatureService`
- AND no second candidate bean MUST exist

#### Scenario: Local profile fails closed in production-like environments

- GIVEN `e2e-bunny-net` or `demo` is active together with `prod`, `production`, or
  `staging`
- WHEN the application context starts
- THEN startup MUST fail before accepting any request
