# Repository Guidelines

## Scope
This repository contains the AncientRPG Fabric client (`client-mod/`) and pure Java
public contracts/assets (`protocol/`). First-party material is source-available,
All Rights Reserved. Keep authoritative gameplay and private encounter logic out
of public source, assets, tool memories, and artifacts.

## Serena Workflow
Read [Serena setup and usage](docs/serena.md) before Java exploration or refactoring.
Activate this checkout by absolute path, or use `AncientRPG-Client` after registration.
Use `get_symbols_overview`, then targeted `find_symbol` reads. Inspect references
before changing public contracts; prefer Serena's reference-aware rename/delete
tools. Use targeted file operations for Markdown, JSON assets, and Gradle Kotlin DSL.

Confirm Java symbols are actually returned; a language server reporting "ready"
does not prove a successful Gradle import. Report indexing failures explicitly and
use targeted reads while fixing setup. Never load another repository's private
memories into this public project. Local runtimes, caches, and generated memories
are ignored; keep the shared `.serena/project.yml` portable.

## Implementation and Validation
Use Java 25 and the pinned Gradle wrapper. `protocol` must remain independent of
Fabric/Minestom implementation classes. Preserve exact compatibility identifiers
and server-owned action outcomes. Major features require reviewed OpenSpec changes;
complete planning artifacts do not authorize implementation.

Run `./gradlew build spotlessCheck :client-mod:prepareDemoMods` (`gradlew.bat` on
Windows). Use formatter-defined Java style, `UpperCamelCase` types, `lowerCamelCase`
members, and `*Test` JUnit classes. Document real-client checks separately from unit
tests. Use Conventional Commits and report checks not run. Merge only after the
applicable reviewed checklist passes. No private checkout is required to build.
