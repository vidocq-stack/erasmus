# Erasmus

**Jakarta Validation 3.1** (Bean Validation) implementation for the
[Vidocq](https://codeberg.org/Vidocq/vidocq) runtime.

> Named after **Erasmus of Rotterdam** (1466–1536), the humanist who spent his
> life critically validating texts against their sources — the metaphor for a
> library whose whole job is to validate data against its constraints.

## Status

🚧 **Bootstrap** — repository scaffold and CI only, implementation not started.

## Ecosystem rules

Erasmus follows the transverse Vidocq philosophy (see the workspace
[CLAUDE.md](https://codeberg.org/Vidocq/vidocq) conventions):

- **Zero third-party dependency** — only the Jakarta Validation 3.1 API.
- **Strict Java Modules (JPMS)** — proper `module-info.java`, minimal `exports`,
  no unjustified `opens`, no classpath.
- **Static code generation first** — APT / Class-File API (JEP 484) at build
  time instead of runtime reflection; AOT-friendly (GraalVM, Leyden).
- **TDD** — test (or TCK scenario) first, then code.
- **Virtual threads** for any I/O.
- Bugs tracked in [BUG.md](BUG.md), benchmarks in [BENCH.md](BENCH.md).

## Build

```bash
sdk env          # Java 25 (Temurin) + Maven 3.9.16, pinned by .sdkmanrc
./mvnw -ntp install -DskipTests
./mvnw test
```

## License

Triple-licensed, at your option: **EPL-2.0 OR EUPL-1.2 OR GPL-2.0-or-later**.
See [LICENSE](LICENSE) and [NOTICE](NOTICE).
