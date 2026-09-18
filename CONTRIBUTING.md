# Contributing

Welcome in. bioparco is a small public zoo of Compose Desktop specimens. The bar for a
PR is "a cute or useful enclosure, a reviewable diff, and `./gradlew check` is green."

New specimens should:

- live in their own top-level folder
- be a Gradle submodule imported by `:showcase`
- ship a README with concepts, references, and the tricky bits
- grow a Spectre recording test next to the others in `:recordings`

## Pull requests

- Branch off `main`; PR back into `main`.
- One specimen or one house-rule change per PR.
- `./gradlew check` passes locally. `./gradlew ktfmtFormat` keeps Kotlin / `.gradle.kts`
  files formatted.
- Commit messages: present-tense, explain the *why*.

## Licence

By contributing you agree your contribution is licensed under [Apache-2.0](LICENSE).
