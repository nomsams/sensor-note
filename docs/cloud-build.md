# Free and Low-Cost Cloud APK Builds

## Primary: GitHub Actions

`.github/workflows/android-build.yml` builds `assembleDebug` on GitHub's free
Ubuntu runner. It uses JDK 17, validates the Gradle wrapper, produces the debug
APK, verifies its signature with `apksigner`, and creates SHA-256/SHA-512
checksums. Public repositories receive free Linux minutes; private accounts have
a recurring monthly included allowance. The artifact is retained for 30 days.

To use it, push this branch to your own GitHub repository, open **Actions**,
select **Android debug APK**, download the artifact, then verify it locally:

```powershell
Expand-Archive haven-debug-apk-*.zip
Get-FileHash .\*.apk -Algorithm SHA256 | Format-List
Get-Content .\SHA256SUMS.txt
```

On Linux/macOS, run `sha256sum -c SHA256SUMS.txt`. For transfer to a phone,
prefer USB or a direct local transfer; compare the on-device SHA-256 using a
file-manager hash feature or Termux (`sha256sum /path/to/app.apk`). Install only
after both hashes match.

## Other Free Or Minimal-Cost Options

### Google Cloud Shell (free)

Cloud Shell includes a weekly e2-small-style allocation and persistent home disk.
Install JDK 17 plus Android command-line tools, clone/upload the source, and run
the same Gradle command. It is excellent for occasional builds, but sessions are
ephemeral and Android SDK setup is manual. Use checksums before transferring.

### GitLab CI (free tier)

A shared Linux runner can install the Android SDK and run Gradle. Free compute
minutes are lower than GitHub's public-repository allowance, so reserve it as a
backup.

### GitHub Codespaces (included monthly allowance)

Useful for interactive debugging when the build fails. Configure JDK and the
Android SDK in a dev container, then upload the APK artifact manually. Not ideal
for routine builds because core-hour storage quotas are consumed faster.

### Oracle Cloud Always Free ARM VM

The always-free Ampere A1 allocation is generous and can build quickly after a
manual SDK/JDK installation, but account signup requires identity/card checks and
capacity varies by region. Treat it as a self-hosted fallback rather than the
primary path.

### Bitrise, CircleCI, Codemagic

All offer Android-capable trial/free tiers. Their queues, credits, and platform
restrictions change frequently, making them less predictable for a minimal
budget.

## Unconventional Fallbacks

- **Google Colab / Kaggle:** technically capable of downloading JDK/SDK and
  running Gradle, but ephemeral, unsuitable for secrets, slow for repeated work,
  and subject to notebook restrictions. Use only for emergency experiments.
- **Termux on-device build:** avoids cloud entirely. Install OpenJDK 17 and
  Gradle in Termux, build from local source, and sign locally. RAM/storage limits
  make this slower and fragile for this NDK-dependent project, but it is a useful
  zero-budget recovery path.
- **F-Droid nightly:** upstream already had an experimental path, but it is tied
  to repository infrastructure and signing policy. Better suited to release
  distribution than rapid private testing.

## Recommendation

Use GitHub Actions as the repeatable builder, Google Cloud Shell for interactive
emergency fixes, and keep a locally generated checksum manifest for every APK you
install.
