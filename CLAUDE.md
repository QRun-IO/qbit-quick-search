# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a QQQ Extension QBit template - a Maven-based Java project for creating infrastructure extensions to the QQQ low-code framework. Extension QBits provide customizers, action handlers, and other framework-level enhancements.

## Build Commands

```bash
mvn compile          # Compile
mvn test             # Run all tests
mvn test -Dtest=ClassName  # Run single test class
mvn package          # Build JAR
```

## Architecture

**QBit Producer Pattern**: The entry point is `*QBitProducer` which implements `QBitProducer` interface:
- `produce(QInstance, namespace)` - Registers the QBit and applies customizations
- Uses fluent `withConfig()` to accept configuration

**QBit Config Pattern**: Configuration via `*QBitConfig` implementing `QBitConfig`:
- `validate(QInstance, errors)` - Validates dependencies exist before production
- Fluent setters (`withX()`) for all properties

**Customizers**: Classes in `customizers/` package modify existing QQQ tables/processes.

## Code Style

- 3-space indentation
- Opening braces on next line
- Javadoc flower box comments (80-char width with `**` borders)
- Fluent-style setters (`withX()`) over traditional setters
- Use wrapper types (`Integer`, `Boolean`) not primitives

## Dependencies

- Java 21
- QQQ Backend Core (see `qqq.version` in pom.xml)
- JUnit 5 + AssertJ for testing
