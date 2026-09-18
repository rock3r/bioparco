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

`MagicNumber` is off on purpose: the animals *are* the numbers. Do not add a baseline
or sprinkle `@Suppress` without an explicit ask. Fix the cause. See the
`addressing-detekt-violations` skill.
