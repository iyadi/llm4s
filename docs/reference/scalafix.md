Scalafix: Ban Environment Variable Access

Overview
- Purpose: Prevent direct access to environment variables to encourage centralized configuration via `Llm4sConfig`.
- Enforced APIs: `sys.env` and `java.lang.System.getenv(...)`.
- Message: Violations emit guidance to use `Llm4sConfig`.

What's Included
- Plugin: `sbt-scalafix` (see `project/plugins.sbt`).
- Rule: Uses `DisableSyntax` with regex to flag:
  - `sys.env`
  - `System.getenv`
  This approach is syntactic and does not require SemanticDB.

Running Scalafix
- Lint on compile: Disabled by default (`scalafixOnCompile := false`).
- Manual run:
  - `sbt scalafixAll` to check all modules.
  - `sbt Test/scalafix` to check only test sources.

Violation Examples
- `val v = sys.env("API_KEY")` → warning: use `Llm4sConfig`
- `val v = System.getenv("API_KEY")` → warning: use `Llm4sConfig`

Suppressing a Finding (discouraged)
- Next line only: add `// scalafix:ok NoSysEnv` or `// scalafix:ok NoSystemGetenv`.
- Block scope: wrap with `// scalafix:off` and `// scalafix:on`.
Use sparingly and document why the exception is necessary.

Migration Guidance
- Replace env access with a typed configuration layer, for example:
  - Use `Llm4sConfig` to validate and load values (e.g., from Typesafe Config, system properties, or injected config), and use it at the app edge.
  - Avoid hard dependencies on the process environment in library code; prefer dependency injection.

Files Changed
- `.scalafix.conf`: Adds `DisableSyntax` regex rules with custom messages.
- `project/plugins.sbt`: Adds the `sbt-scalafix` plugin.
- `build.sbt`: Disables `scalafixOnCompile` by default; use manual runs in CI or locally.

Notes
- The rule is syntactic and works for Scala 3 without SemanticDB.
- If you see false negatives for unusual code paths, please open an issue with a minimal repro so we can extend the rule.
