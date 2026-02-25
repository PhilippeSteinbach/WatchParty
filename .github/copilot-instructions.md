# WatchParty – GitHub Copilot Instructions

Central project rules for the **WatchParty** application.  
For language-specific details see:
- [`server/.github/java.instructions.md`](../server/.github/java.instructions.md) – Java / Spring Boot backend
- [`client/.github/angular.instructions.md`](../client/.github/angular.instructions.md) – Angular frontend

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular (latest, standalone components), TypeScript, SCSS |
| Backend | Java 21+, Spring Boot 3 |
| Real-time | Spring WebSocket (STOMP over SockJS) |
| Testing (frontend) | Jasmine, Karma; Cypress / Playwright for E2E |
| Testing (backend) | JUnit 5; Mockito for mocks |
| API | REST + WebSocket; SpringDoc OpenAPI / Swagger docs |
| Containerisation | Docker (multi-stage Maven build) |

---

## Project Rules

1. **Follow existing conventions first.** When in doubt, match the style of the surrounding code.
2. **Minimal diffs.** Change only what is necessary; avoid unrelated refactors in the same PR.
3. **No secrets in source.** Use environment variables, user-secrets, or a secrets manager.
4. **Keep code DRY.** Extract shared logic into utilities, services, or shared modules.
5. **Comments explain *why*, not *what*.** Write self-documenting code; comment on intent and trade-offs.
6. **No unused code.** Remove unused methods, parameters, imports, and variables.
7. **Accessibility (a11y).** Use semantic HTML and ARIA attributes in Angular templates (WCAG 2.1).
8. **Localisation.** Move user-visible strings to resource / i18n files; keep error text localizable.
9. **Do not edit auto-generated files** (Angular generated scaffolding, generated sources).

---

## Naming Conventions

### General
- Names must be consistent within their scope—pick one style and stick to it.
- Use English for all identifiers, comments, and documentation.

### Java (backend)
| Element | Convention | Example |
|---------|-----------|---------|
| Classes | UpperCamelCase | `WatchRoomService` |
| Methods, fields, local variables | camelCase | `roomId` |
| Constants | UPPER_SNAKE_CASE | `MAX_PARTICIPANTS` |
| Packages | lowercase | `com.watchparty.service` |

### TypeScript / Angular (frontend)
| Element | Convention | Example |
|---------|-----------|---------|
| Components, services, pipes | kebab-case file names; PascalCase class | `watch-room.component.ts` → `WatchRoomComponent` |
| Variables, functions | camelCase | `currentRoom` |
| Constants (module-level) | SCREAMING_SNAKE_CASE | `MAX_PARTICIPANTS` |
| Interfaces / types | PascalCase | `WatchRoomState` |

---

## Testing

### Mandatory coverage
- Every new **public API** (method, endpoint, component input/output) must have at least one test.
- Bug fixes must include a **regression test** that would have caught the bug.

### Backend (Java)
- Test class naming: `RoomService` → `RoomServiceTest`
- Name tests by behaviour: `whenHostLeaves_thenRoomIsClosed()`
- Follow Arrange-Act-Assert (AAA); do not add `// Arrange / Act / Assert` comments
- One behaviour per test; no branching inside tests
- Mock only external dependencies; never mock code that belongs to the solution under test
- See [`server/.github/java.instructions.md`](../server/.github/java.instructions.md) for full testing guidance

### Frontend (Angular)
- Use `TestBed` with mocked dependencies for component tests
- Test signal-based state updates with Angular testing utilities
- Mock HTTP calls with `provideHttpClientTesting`
- Write E2E tests (Cypress or Playwright) for critical user journeys
- See [`client/.github/angular.instructions.md`](../client/.github/angular.instructions.md) for full testing guidance

---

## Definition of Done (DoD)

A feature or fix is **Done** when all of the following are true:

- [ ] Code compiles without warnings
- [ ] Existing tests pass (`./mvnw test` / `ng test`)
- [ ] New tests added for all new/changed public APIs (unit + integration where applicable)
- [ ] Code reviewed and approved by at least one other contributor
- [ ] No new linting errors (`ng lint` clean)
- [ ] API changes documented (OpenAPI / Swagger updated)
- [ ] No secrets, hardcoded config, or TODO comments left in the code
- [ ] Feature works end-to-end in a local development environment
- [ ] Accessibility requirements met for any UI changes (WCAG 2.1)
- [ ] PR description explains *what* changed and *why*

---

## Error Handling

- Use precise exception types (`IllegalArgumentException`, `IllegalStateException`, …); never throw or catch the base `Exception` type.
- Do not swallow exceptions silently—log and rethrow, or let them bubble.
- Return consistent HTTP problem-details responses (RFC 9457) from API endpoints.
- In Angular, handle errors with RxJS `catchError` and display user-friendly feedback.

---

## Performance

- Backend: use async processing where appropriate; stream large payloads.
- Frontend: use `OnPush` change detection and Angular Signals for fine-grained reactivity; use `trackBy` in `*ngFor`.
- Optimise only when measured—simple code first.

---

## Security

- Sanitise all user input (Angular's built-in sanitisation on the frontend; Jakarta Bean Validation on the backend).
- Implement authentication via JWT Bearer tokens; protect routes with guards (Angular) and Spring Security (backend).
- Apply the least-privilege principle to all access modifiers and API authorisation.
