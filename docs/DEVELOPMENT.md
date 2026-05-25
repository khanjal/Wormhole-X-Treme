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

The resulting shaded JAR will be in `target/` (e.g. `target/WormholeXTreme-1.0.0.jar`).

## Tests
- Unit tests use JUnit 5 + Mockito. Run all tests with `mvn test`.

## Coding conventions
- Java 17, Allman-style braces, 4-space indentation.
- Use anonymous `Runnable` classes (no lambdas).
- Use `WormholeXTreme.getThisPlugin().prettyLog(Level, boolean, String)` for logging.

## Submitting changes
- Create feature branches from `upgrade/spigot-1.20.4` (or main branch policy).
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
