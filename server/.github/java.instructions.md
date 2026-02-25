---
description: 'Guidelines for building Java Spring Boot applications'
applyTo: '**/*.java'
---

# Java Spring Boot Development

## Java Instructions
- Always use Java 21+ features (records, sealed classes, pattern matching, virtual threads where appropriate).
- Write clear and concise Javadoc comments for public APIs.

## General Instructions
- Make only high confidence suggestions when reviewing code changes.
- Write code with good maintainability practices, including comments on why certain design decisions were made.
- Handle edge cases and write clear exception handling.
- For libraries or external dependencies, mention their usage and purpose in comments.
- Address code smells proactively during development rather than accumulating technical debt.
- Focus on readability, maintainability, and performance when refactoring identified issues.

## Best Practices

- **Records**: Use Java Records for DTOs and immutable value objects.
- **Pattern Matching**: Use pattern matching for `instanceof` and `switch` expressions.
- **Type Inference**: Use `var` for local variables when the type is clear from the right-hand side.
- **Immutability**: Favor immutable objects. Make classes and fields `final` where possible. Use `List.of()`/`Map.of()` for fixed data.
- **Streams and Lambdas**: Use the Streams API and lambda expressions for collection processing. Employ method references.
- **Null Handling**: Avoid returning or accepting `null`. Use `Optional<T>` for possibly-absent values and `Objects.requireNonNull()`.

## Naming Conventions

- `UpperCamelCase` for class and interface names.
- `lowerCamelCase` for method and variable names.
- `UPPER_SNAKE_CASE` for constants.
- `lowercase` for package names.
- Use nouns for classes (`RoomService`) and verbs for methods (`findRoomByCode`).
- Do not use `I` prefix for interfaces.

## Project Setup and Structure

- Use Spring Boot 3 with Maven.
- Organise code by feature/domain (entity, repository, service, controller per feature).
- Use `application.yml` for configuration with profile-specific overrides (`application-dev.yml`).
- Keep `WatchPartyApplication.java` minimal — delegate configuration to `@Configuration` classes.

## Data Access Patterns

- Use Spring Data JPA with Hibernate for data access.
- Define repository interfaces extending `JpaRepository` or `CrudRepository`.
- Use Flyway for database migrations (`db/migration/V1__*.sql`).
- Write JPQL or native queries via `@Query` when needed.
- Avoid N+1 queries — use `@EntityGraph` or `JOIN FETCH` where appropriate.

## Authentication and Authorization

- Use Spring Security with JWT Bearer tokens.
- Implement `JwtAuthenticationFilter` extending `OncePerRequestFilter`.
- Use `SecurityFilterChain` bean for configuration (no `WebSecurityConfigurerAdapter`).
- Apply method-level security with `@PreAuthorize` where appropriate.

## Validation and Error Handling

- Use Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Valid`) on DTOs.
- Implement `@RestControllerAdvice` for global exception handling.
- Return RFC 9457 Problem Details (`ProblemDetail`) for error responses.
- Use specific exception types (e.g., `RoomNotFoundException`, `UnauthorizedException`).

## API Documentation

- Use SpringDoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`) for Swagger UI.
- Annotate controllers with `@Operation`, `@ApiResponse` for documentation.
- Swagger UI available at `/swagger-ui.html`.

## Logging

- Use SLF4J with Logback for structured logging.
- Use parameterised logging (`log.info("Room {} created", roomId)`) — never string concatenation.
- Log at appropriate levels: ERROR for failures, WARN for recoverable issues, INFO for key events, DEBUG for diagnostics.

## Testing

- Use JUnit 5 with Mockito for unit tests.
- Do not emit "Arrange", "Act", or "Assert" comments.
- Name tests descriptively: `whenHostLeaves_thenRoomIsClosed()`.
- Use `@WebMvcTest` for controller tests, `@DataJpaTest` for repository tests.
- Use `@SpringBootTest` with `@Testcontainers` for integration tests.
- Mock external dependencies with `@MockBean` or Mockito.
- One behaviour per test; no branching inside tests.

## Common Bug Patterns

- Resource management — Always close resources. Use try-with-resources.
- Equality checks — Use `.equals()` or `Objects.equals(...)` rather than `==` for non-primitives.
- Redundant casts — Remove unnecessary casts; prefer correct generic typing.
- Reachable conditions — Avoid conditional expressions that are always true or false.

## Common Code Smells

- Parameter count — Keep method parameter lists short. Group into value objects if needed.
- Method size — Keep methods focused and small. Extract helper methods.
- Cognitive complexity — Reduce nesting by extracting methods or using polymorphism.
- Duplicated literals — Extract into named constants or enums.
- Dead code — Remove unused variables and assignments.
- Magic numbers — Replace with named constants.

## Build and Verification

- Use Maven: `./mvnw clean package` (or `mvnw.cmd clean package` on Windows).
- Ensure all tests pass as part of the build.
- Configure health checks via Spring Boot Actuator (`/actuator/health`).
- Use multi-stage Dockerfile: Maven build → JRE runtime image.