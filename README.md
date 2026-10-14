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
| 3 | Japanese OCR (ML Kit, on-device) | ✅ |
| 4 | On-page furigana + 1 tap (word) / 2 taps (sentence) | ✅ |
| 5 | Bubble translation (DeepL), cached, with previous-page context | ✅ |
| 6 | Swipe-through on bubbles, adaptive capture rate, furigana size, battery saver | ✅ |
| 7 | manga-ocr + bubble detector (on-device) | postponed |
| + | Manual cards: type romaji/Japanese or scan a photo, with kanji suggestions | ✅ |

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

## Building the dictionary

The app bundles JMdict as a SQLite database. It is generated, not committed:
`:tools:dictbuilder:buildDictionary` downloads JMdict and builds
`app/src/main/assets/dictionary/jmdict.db`. It runs automatically before the first app build.

## Third-party data and libraries

- **JMdict** — Electronic Dictionary Research and Development Group, used under
  [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). https://www.edrdg.org/jmdict/j_jmdict.html
- **JLPT word lists** — [open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks) (MIT),
  based on Jonathan Waller's lists at [tanos.co.uk](http://www.tanos.co.uk/jlpt/) (CC BY).
- **Kuromoji** (Apache 2.0) with the **IPADIC** dictionary — Japanese tokenizer.
- **Google ML Kit** text recognition (Japanese) — on-device OCR.

## License

[GPL-3.0](LICENSE): anyone may use, study and modify it, but distributed
versions must stay open source under the same license.
