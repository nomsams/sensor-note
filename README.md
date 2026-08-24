# Field Sensor Toolkit

A local-first Android sensor toolkit for environmental observation, diagnostics,
and field experiments. It records sensor changes and media locally, supports
configurable alerts, and includes diagnostic visualizations.

## Developer Build

Requirements:

- JDK 17
- Android SDK Platform 34 and Build Tools 34.0.0
- Gradle wrapper included in this repository

```bash
./gradlew clean assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

## Continuous Integration

GitHub Actions builds a debug APK on Ubuntu runners. The workflow validates the
Gradle wrapper, verifies APK signatures, and publishes SHA-256/SHA-512 checksums
with each artifact.

See `docs/cloud-build.md` for cloud build alternatives and transfer checks.

## Integrity

Before installing an APK, verify its SHA-256 checksum against the generated
`SHA256SUMS.txt`. Compare the hash again after transferring the file to the test
device.

## License

See [LICENSE](LICENSE).
