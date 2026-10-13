# Manga Reader JP — project rules

- Android app in Kotlin + Jetpack Compose. Package `io.github.otoniel2004.mangareaderjp`.
- Plan and decisions: `docs/PLAN.md`. Follow the phase order; each phase ends testable on the phone (Samsung S24 FE), but the app must work on any Android 8.0+ (minSdk 26).
- **Everything in the repository is in English**: UI text, code, comments, tests, docs, commit messages. Translations produced by the app go to English too.
- Chat with the user in Portuguese, didactically: define technical terms and show step-by-step flows.

## Secrets — non-negotiable
- Never commit keys, tokens, passwords, keystores, `.env`, `local.properties`.
- The DeepL key is typed by the user into the app and stored encrypted on the device; never in `BuildConfig`, `gradle.properties`, resources or any repo file.
- The `.githooks/pre-commit` hook must be active (`git config core.hooksPath .githooks`). Never use `--no-verify`.
- Review `git diff --cached --stat` before every commit.

## External reference
- FrankYomik (akitaonrails) is AGPL-3.0: ideas only, never copy code.

## Building on Windows
- JDK: Android Studio's (`C:\Program Files\Android\Android Studio\jbr`) → set `JAVA_HOME` before `gradlew.bat`.
- SDK: `%LOCALAPPDATA%\Android\Sdk` (configured in `local.properties`, which is not versioned).
- Commands: `gradlew.bat testDebugUnitTest assembleDebug installDebug`.
