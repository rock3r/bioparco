# Static analysis

bioparco uses:

- `detekt` 2.x plus Compose Rules (`io.nlopez.compose.rules:detekt`)
- `ktfmt` with KotlinLang style

`./gradlew check` runs both. Do not add a Detekt formatting plugin; `ktfmt` owns format.

```bash
./gradlew check
./gradlew detekt
./gradlew ktfmtCheck
./gradlew ktfmtFormat
```

`MagicNumber` is disabled because animation and drawing code uses many intentional numeric constants.
Do not add a baseline or `@Suppress` without an explicit request. Fix the cause. See the
`addressing-detekt-violations` skill.
