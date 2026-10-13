# Manga Reader JP

An Android app for learning Japanese by reading manga. It reads the screen while
you use **any** manga or book reader, puts furigana over kanji and katakana,
translates a word with 1 tap and a whole speech bubble with 2 taps (into
English, with romaji), and saves vocabulary and sentences so you can study them
with spaced repetition, Anki-style.

> Work in progress. The full plan is in [docs/PLAN.md](docs/PLAN.md).

## Status

| Phase | What | State |
|---|---|---|
| 1 | Home, saved cards, furigana, romaji, study (SM-2) | ✅ |
| 2 | Background screen capture (page change detection) | ✅ |
| 3 | Japanese OCR | ⏳ |
| 4 | On-page furigana + dictionary + 1 tap | — |
| 5 | Bubble translation (DeepL) + 2 taps | — |

## Building

Requirements: Android Studio (ships the JDK and SDK).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # installs on a USB-connected phone
```

## Secrets

No API key lives in the code. The DeepL key is typed into the app itself and
stored only on the phone. The repository ships a pre-commit hook that blocks
commits containing files or text that look like credentials. Enable it after
cloning:

```bash
git config core.hooksPath .githooks
```

## License

[GPL-3.0](LICENSE): anyone may use, study and modify it, but distributed
versions must stay open source under the same license.
