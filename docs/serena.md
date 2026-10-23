# Serena for the AncientRPG Client

The checked-in `.serena/project.yml` defines **AncientRPG-Client**, a Java-only
project covering `client-mod/` and `protocol/`. It indexes this repository only.
No private source checkout or private tool memory is needed for client work.

## Setup
Install/connect Serena using your assistant's MCP integration. On first use,
activate this checkout by absolute path; later use `AncientRPG-Client`. Read Serena's
`initial_instructions` once per session. The project requires Java 25 and its own
Gradle 9.6.0 wrapper for indexing as well as builds.

Serena 1.7.0 ignores project-specific language-server settings unless the project
path is trusted in the user's Serena configuration. Add only this checkout's exact
absolute path to `trusted_project_path_patterns` in `~/.serena/serena_config.yml`.
Restart the MCP connection after trust/config changes; switching projects can retain
old in-memory settings. Shared configuration enables Gradle wrappers and the system
Java environment. Supply machine-specific runtime paths in ignored
`.serena/project.local.yml`:

```yaml
ls_specific_settings:
  java:
    gradle_wrapper_enabled: true
    use_system_java_home: true
    gradle_java_home: /absolute/path/to/jdk-25
    runtimes:
      - name: JavaSE-25
        path: /absolute/path/to/jdk-25
        default: true
```

Use forward-slash paths or single-quoted Windows paths. Keep local overrides,
caches, logs, and memories untracked. Never add a private source directory to this
project's workspace folders.

## Use and Verify

```text
activate_project(project="/absolute/path/to/client")
get_symbols_overview(relative_path="client-mod/src/main/java/dev/ancientrpg/client/AncientRpgClient.java", depth=1)
find_symbol(name_path_pattern="WireCodec", relative_path="protocol/src/main/java/dev/ancientrpg/protocol/WireCodec.java")
find_referencing_symbols(name_path="WireCodec/encodeInteraction", relative_path="protocol/src/main/java/dev/ancientrpg/protocol/WireCodec.java")
```

Expect named types/methods and genuine references, not an empty result for a known
Java class. Use Serena for semantic exploration/refactoring, then run the repository's
build/tests/formatter. Use ordinary targeted edits for documentation and assets.

If symbols are empty, check Serena's user logs for Gradle import errors, confirm
trust and Java 25 runtime configuration, and restart the MCP connection. A "ready"
status can coexist with an unsuccessful import. Do not downgrade dependencies or
change runtime gameplay code to accommodate an incorrectly configured indexer.

## Gradle 9 Import Workaround

Keep `org.gradle.parallel=false` in `gradle.properties`: the bundled JDTLS
annotation-processor discovery can fail under Gradle 9 parallel resolution with
"without an exclusive lock". See the [upstream issue](https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3807).
This affects build scheduling only; explicit CLI `--parallel` remains available
for builds outside the indexer. After changing this setting, restart Serena and
verify symbols again. Generated Eclipse `bin/` outputs are ignored.
