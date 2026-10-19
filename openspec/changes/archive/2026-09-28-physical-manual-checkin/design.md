# Design: Physical manual check-in by receptionist (#45, US-PHYSICAL-008)

## Technical Approach

One new role constant, one reshaped wire DTO, one reshaped command, one new application DTO,
three new domain exceptions, and **one new method** on the existing check-in use case. No new
table, no new endpoint, no new port, no `SecurityConfig` edit.

The organising principle is that the eleven-step QR method is **not edited at all** — not one
statement, not one line, not its signature. MANUAL arrives as a sibling in-port operation
(`checkInManually`), dispatched at the web adapter where the discriminated union is already
resolved (C4). `git diff` over `ProcessPhysicalCheckInUseCaseImpl.checkIn` is empty; that is the
review anchor for the proposal's highest-likelihood risk, and it is stronger than any convention
about "separated paths" because there is nothing to separate.

The two paths share exactly four things, all pre-existing: `AttendanceRepository`,
`PhysicalCapacityAssignmentRepository`, the private `acquireLockOrThrow` helper with its
`lockTtl` (C6), and `PhysicalCheckInExceptionHandler`. Everything else is parallel, not reused
conditionally. Domain stays framework-free (ADR-0021; `domain_should_not_use_spring_annotations`,
`domain_should_not_use_jpa_annotations`, `layered_architecture_should_be_respected` in
`api/physical/src/test/java/com/menta/physical/ArchitectureTest.java`).

## Architecture Decisions

### C1 — `CheckInRequest`: the union closes with `@AssertTrue` predicates, not a custom validator

Today every component is `@NotBlank` and `type` is `@Pattern(regexp = "QR")`. Conditional
presence cannot be expressed that way: `qrCredentials` is required for one variant and forbidden
for the other.

| Option | Tradeoff | Decision |
|---|---|---|
| Bean Validation **groups** (`@Validated(QrGroup.class)`) | Spring selects groups statically per handler method; the group depends on the payload being validated, so it cannot be chosen in time. Structurally impossible here | Rejected |
| Custom class-level `@Constraint` + `ConstraintValidator` | Correct, but **zero precedent** — grep finds no `ConstraintValidator` or `@Constraint` anywhere in this repo. A new validation mechanism to review for one DTO | Rejected |
| Jackson polymorphism (`@JsonTypeInfo` + sealed interface, two records) | Type-safe, but rejecting a forbidden field needs `FAIL_ON_UNKNOWN_PROPERTIES` (a global `ObjectMapper` change affecting every module) and raises `HttpMessageNotReadableException`, which this advice does **not** map — the door reader's `400` shape would change. Same reason `CurrentSubscriptionResponse` rejected `@JsonTypeInfo` | Rejected |
| Cross-field check written by hand in the controller | Moves validation policy out of the DTO into the adapter, and lands on the `IllegalArgumentException` → `400` handler that already means "malformed path id" | Rejected |
| **`@AssertTrue` predicates on the record** | Exactly the repo's existing cross-field idiom — `CreatePhysicalPurchaseRequest.isCheckoutProPaymentMethod()` (`api/billing/.../web/dto/`). Fires inside `@Valid`, so it raises `MethodArgumentNotValidException`, which the advice **already** maps to `400 INVALID_REQUEST` with no new handler and no message leakage | **Chosen** |

```java
public record CheckInRequest(
    @NotBlank @Pattern(regexp = "QR|MANUAL") String type,
    String qrCredentials,
    String deviceId,
    String deviceToken,
    String studentId
) {

    /** #45: a QR body must carry the reader triple and must NOT carry studentId. */
    @AssertTrue(message = "Invalid QR check-in body")
    public boolean isWellFormedQrVariant() {
        if (!"QR".equals(type)) {
            return true;
        }
        return present(qrCredentials) && present(deviceId) && present(deviceToken)
            && studentId == null;
    }

    /** #45: a MANUAL body must carry studentId and must NOT carry any QR-only field. */
    @AssertTrue(message = "Invalid MANUAL check-in body")
    public boolean isWellFormedManualVariant() {
        if (!"MANUAL".equals(type)) {
            return true;
        }
        return present(studentId)
            && qrCredentials == null && deviceId == null && deviceToken == null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
```

Each predicate returns `true` for the other variant, so `@Pattern` alone owns an unknown `type`
and a single body never collects two contradictory violations. Per-field `@NotBlank` is removed
because presence is now variant-conditional — the predicates are the only presence authority, and
`isWellFormedQrVariant` restores the exact pre-change QR requirement (all three non-blank).
Forbidden fields are rejected on `!= null`, not on blankness, so `"deviceId": ""` on a MANUAL
body is also a `400`. `studentId` stays a `String` and the controller maps it with
`UUID.fromString`, whose `IllegalArgumentException` the advice already maps to
`400 INVALID_REQUEST` — the same treatment a malformed `{sessionId}` gets.

### C2 — `CheckInCommand`: `studentIdFromQr` is dead today and is repurposed, not added

`studentIdFromQr` has exactly one producer (the controller, passing `null`) and **zero readers** —
the QR method derives the student from `parsed.studentId()`. It is renamed to `studentId` and
becomes the MANUAL subject. Two static factories replace direct construction so a mixed command
is unrepresentable at the application boundary too, mirroring `AttendanceViewer`'s
factory discipline:

```java
public record CheckInCommand(
    SessionId sessionId,
    AttendanceKind type,        // the dispatch key (C4) — QR or MANUAL
    UUID studentId,             // MANUAL only; QR derives it from qrCredentials
    String qrCredentials,       // QR only
    String deviceId,            // QR only
    String deviceToken,         // QR only
    CheckInActor actor          // MANUAL only; anonymous() for QR
) {
    public static CheckInCommand qr(
        SessionId sessionId, String qrCredentials, String deviceId, String deviceToken);

    public static CheckInCommand manual(SessionId sessionId, UUID studentId, CheckInActor actor);
}
```

The QR method's four accessor calls (`sessionId()`, `qrCredentials()`, `deviceId()`,
`deviceToken()`) are unaffected by added components. Five construction sites exist repo-wide
(`PhysicalCheckInController:68`, `ProcessPhysicalCheckInUseCaseImplTest:102,158,169`,
`PhysicalCheckInControllerTest:87`); all become `CheckInCommand.qr(...)`, which is shorter than
what they replace. The three use-case-test sites are arrange lines, not assertions.

### C3 — `CheckInActor`: roles cross as plain names, because `Role` is not importable

`:api:physical`'s `build.gradle.kts` declares `:api:shared` and **no** `:api:auth`, so
`com.menta.auth.domain.model.Role` is not on its compile classpath — the module boundary here is
enforced by the Gradle graph, not by an ArchUnit rule (no rule naming `com.menta.auth` exists in
this repo; the check is stronger than one). Plain names are already the canonical cross-boundary
currency: `JwtService:89` puts `user.getRole().name()` in the `role` claim,
`TokenUserDetailsService:60` turns it into `"ROLE_" + name`, and three Physical controllers
already compare `"ROLE_ADMIN"` as a string.

| Option | Tradeoff | Decision |
|---|---|---|
| `Authentication` on the command | Spring type in `application` — ArchUnit build failure | Rejected |
| Controller computes `boolean canRecordManualCheckIn` | Makes D5 a lie and D7's asserted step 1 vacuous: the decision would live in the adapter | Rejected |
| Copy a `Role` enum into `:api:physical` | A second source of truth that drifts silently the next time `:api:auth` adds a constant | Rejected |
| Sealed `CheckInActor` hierarchy à la `AttendanceViewer` | Over-modelled: the use case does not branch three ways, it either permits or throws | Rejected |
| **`record CheckInActor(UUID userId, Set<String> roleNames)`** | One small application DTO; the `ROLE_` prefix is stripped at the adapter, so `application` never learns Spring's convention | **Chosen** |

```java
// application/dto/CheckInActor.java
public record CheckInActor(UUID userId, Set<String> roleNames) {

    private static final CheckInActor ANONYMOUS = new CheckInActor(null, Set.of());

    public CheckInActor {
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
        if (!roleNames.isEmpty() && userId == null) {
            throw new IllegalArgumentException("an authenticated actor must carry a userId");
        }
    }

    public static CheckInActor anonymous() {
        return ANONYMOUS;
    }

    public boolean hasAnyRoleOf(Set<String> allowed) {
        return roleNames.stream().anyMatch(allowed::contains);
    }
}
```

The constructor guard makes "authorized actor with no id" unrepresentable, which is what lets
step M7 dereference `actor.userId()` without a null check (C5).

**Anonymous is a real case, twice.** The endpoint is `permitAll()` and `SecurityConfig` does not
disable anonymous, so an unauthenticated HTTP call arrives as an `AnonymousAuthenticationToken`
(`getName() == "anonymousUser"`, authority `ROLE_ANONYMOUS`); `PhysicalCheckInControllerTest`
invokes the controller method directly and passes `null`. Both must yield `403 INSUFFICIENT_ROLE`,
never `400` and never an NPE:

```java
private static CheckInActor checkInActor(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
        return CheckInActor.anonymous();
    }
    Set<String> roles = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(name -> name.startsWith("ROLE_"))
        .map(name -> name.substring("ROLE_".length()))
        .collect(Collectors.toUnmodifiableSet());
    return new CheckInActor(UUID.fromString(authentication.getName()), roles);
}
```

`UUID.fromString` is never reached for `"anonymousUser"`, which is why the instance check
precedes it.

### C4 — Dispatch: a second in-port method, so the QR method is byte-unchanged

```java
public interface ProcessPhysicalCheckInUseCase {
    /** QR door flow (US-PHYSICAL-001 escenario 2) — unchanged. */
    CheckInResult checkIn(CheckInCommand command);

    /** #45, US-PHYSICAL-008: receptionist-initiated MANUAL variant. */
    CheckInResult checkInManually(CheckInCommand command);
}
```

| Option | Tradeoff | Decision |
|---|---|---|
| `if (type == MANUAL)` guard at the top of `checkIn` | Interleaves a conditional into the load-bearing method the proposal forbids touching | Rejected |
| Keep one in-port method; `checkIn` becomes a dispatcher and the eleven steps move to a private `processQrCheckIn` | Body statements survive verbatim, but the declaration changes and a ~60-line pure-move diff is harder to review than a zero-line one — against the very risk this change is most exposed to | Rejected |
| A separate `ProcessManualCheckInUseCase` class | Duplicates `acquireLockOrThrow`, `lockTtl` wiring and the repository set for a path that shares them; two beans for one endpoint | Rejected |
| **Second method on the same in-port and impl** | The QR method is untouched, full stop; both operations keep one bean and one advice; D3's "one endpoint" still holds because the endpoint is one | **Chosen** |

The controller resolves the union once, then switches on the typed discriminator:

```java
@PostMapping("/api/v1/physical/sessions/{sessionId}/check-ins")
public ResponseEntity<CheckInResponse> checkIn(
    @PathVariable String sessionId, @Valid @RequestBody CheckInRequest request,
    Authentication authentication
) {
    CheckInCommand command = command(SessionId.of(sessionId), request, authentication);
    CheckInResult result = switch (command.type()) {
        case QR -> processPhysicalCheckInUseCase.checkIn(command);
        case MANUAL -> processPhysicalCheckInUseCase.checkInManually(command);
    };
    HttpStatus status = result.newlyRecorded() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(CheckInResponse.from(result.attendance()));
}
```

The `switch` over `AttendanceKind` is exhaustive with **no `default`** (Java 21): a future third
constant becomes a compile error at exactly the place that must decide what to do with it.

### C5 — The MANUAL path: D7's order, verbatim, with the session lookup placed deliberately

One new constructor parameter (`UserExistencePort`, the D8/`:api:shared` port billing already
consumes in `AssignTrialSubscriptionUseCaseImpl`), one new field, one new constant, one new
method. `checkIn` is not among them.

```java
private static final Set<String> MANUAL_CHECK_IN_ROLES = Set.of("RECEPTIONIST", "ADMIN");

@Override
public CheckInResult checkInManually(CheckInCommand command) {
    // M1: only the front desk (or an admin) may record on another person's behalf.
    if (!command.actor().hasAnyRoleOf(MANUAL_CHECK_IN_ROLES)) {
        throw new InsufficientRoleException();
    }

    // M2: an unknown subject is rejected before any Physical state is read (D7).
    if (!userExistencePort.existsById(command.studentId())) {
        throw new StudentNotFoundException();
    }

    // M3: a confirmed capacity assignment is a product precondition. Deliberately
    // BEFORE the cancellation check — the inverse of the QR flow's step 6/7 order
    // (D7): for MANUAL both are rejections, and D7 fixes which one a
    // multi-violation request receives.
    if (!assignmentRepository.existsConfirmedAssignment(command.sessionId(), command.studentId())) {
        throw new CapacityAssignmentRequiredException();
    }

    // M4: D8 — ONLY cancellation gates MANUAL. hasOccurred(now) is never consulted:
    // retroactive backfill of an elapsed, non-cancelled session is the point of
    // this variant, with no time limit.
    PhysicalSession session = sessionRepository.findById(command.sessionId())
        .orElseThrow(SessionNotFoundException::new);
    if (session.getStatus() == SessionStatus.CANCELLED) {
        throw new SessionNotActiveException();
    }

    // M5: idempotent replay short-circuits before touching Redis at all.
    Optional<Attendance> existing =
        attendanceRepository.findBySessionIdAndUserId(command.sessionId(), command.studentId());
    if (existing.isPresent()) {
        return new CheckInResult(AttendanceViewMapper.toView(existing.get()), false);
    }

    // M6: ONE lock, the same key shape and TTL the QR flow uses (D1, C6).
    acquireLockOrThrow(ATTENDANCE_LOCK_PREFIX + command.sessionId() + ":" + command.studentId());

    // M7: device_id carries the acting person's own userId (D2).
    Attendance attendance = Attendance.record(
        command.sessionId(), command.studentId(), clock.now(),
        command.actor().userId().toString(), AttendanceKind.MANUAL
    );
    return new CheckInResult(AttendanceViewMapper.toView(attendanceRepository.save(attendance)), true);
}
```

**Where the session lookup goes, and what it costs.** D7 orders the four *rejection checks*; it
says nothing about the read that feeds the fourth. Loading the session inside M4 keeps the spec's
ordered table byte-exact, but it has a consequence a reviewer must be told rather than discover:
for an unknown `{sessionId}`, `existsConfirmedAssignment` necessarily returns `false` (the
`fk_physical_attendances_session`-style FK on `physical_capacity_assignments` makes an orphan
assignment impossible), so **MANUAL answers `403 CAPACITY_ASSIGNMENT_REQUIRED` where QR answers
`404 SESSION_NOT_FOUND`**. The `orElseThrow(SessionNotFoundException::new)` is therefore a
defense-in-depth branch reachable only from a unit test with mocked ports — which is where it is
covered, rather than being deleted and leaving an `Optional.get()`.

Multi-violation precedence, asserted (not incidental):

| Violations present | Response |
|---|---|
| wrong role + everything else wrong | `403 INSUFFICIENT_ROLE` |
| unknown student + cancelled session | `404 STUDENT_NOT_FOUND` |
| no assignment + cancelled session | `403 CAPACITY_ASSIGNMENT_REQUIRED` |
| unknown session (no assignment can exist) | `403 CAPACITY_ASSIGNMENT_REQUIRED` |
| elapsed, non-cancelled session, all else valid | `201 Created` (D8) |

### C6 — The lock: one key, verified against the code, shared with QR on purpose

Read from the source, not paraphrased: `ATTENDANCE_LOCK_PREFIX = "checkin:attendance:"` and step
10 builds `ATTENDANCE_LOCK_PREFIX + command.sessionId() + ":" + parsed.studentId()`, where
`SessionId.toString()` returns the bare UUID. The key is therefore
**`checkin:attendance:<sessionUuid>:<studentUuid>`** — the proposal's paraphrase is accurate.

MANUAL reuses the same private `acquireLockOrThrow(String)`, the same `lockTtl` field
(`QrProperties.lockTtl`, default `Duration.ofSeconds(10)`), and the same
acquire-or-expire semantics with no compare-and-delete (D1, superseding the Draft US doc's Lua
nonce request). There is no MANUAL analogue of `checkin:qr:{jti}` because there is no credential
to single-redeem, so MANUAL takes **one** lock, not two.

Reusing the identical key rather than inventing `checkin:manual:…` is the substantive part: a QR
scan and a front-desk entry for the same `(session, student)` are competing attempts at the *same*
`UNIQUE (session_id, user_id)` INSERT, so they must contend, and with this key they do.
`RedisCheckInLockPort` already converts a Redis failure into `CheckInDegradedException`, so MANUAL
inherits `503 CHECK_IN_DEGRADED` and `409 ALREADY_PROCESSING` with no new code.

### C7 — Three new exceptions, not two, and their mappings

All extend `com.menta.shared.domain.exceptions.BusinessException` with a `private static final
String ERROR_CODE` and a no-arg constructor, exactly like `SessionCancelledException`. None carries
a field: an authorization failure must not echo which roles would have worked, and a 404 must not
distinguish "no such user" from "not a student".

```java
public class SessionNotActiveException extends BusinessException {
    private static final String ERROR_CODE = "SESSION_NOT_ACTIVE";
    public SessionNotActiveException() {
        super(ERROR_CODE, "This session is cancelled and no longer accepts check-ins.");
    }
}
// InsufficientRoleException  → "INSUFFICIENT_ROLE"
// StudentNotFoundException   → "STUDENT_NOT_FOUND"
```

| Exception | Code | HTTP | Handler detail |
|---|---|---|---|
| `InsufficientRoleException` | `INSUFFICIENT_ROLE` | **403** | "Manual check-in requires the RECEPTIONIST or ADMIN role." |
| `StudentNotFoundException` | `STUDENT_NOT_FOUND` | **404** | "Student not found." |
| `SessionNotActiveException` | `SESSION_NOT_ACTIVE` | **409** | "This session is cancelled and no longer accepts check-ins." |

Three `@ExceptionHandler` methods appended to `PhysicalCheckInExceptionHandler`, each a
`ProblemDetails.response(...)` call in the file's existing shape. The QR handlers —
`SESSION_CANCELLED` (403) and `OUTSIDE_CHECK_IN_WINDOW` (403) — are untouched and **not**
consolidated: per-variant divergence is deliberate (proposal, Out of Scope). `409` over `422`
follows this module's rule that a well-formed request against a forbidden resource state is a
conflict (`CourseHasActiveAssignmentsException`, `CapacityBelowAssignedException`,
`SessionAlreadyOccurredException` are all `409`; `422` appears nowhere in Physical).

`StudentNotFoundException` is a third class the proposal's Affected Areas table did not list; the
in-scope `404 STUDENT_NOT_FOUND` (Escenario 6) requires it, and grep confirms the code exists
nowhere in `api/` today (only in `docs/` and the specs).

### C8 — `Role.RECEPTIONIST`: append after `STUDENT`, blast radius verified concretely

```java
public enum Role {
    ADMIN,
    INSTRUCTOR,
    STUDENT,
    /** #45, US-PHYSICAL-008: front desk. May record MANUAL physical check-ins. */
    RECEPTIONIST
}
```

The proposal flagged this as a risk to verify rather than assume. Verified now:

| Consumer | Verdict |
|---|---|
| Exhaustive `switch` over `Role` | **None exists.** All 18 `switch` sites in `api/` are over `PaymentStatus`, `PaymentTarget`, `PurchaseType`, `PaymentMethod`, `CancellationTarget`, `AttendanceViewer`, or webhook outcomes |
| `users.role` column | `V1__baseline.sql:6` is `VARCHAR(20)`, and `UserJpaEntity` declares `length = 20`; `RECEPTIONIST` is 12 characters. **No migration** |
| `JwtService` | `.claim("role", user.getRole().name())` / `Role.valueOf(roleName)` — name-based, no enumeration |
| `RoleAuthorizationManager` | `Map<String, Set<Role>>` keyed by path prefix; an unmapped role simply matches nothing |
| `SecurityConfig` | String-literal `hasRole`/`hasAnyRole`; no `Role.values()` iteration. **Not modified by this change** |
| Public registration | `RegisterUserUseCaseImpl.publicRole` throws for anything but `STUDENT`, so `RECEPTIONIST` is **not** self-assignable through `POST /register` — a real security property, verified rather than assumed |

Appending last also keeps every `ordinal()` stable, which matters only because nothing must silently
depend on it — nothing does (`@Enumerated` is absent; the column is a plain `VARCHAR`).

### C9 — `Attendance`: a doc-comment change, and nothing else

D2 broadens `device_id` to "actor". No constructor, factory, getter, or invariant changes: it is
still required non-blank, and a 36-character UUID string fits `VARCHAR(255)`
(`V15__physical_attendances.sql:14`). The two doc lines that are now false are replaced:

```java
 *   <li>{@code deviceId} — who or what recorded this check-in: the reader id for
 *       a QR scan, or the acting RECEPTIONIST/ADMIN's own {@code userId} for a
 *       MANUAL front-desk entry (#45, D2). Caller-supplied, never validated
 *       against the device registry. Readers MUST NOT assume it identifies a
 *       device.</li>
 *   <li>{@code kind} — how it got there ({@link AttendanceKind}); both variants
 *       are wired as of #45, and {@code kind} is the only reliable way to
 *       interpret {@code deviceId}.</li>
```

The `<p>`-paragraph sentence "The MVP never reuses `deviceId` past the `recordedAt` snapshot"
stays true and is left alone. The pairing rule — *read `kind` before interpreting `deviceId`* —
is the load-bearing half for `AttendanceView`/history consumers.

### C10 — Wiring: one bean parameter; `SecurityConfig` and the migration head untouched

`PhysicalConfiguration.processPhysicalCheckInUseCase` gains a `UserExistencePort userExistencePort`
method parameter, passed through to the impl's constructor — the identical pattern
`BillingConfiguration` uses for the same port (`:api:auth`'s `UserExistenceAdapter` resolves it by
type at assembly time in `:api:app`). `:api:physical`'s `build.gradle.kts` already declares
`:api:shared` and `spring-boot-starter-security`, so **no build file changes**.

`SecurityConfig:275` stays `.requestMatchers(HttpMethod.POST,
"/api/v1/physical/sessions/*/check-ins").permitAll()` — unmodified, per D5. `RoleAuthorizationManager`
discriminates on URL prefix only and cannot see a request body, and the QR reader has no user
session, so the filter chain cannot be the gate here. The file is not in this change's diff at all,
which is also the cleanest possible answer to "did this widen any other endpoint": no.

`V23__physical_devices.sql` remains the migration head.

## Data Flow

    POST /api/v1/physical/sessions/{id}/check-ins        [PhysicalCheckInController]
      │  @Valid CheckInRequest → @Pattern + two @AssertTrue predicates  (C1)
      │  a mixed body dies here: MethodArgumentNotValidException → 400 INVALID_REQUEST
      ▼
    switch (command.type())              exhaustive over AttendanceKind, no default  (C4)
      │
      ├── QR ──────→ checkIn(command)            ← eleven steps, BYTE-UNCHANGED
      │
      └── MANUAL ──→ checkInManually(command)    ← new method  (C5)
                       M1 actor.hasAnyRoleOf({RECEPTIONIST, ADMIN})  ─┤ 403 INSUFFICIENT_ROLE
                       M2 userExistencePort.existsById(studentId)     ─┤ 404 STUDENT_NOT_FOUND
                       M3 existsConfirmedAssignment(session, student) ─┤ 403 CAPACITY_ASSIGNMENT_REQUIRED
                       M4 findById → status == CANCELLED?             ─┤ 409 SESSION_NOT_ACTIVE
                          (hasOccurred is NEVER consulted — D8)
                       M5 findBySessionIdAndUserId present?           ─→ 200, no lock, no INSERT
                       M6 acquireIfAbsent(
                            "checkin:attendance:{sessionId}:{studentId}", 10s)
                                                                      ─┤ 409 ALREADY_PROCESSING
                                                                      ─┤ 503 CHECK_IN_DEGRADED
                       M7 Attendance.record(session, student, now,
                            actor.userId().toString(), MANUAL)         ─→ 201 Created
                                                    └── device_id = the person (D2, C9)

    Authentication ──┐
      null │ AnonymousAuthenticationToken → CheckInActor.anonymous() → M1 throws  (C3)
      else │ UUID.fromString(getName()) + authorities minus the "ROLE_" prefix

## File Changes

| File | Action | Description |
|---|---|---|
| `physical/infrastructure/web/dto/CheckInRequest.java` | Modify | `+studentId`, `type` → `QR|MANUAL`, two `@AssertTrue` predicates, per-field `@NotBlank` removed (C1) |
| `physical/application/dto/CheckInCommand.java` | Modify | `+type`, `+actor`, `studentIdFromQr` → `studentId`, two static factories (C2) |
| `physical/application/dto/CheckInActor.java` | Create | `(userId, roleNames)` + `anonymous()` + `hasAnyRoleOf` (C3) |
| `physical/application/port/in/ProcessPhysicalCheckInUseCase.java` | Modify | `+checkInManually(CheckInCommand)` (C4) |
| `physical/application/usecase/ProcessPhysicalCheckInUseCaseImpl.java` | Modify | `+UserExistencePort` ctor param/field, `+MANUAL_CHECK_IN_ROLES`, `+checkInManually`. **`checkIn` byte-unchanged** (C5) |
| `physical/domain/exception/SessionNotActiveException.java` | Create | `SESSION_NOT_ACTIVE` → 409 (C7) |
| `physical/domain/exception/InsufficientRoleException.java` | Create | `INSUFFICIENT_ROLE` → 403 (C7) |
| `physical/domain/exception/StudentNotFoundException.java` | Create | `STUDENT_NOT_FOUND` → 404; not in the proposal's table, required by Escenario 6 (C7) |
| `physical/infrastructure/web/controller/PhysicalCheckInController.java` | Modify | `Authentication` param, `checkInActor`, command factory, exhaustive dispatch; class javadoc's "never touches Authentication" claim corrected (C4) |
| `physical/infrastructure/web/controller/PhysicalCheckInExceptionHandler.java` | Modify | Three new `@ExceptionHandler` methods; existing nine untouched (C7) |
| `physical/domain/model/Attendance.java` | Modify | Javadoc only — `deviceId` "actor" invariant + the read-`kind`-first rule (C9, D2) |
| `physical/infrastructure/config/PhysicalConfiguration.java` | Modify | One `UserExistencePort` bean parameter threaded through (C10) |
| `api/auth/.../domain/model/Role.java` | Modify | `+RECEPTIONIST`, appended last (C8) |
| `api/openapi/physical-v1.yaml` | Modify | `CheckInRequest` → `oneOf: [CheckInQrRequest, CheckInManualRequest]` + `discriminator.propertyName: type`; `403 INSUFFICIENT_ROLE`, `404 STUDENT_NOT_FOUND`, `409 SESSION_NOT_ACTIVE`; D8 and the unknown-session precedence in prose |
| `bruno/API - Direct/physical/*.bru` | Create | First check-in requests in the collection: QR, MANUAL happy path, MANUAL rejections |
| `docs/user-stories/US-PHYSICAL-008.md` | Modify | Draft → active; record D1 superseding its compare-and-delete request, and D8's divergence |
| `api/auth/.../security/SecurityConfig.java` | **Unchanged** | D5 — not in the diff (C10) |
| `api/app/.../db/migration/` | **Unchanged** | `role VARCHAR(20)`, `device_id VARCHAR(255)`, `kind` already exist; `V23` stays head (C8, C9) |
| `api/physical/build.gradle.kts` | **Unchanged** | `:api:shared` + security starter already declared (C10) |
| `ProcessPhysicalCheckInUseCaseImplTest` | Modify | New MANUAL classes/methods; three arrange lines → `CheckInCommand.qr(...)`. **Zero QR assertion edits** |
| `PhysicalCheckInControllerTest` | Modify | QR cases change shape: new method parameter, `CheckInCommand.qr(...)` equality, and the now-false name `check_in_never_reads_authentication_...` |

`oneOf` has precedent (`virtual-v1.yaml:567`, `billing-v1.yaml:740`); `discriminator` does not, and
is added as optional metadata because the wire genuinely is discriminated. `physical-v1.yaml`
declares no `securitySchemes` block, so the MANUAL variant's bearer requirement is documented in
prose, matching how the file already documents `permitAll` at line 40.

## Testing Strategy

| Layer | What to test | Approach |
|---|---|---|
| Unit (web DTO) | MANUAL + each of `qrCredentials`/`deviceId`/`deviceToken` (populated **and** empty-string) → invalid; MANUAL without `studentId` → invalid; QR + `studentId` → invalid; QR missing any of the triple → invalid; `type` absent/`"qr"`/`"OTHER"` → invalid; both valid variants → zero violations | `Validator` from `Validation.buildDefaultValidatorFactory()`, parameterized per forbidden field |
| Unit (application) | **D7 order**, one case per precedence row of C5's table, each asserting the exception type *and* `verifyNoInteractions` on the ports that must not have been reached (e.g. no `findById` when the assignment check fails) | Mockito `verify`/`verifyNoInteractions`/`InOrder` |
| Unit (application) | `RECEPTIONIST` and `ADMIN` both reach M7; `STUDENT`, `INSTRUCTOR`, empty-role, and `anonymous()` all throw `InsufficientRoleException`; `existsById` is never called for a rejected role | `ProcessPhysicalCheckInUseCaseImplTest`, new nested class |
| Unit (application) | **D8**: `hasOccurred` is irrelevant — an elapsed non-cancelled session yields `201`; a cancelled future session yields `409`. No `sessionWindowBefore/After` value changes either outcome | Parameterized over `scheduledAt` far past/future |
| Unit (application) | Idempotent MANUAL replay returns `newlyRecorded == false` **and** `verifyNoInteractions(lockPort)` + `verify(attendanceRepository, never()).save(any())` | Captor + `never()` |
| Unit (application) | Lock key is exactly `checkin:attendance:{sessionId}:{studentId}` with the injected TTL, and exactly **one** `acquireIfAbsent` call (no `checkin:qr:` key) | `ArgumentCaptor<String>` + `verify(times(1))` |
| Unit (application) | Persisted `Attendance` has `kind == MANUAL` and `deviceId == actor.userId().toString()` | `ArgumentCaptor<Attendance>` |
| Unit (application) | Unknown-session defense-in-depth: assignment mocked `true`, `findById` empty → `SessionNotFoundException` | Mocked ports (the only reachable route, C5) |
| Unit (application) | **Regression**: every existing QR case passes unmodified except for the `CheckInCommand.qr(...)` arrange swap | `git diff` over QR assertion bodies must be empty |
| Unit (web) | Anonymous (`null` **and** `AnonymousAuthenticationToken`) MANUAL → `checkInManually` receives `CheckInActor.anonymous()`; authorities lose the `ROLE_` prefix; `type: "MANUAL"` routes to `checkInManually` and `"QR"` to `checkIn`, never both | Direct controller invocation, as the existing test does |
| Unit (web) | Malformed `studentId` → `400 INVALID_REQUEST`; each new exception → 403/404/409 with its exact code; the nine existing mappings unchanged | `PhysicalCheckInExceptionHandlerTest` |
| Integration | RECEPTIONIST `201` → repeat `200` with unchanged row count → `device_id` equals the receptionist's `userId` and `kind = 'MANUAL'` in MySQL; ADMIN `201`; STUDENT/INSTRUCTOR/anonymous `403`; cancelled `409`; elapsed non-cancelled `201`; unknown student `404`; a QR scan for a student already MANUAL-checked-in returns `200` (shared-key/shared-row proof) | Testcontainers MySQL 8 + Redis, full filter chain, mirroring `PhysicalCheckInIntegrationTest` |
| Integration | Attendance history renders a MANUAL row correctly (D2 consumer risk) | Existing `PhysicalAttendanceHistoryIntegrationTest` extended with a MANUAL row |
| Security | `permitAll` on `/check-ins` is unchanged; no other endpoint's authorization moved; a `RECEPTIONIST` JWT is rejected by `/api/v1/admin/**` | `SecurityConfigTest` parameterized case for the new role |
| Architecture | No Spring/JPA in `physical/domain` or `physical/application`; `CheckInActor` imports only `java.util` | Existing `ArchitectureTest` (no new rule) |
| Build | `Role.RECEPTIONIST` breaks no consumer | `./gradlew build` + full `test` — the gate C8's table predicts, not replaces |
| Coverage | 95% domain+application, 90% infrastructure | `./gradlew :api:physical:test jacocoTestCoverageVerification`; `:api:auth` domain stays 100% (an enum constant adds no branch) |

**TDD order (RED → GREEN per slice):** `Role.RECEPTIONIST` → three exceptions + handler mappings →
`Attendance` javadoc → `CheckInActor` → `CheckInCommand` factories → `checkInManually` role gate →
student existence → assignment → cancellation/D8 → idempotency → lock key/TTL → INSERT actor →
`CheckInRequest` union predicates → controller dispatch and anonymous handling → integration and
security last.

## Threat Matrix

N/A — no shell command, subprocess, VCS/PR automation, executable-file classification, or
process-integration boundary. The change's two adversarial surfaces are covered structurally
instead: privilege escalation is gated in the use case with the role set as the only authority and
an anonymous actor that cannot carry a `userId` (C3, C5), and payload confusion is closed by the
union predicates rejecting every cross-variant field before a use case runs (C1). Both have
explicit per-case tests, including the deliberately non-obvious "self-registration cannot mint a
RECEPTIONIST" property (C8).

## Migration / Rollout

No migration. `users.role` is `VARCHAR(20)` and `physical_attendances.device_id` is
`VARCHAR(255)`, both verified, and `physical_attendances.kind` already accepts `MANUAL`; `V23`
remains the head. No feature flag: the MANUAL variant is unreachable until this merge lands, since
`@Pattern(regexp = "QR")` rejects it today with `400`.

No configuration property is added or repurposed — MANUAL reuses `app.physical.qr.lock-ttl`, and
`app.physical.checkin.device-token` is irrelevant to it (a MANUAL body may not carry a device
token at all).

Rollback follows the proposal's plan. The one non-trivial step is unchanged and real: reverting
`Role` while a user already holds `RECEPTIONIST` leaves an unmappable persisted value and an
unmappable JWT claim (`Role.valueOf` throws), so reassign those users first, or revert only the
Physical module and leave the constant in place — it is inert without the MANUAL path.

Three independently deliverable slices (`sdd-tasks` owns the authoritative 400-line guard):

1. **Domain + role** — `Role.RECEPTIONIST`, the three exceptions, the `Attendance` javadoc. Inert:
   nothing calls any of it yet.
2. **Application** — `CheckInActor`, `CheckInCommand` reshape, the in-port method,
   `checkInManually`, `PhysicalConfiguration` wiring, and the ordering/lock/actor unit tests.
3. **Web + contract** — `CheckInRequest` union, controller dispatch and anonymous handling, the
   three handler mappings, OpenAPI, Bruno, the US doc, and the integration/security tests.

Slice 2 carries the ordering risk and should be reviewed alone. Slice 3 is where the QR
*controller* test changes shape, so a reviewer comparing QR diffs should expect edits there and
none in `ProcessPhysicalCheckInUseCaseImplTest`'s assertions.

## Requirement → Component Map

| # | Requirement (spec) | Components | Slice |
|---|---|---|---|
| R1 | MANUAL check-in role gate — RECEPTIONIST/ADMIN only, else `403 INSUFFICIENT_ROLE` | `Role.RECEPTIONIST` (C8), `CheckInActor` (C3), M1 + `InsufficientRoleException` (C5, C7) | **1**/**2** |
| R2 | MANUAL request is a validated discriminated union | `CheckInRequest` `@AssertTrue` predicates (C1), `CheckInCommand` factories (C2) | **3**/**2** |
| R3 | Ordered MANUAL validation before insertion (D7) | M1–M4 + the precedence table (C5), `StudentNotFoundException`/`SessionNotActiveException` (C7) | **2**/**1** |
| R4 | Idempotent redemption — `200`, no lock, no insert | M5 read-through (C5), `AttendanceRepository` unchanged | **2** |
| R5 | Success persists the receptionist as actor (`kind = MANUAL`, `device_id = userId`) | M7 (C5), `Attendance` javadoc (C9) | **2**/**1** |
| R6 | `physical-checkin` delta: QR now rejects `studentId`, `type` is no longer single-valued | `isWellFormedQrVariant` (C1) | **3** |
| — | D1 lock reuse, same key and TTL, one lock | C6, `acquireLockOrThrow` unchanged | **2** |
| — | QR path's eleven steps, exceptions and assertions unchanged | C4 (zero-diff method), regression check | all |

## Open Questions

None blocking. Four items for `sdd-tasks` to carry forward as **facts, not questions**:

- **An unknown `{sessionId}` on a MANUAL body answers `403 CAPACITY_ASSIGNMENT_REQUIRED`, not
  `404 SESSION_NOT_FOUND`** (C5). This is a consequence of D7 placing the assignment check before
  the cancellation check, it diverges from the QR variant, and it must be stated in the OpenAPI
  prose and the PR body. The `404` branch remains as a mock-reachable defense-in-depth guard.
- **D7 inverts the QR flow's step 6/7 order** (assignment before cancellation, where QR checks
  cancellation first to stop a stale assignment). Deliberate and settled; the divergence is pinned
  by the precedence tests, not left to a reader's inference.
- **Three new exception classes are required, not two** (C7). `StudentNotFoundException` is absent
  from the proposal's Affected Areas table but required by the in-scope `404 STUDENT_NOT_FOUND`.
- **`PhysicalCheckInControllerTest`'s QR cases do change** (new parameter, `CheckInCommand.qr(...)`
  equality, and a test name that becomes false). The "no edits to existing QR tests" guarantee is
  scoped to `ProcessPhysicalCheckInUseCaseImplTest`'s assertions, which stay untouched.
