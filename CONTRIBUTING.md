# Contributing

Bioparco accepts Compose Desktop specimens and changes to its shared tools and rules.
Keep each pull request focused and small enough to review. Run `./gradlew check` before pushing.

Each new specimen must meet these requirements:

- Put the specimen in its own top-level folder.
- Add it as a Gradle submodule that `:showcase` imports.
- Include a README with concepts, references, and implementation limits.
- Add a Spectre recording test in `:recordings`.
- Link the stable preview and MP4 URLs in the root README entry and the specimen README.
  Run the CI workflow on the branch with the specimen's name to publish those files.
  See [docs/RECORDING.md](docs/RECORDING.md). `./gradlew check` requires the links.
  Use the workflow for routine uploads.

## Pull requests

- Create a branch from `main` and target `main` with the pull request.
- Limit each pull request to one specimen or one shared rule.
- Run `./gradlew check` locally. Use `./gradlew ktfmtFormat` to format Kotlin and `.gradle.kts` files.
- Write present-tense commit subjects that explain the reason for the change. Omit conventional commit prefixes.

## License

Contributions use the Apache License 2.0. By contributing, you agree to the terms in
[LICENSE](LICENSE).
