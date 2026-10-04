# Tasks: BFF Physical Classes View

## 1. Ports and adapters (`bff/.../port/out`, `bff/.../adapter`)

- [x] 1.1 `PhysicalCatalogApiClient` + DTOs `PhysicalClassSummary`, `PhysicalClassDetail`
- [x] 1.2 `PhysicalPurchaseApiClient` + DTOs `ClassPricing`, `ClassQuote`, `ClassCheckout`, enums
- [x] 1.3 `PhysicalCatalogApiAdapter` (modality filter, virtual body → 404, no auth header)
- [x] 1.4 `PhysicalPurchaseApiAdapter` (pricing 404 → empty; quote 400/404/422; checkout 409/410/400)
- [x] 1.5 WireMock tests for both adapters

## 2. Use cases (`bff/.../application/usecase`) — `BffArchitectureTest`

- [x] 2.1 `GetClassesViewUseCase`, `GetClassDetailViewUseCase` with `ClassLabels`
- [x] 2.2 `QuoteClassUseCase` (sealed `ClassQuoteView`, idempotency key supplier)
- [x] 2.3 `PurchaseClassUseCase` (sealed `ClassPurchaseView`)
- [x] 2.4 Mockito tests for the four use cases

## 3. Web (`bff/.../web`, `templates/`, config)

- [x] 3.1 `ClassesController` + standalone MockMvc test
- [x] 3.2 `classes.html`, `class-detail.html`, `class-quote.html`, `class-purchase.html`
- [x] 3.3 `UseCaseConfig` beans, `menta.classes.display-zone`, `BffSecurityConfig` matchers
- [x] 3.4 Dashboard link to `/classes`
- [x] 3.5 `PhysicalClassesViewIntegrationTest` (rendering, purchase flow, route access)
