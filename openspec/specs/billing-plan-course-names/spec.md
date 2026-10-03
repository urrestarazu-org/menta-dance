# Billing Plan Course Names Specification

## Purpose

Resolution of the `courses[].name` field returned by the public billing plan endpoints, `GET /api/v1/billing/plans` (list) and `GET /api/v1/billing/plans/{id}` (get). Until #108 every name was `null` because the course catalog port had no real implementation. This capability fixes which courses resolve to a title, how unresolved courses degrade, how the Virtual and Physical modules are consulted (precedence, batching, failure isolation) and that batch visibility matches the existing single-id lookups (decisions D1-D4, V0: no backward-compatibility obligations). Cache and rate limit (#312), the nullable contract itself and the BFF are out of scope and NOT requirements of this capability.

Terms: a course is **resolvable** when it is a PUBLISHED virtual course or an ACTIVE physical course; every other case (unknown id, virtual DRAFT or ARCHIVED, physical inactive, id that is not a valid UUID) is **unresolvable**. The **name** of a resolvable course is its title.

## Requirements

### Requirement: Resolvable courses show their title

Each plan returned by either plan endpoint MUST list every included course as `{id, name}`. For a resolvable course, `name` MUST equal the course title and `id` MUST equal the course id stored in the plan. `id` MUST always be present.

#### Scenario: Published virtual course resolves

- GIVEN a plan including a PUBLISHED virtual course `V` titled "Salsa Online"
- WHEN a client requests the plan list or `GET /plans/{id}`
- THEN the course is returned as `{id: V, name: "Salsa Online"}`

#### Scenario: Active physical course resolves

- GIVEN a plan including an ACTIVE physical course `P` titled "Bachata Nivel 1"
- WHEN a client requests the plan
- THEN the course is returned as `{id: P, name: "Bachata Nivel 1"}`

#### Scenario: Mixed plan resolves both modalities

- GIVEN a plan including one resolvable virtual and one resolvable physical course
- WHEN a client requests the plan
- THEN both courses carry their own title and the plan's course order is unchanged

### Requirement: Unresolvable courses degrade to a null name

An unresolvable course MUST be returned with `name` = `null` and its stored `id`. The endpoint MUST still answer HTTP 200 with the full plan. An unresolvable course MUST NOT be removed from `courses`, MUST NOT alter the `id` or position of any course, and MUST NOT affect the other courses' names or any other plan field. A non-UUID id MUST be treated as unresolvable and MUST NOT prevent other ids in the same request from resolving.

#### Scenario: Nonexistent course

- GIVEN a plan including course id `X` that exists in neither module
- WHEN a client requests the plan
- THEN the response is 200 and the course is `{id: X, name: null}`

#### Scenario: Not publicly visible

- GIVEN plans including a virtual course in DRAFT, one in ARCHIVED and a physical course that is inactive
- WHEN a client requests the plans
- THEN each is returned with its id and `name` = `null`, and the course count is unchanged

#### Scenario: Malformed id among valid ones

- GIVEN a plan including a course whose stored id is not a UUID and a resolvable course `V`
- WHEN a client requests the plan
- THEN the response is 200, the malformed course has `name` = `null`, and `V` has its title

#### Scenario: Plan without courses

- GIVEN a plan with no courses
- WHEN a client requests it
- THEN the response is 200 with `courses` = `[]`

### Requirement: Virtual takes precedence over physical

An id MUST be resolved against the virtual catalog first. The physical catalog MUST be consulted only for ids the virtual lookup did not answer. If both modules could answer the same id, the virtual title MUST be returned.

#### Scenario: Id answerable by both

- GIVEN id `C` is a PUBLISHED virtual course "Virtual C" and also an ACTIVE physical course "Fisico C"
- WHEN a client requests a plan including `C`
- THEN `name` = "Virtual C"

#### Scenario: Physical lookup skipped when virtual answers everything

- GIVEN every course id of the request resolves as a published virtual course
- WHEN the plans are requested
- THEN the physical module is not consulted

### Requirement: Batched, constant-cost resolution per request

One plans request MUST resolve the distinct course ids of ALL returned plans together, each id looked up once regardless of how many plans reference it. The system MUST perform at most one batch lookup per module per request, so the number of lookups MUST NOT grow with the number of plans or courses. An empty set of ids MUST perform no lookup. Names MUST be returned per plan in that plan's own course order.

#### Scenario: Shared course across plans

- GIVEN three plans that all include course `V`
- WHEN the plan list is requested
- THEN `V` is looked up once, and all three plans show its title

#### Scenario: Lookup count independent of size

- GIVEN a request whose plans reference 2 courses, and another whose plans reference 20 distinct courses across 10 plans
- WHEN each is served
- THEN each performs at most one virtual and one physical batch lookup

#### Scenario: Nothing to resolve

- GIVEN no active plans, or plans with no courses
- WHEN the plan list is requested
- THEN neither module is consulted and the response is 200

### Requirement: A failing module degrades only its own courses

If one module's lookup fails (infrastructure error, not a malformed id), the ids that module would have answered MUST degrade to `name` = `null`, the other module's resolved names MUST still appear, and the endpoint MUST answer 200. Each failure MUST be logged at WARN level identifying the module and the affected course ids, and MUST NOT log any other data. If both modules fail, all names MUST be `null` and the endpoint MUST still answer 200. A malformed id MUST NOT be treated as a module failure and MUST NOT emit that warning.

#### Scenario: Virtual lookup fails

- GIVEN a plan with virtual course `V` and active physical course `P`, and the virtual lookup throws
- WHEN the plan is requested
- THEN the response is 200, `V.name` = `null`, `P` has its title, and one WARN names the virtual module and the affected ids

#### Scenario: Physical lookup fails

- GIVEN virtual course `V` resolves and the physical lookup throws for physical course `P`
- WHEN the plan is requested
- THEN the response is 200, `V` has its title, `P.name` = `null`, and one WARN names the physical module and the affected ids

#### Scenario: Both modules fail

- GIVEN both lookups throw
- WHEN the plan list is requested
- THEN the response is 200 with every course `name` = `null` and its stored `id`

### Requirement: Batch lookups match single-id visibility

The virtual batch lookup MUST return exactly the courses the single-id virtual lookup returns (PUBLISHED only), and the physical batch lookup exactly those the single-id physical lookup returns (ACTIVE only), each with the same title. Unknown ids and non-visible courses MUST be omitted without distinguishing them.

#### Scenario: Virtual parity

- GIVEN virtual courses in PUBLISHED, DRAFT and ARCHIVED, plus an unknown id
- WHEN all four ids are looked up as a batch and individually
- THEN both approaches return only the PUBLISHED course, with the same title

#### Scenario: Physical parity

- GIVEN an ACTIVE and an inactive physical course, plus an unknown id
- WHEN all three ids are looked up as a batch and individually
- THEN both approaches return only the ACTIVE course, with the same title

### Requirement: Response contract is unchanged

The plan endpoints' response shape MUST NOT change: `courses[]` items MUST keep `id` and `name`, and `name` MUST remain nullable. Fields other than `courses[].name` MUST be unaffected.

#### Scenario: Shape preserved

- GIVEN any plan, resolvable or not
- WHEN served by either endpoint
- THEN each course item has exactly `id` and `name`, and `name` is a string or `null`

## Design Notes (non-normative)

The proposal fixes the batch shapes `Map<String, String> courseNames(Collection<String>)` (only resolved ids present), `VirtualCourseCatalogPort.findPublishedByIds` and `PhysicalCourseAvailabilityPort.findActiveByIds`. Left to sdd-design: where malformed-id filtering lives, the WARN log format, and how Virtual's batch keeps a constant query count.
