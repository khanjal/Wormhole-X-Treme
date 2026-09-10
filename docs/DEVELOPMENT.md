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

The resulting shaded JAR will be in `target/`, named from the version in `pom.xml` (e.g. `target/WormholeXTreme-<version>.jar`).

## Tests
- Unit tests use JUnit 5 + Mockito. Run all tests with `mvn test`.

## Static analysis
- SpotBugs runs in CI and can be run locally with `mvn -DskipTests=true spotbugs:check`. It fails
  the build on what it finds.
- PMD runs only when asked, and never fails a build:

```bash
mvn pmd:pmd -Dformat=csv
```

  Findings land in `target/pmd.csv`. It exists to answer, in about ten seconds, part of what
  SonarCloud would say minutes later on the PR. The rules it runs are the subset measured to
  agree with SonarCloud on this codebase, and `pmd-ruleset.xml` records that measurement, what
  it covers, and what it deliberately leaves out. A clean run is meaningful; an individual
  finding is a lead worth confirming, not a verdict.
- SonarCloud remains the authority. Its quality gate wants 80% coverage on new code, so a sweep
  or a rename trips it by construction -- read the new-issue count rather than the tick.

## Coding conventions
- Java 17, Allman-style braces, 4-space indentation.
- Use anonymous `Runnable` classes for scheduled tasks, not lambdas -- they reschedule themselves and mutate retry state through the array-holder idiom. Lambdas and method references are used freely elsewhere (tab completion, `computeIfAbsent` suppliers, `FilenameFilter`). See `.github/copilot-instructions.md` for the full convention.
- Use `WormholeXTreme.getThisPlugin().prettyLog(Level, String)` for logging. The three-argument overload adds the plugin version to the tag and is for startup and shutdown lines only; never pass it `false`.

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
