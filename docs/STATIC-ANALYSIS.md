# Static analysis

Bioparco checks Kotlin with these tools:

- `detekt` 2.x plus Compose Rules (`io.nlopez.compose.rules:detekt`)
- `ktfmt` with KotlinLang style

`./gradlew check` runs both tools. Use `ktfmt` for formatting. Do not add a Detekt formatting plugin.

```bash
./gradlew check
./gradlew detekt
./gradlew ktfmtCheck
./gradlew ktfmtFormat
```

The Detekt configuration disables `MagicNumber` because drawing and animation code needs many numeric constants.
Fix the cause of each finding. Adding a baseline or `@Suppress` requires an explicit request from the owner.
The `addressing-detekt-violations` skill describes the review steps.
