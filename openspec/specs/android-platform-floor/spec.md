# Android Platform Floor Specification

## Purpose

Platform floor and shared build configuration for the Android module (#375, ADR-0047; the floor was 26 under ADR-0046 until #668).

## Requirements

### Requirement: minSdk 29

The module MUST declare `minSdk = 29` and MUST NOT enable core library desugaring. `targetSdk` MUST remain 36. `openspec/config.yaml` MUST mention the floor.

#### Scenario: Build configuration
- GIVEN `android/build.gradle.kts`
- WHEN inspected
- THEN `minSdk` is 29 AND `targetSdk` is 36 AND no desugaring dependency or option is present

#### Scenario: Build stays green
- GIVEN the change applied
- WHEN `./gradlew build :android:assembleDebugAndroidTest` runs
- THEN it succeeds

### Requirement: Shared API base URL

The module MUST expose one `BuildConfig` field `API_BASE_URL` (debug `http://10.0.2.2:8081`, release `https://api.mentadance.com`) used by every remote data source. The name `AUTH_API_BASE_URL` MUST NOT remain.

#### Scenario: Rename complete
- GIVEN the source tree
- WHEN searched for `AUTH_API_BASE_URL`
- THEN no occurrence is found AND auth and physical data sources both use `API_BASE_URL`
