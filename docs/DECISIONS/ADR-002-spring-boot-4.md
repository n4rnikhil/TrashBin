# ADR-002: Move the backend to Spring Boot 4

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-11-16 |
| **Deciders** | Product owner (decision), engineering (proposal) |
| **Resolves** | ADR-001 §6 open item 1 / decision D5 ("Spring Boot 3.x") |

## Context

The backend was pinned to Spring Boot 3.5.16 in M0. The M6 dependency-check found critical and high
advisories in Spring Framework 6.2.19 and Spring Security 6.5.11. 3.5.16 is the last 3.5 release on
Maven Central and 6.2.19 the last open-source 6.2 release, so those fixes are only available in Spring
Framework 7 / Spring Security 7 (Spring Boot 4) or through commercial support. Other libraries could be
patched in place with version overrides; Spring itself could not.

## Decision

Upgrade to **Spring Boot 4.1** (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3,
springdoc-openapi 3). The brief's "Spring Boot 3.x" becomes "Spring Boot 4.x"; Java stays at 21.

Migration choices:
- Boot 4's modular starters (`spring-boot-starter-webmvc`, `-security-oauth2-resource-server`,
  `-flyway`) and test starters (`-webmvc-test`, `-security-test`); no "classic" compatibility starters.
- Jackson 3 (`tools.jackson.*`) everywhere; no Jackson 2 bridge. Annotations keep their
  `com.fasterxml.jackson.annotation` package.
- JSpecify `@Nullable` instead of Spring's deprecated `org.springframework.lang` annotations.
- The temporary version overrides added in M6 are removed; the Boot 4.1 BOM supplies patched versions.

## Consequences

- Security fixes keep flowing on a supported line. The M6 dependency-check is re-run after the move
  (results in [VERIFICATION](../VERIFICATION.md)).
- No API change: the OpenAPI snapshot test guards the contract and must pass unchanged.
- Docs that said "Spring Boot 3.x" are updated in the same change.
