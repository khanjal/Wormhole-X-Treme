# Development Guide

This document describes how to set up a development environment for Wormhole X-Treme, run tests, build artifacts, and follow repository conventions.

## Prerequisites
- Java 17 (JDK)
- Maven 3.8+
- IDE (IntelliJ IDEA, VS Code, or Eclipse)

## Build
- To run tests and build the plugin jar:

```bash
mvn -DskipTests=false test
mvn -DskipTests=true package
```

The resulting shaded JAR will be in `target/`, named from the version in `pom.xml` (e.g. `target/WormholeXTreme-1.5.0.jar`).

## Tests
- Unit tests use JUnit 5 + Mockito. Run all tests with `mvn test`.

## Coding conventions
- Java 17, Allman-style braces, 4-space indentation.
- Use anonymous `Runnable` classes for scheduled tasks, not lambdas -- they reschedule themselves and mutate retry state through the array-holder idiom. Lambdas and method references are used freely elsewhere (tab completion, `computeIfAbsent` suppliers, `FilenameFilter`). See `.github/copilot-instructions.md` for the full convention.
- Use `WormholeXTreme.getThisPlugin().prettyLog(Level, boolean, String)` for logging.

## Submitting changes
- Create feature branches from `main`, and open a PR. Nothing is committed to `main` directly, including small fixes.
- Run tests locally and ensure build passes before creating PR.

## Common tasks
- Run tests:

```bash
mvn -DskipTests=false test
```

- Build package (skip tests):

```bash
mvn -DskipTests=true package
```

- Format code: follow repo style (IDE formatting constrained by project conventions).

## Notes
- See `README.md` for usage and commands.
- For long-running changes (SpotBugs, CI changes), coordinate with maintainers.
