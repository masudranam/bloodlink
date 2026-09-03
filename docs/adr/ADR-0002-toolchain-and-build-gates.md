# ADR-0002: Toolchain and build gates

**Status:** Accepted
**Date:** 2026-09-03

## Context

The project is a portfolio piece with a small number of rules it must never
break. Gates that are added late are gates that get argued with; gates added to
an empty repo are simply how the project works.

The machine this was scaffolded on has Temurin JDK 25 installed and no Maven at
all, while the target runtime is Java 21.

## Decision

- **Java 21** is the target. `maven.compiler.release=21` so the bytecode is
  correct regardless of which JDK runs the build, and CI runs on Temurin 21 so
  the tested runtime is the real one.
- **Maven Wrapper 3.3.4, script-only distribution.** `./mvnw` pins Maven 3.9.16
  and downloads it on first use. No `maven-wrapper.jar` is committed — the repo
  stays free of binaries.
- **Checkstyle runs in the `validate` phase** with `violationSeverity=warning`,
  so any violation fails the build rather than printing a warning nobody reads.
  It checks `src/main/java` only: the traceability convention requires test
  methods named `ac3_...`, which the `MethodName` rule would reject.
- **Jacoco's coverage gate is scoped to the service layer**, as a `PACKAGE` rule
  matching `com.roktolink.*.service`, checked in `verify` with
  `haltOnFailure=true`. A rule matching no package is a no-op, so the gate is
  silent on the skeleton and starts biting the moment a service exists.
- **Testcontainers, not an embedded database.** Tests run against
  `postgres:16-alpine`, the same image Docker Compose runs locally. Haversine in
  native SQL and Flyway migrations cannot be honestly tested against H2.
- **`ddl-auto=validate`, never `update`.** Flyway owns the schema; Hibernate
  gets a veto and nothing more.

## Consequences

- A contributor needs Docker running to execute the backend test suite. That is
  a real cost and it is accepted: the alternative is tests that pass against a
  database the application will never meet.
- The first `./mvnw` on a clean machine downloads Maven and then the world.
- Coverage is enforced where the logic lives instead of being diluted to a
  repo-wide average that entities and DTOs can inflate.

## Alternatives considered

- **Compile on JDK 25 and call it Java 25:** would drop the pinned target for no
  gain; nothing in the stack needs a language feature newer than 21.
- **Google or Sun Checkstyle presets:** both fight the code's 4-space style and
  produce hundreds of advisory warnings, which trains people to ignore the tool.
- **A repo-wide 80% coverage threshold:** easy to satisfy by testing getters.
