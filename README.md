# Manga Reader JP

An Android app for learning Japanese by reading manga. It reads the screen while you use **any**
manga or book reader, puts furigana over kanji and katakana, translates a word with one tap and a
whole speech bubble with two taps (into English, with romaji), and saves vocabulary and sentences
so you can study them with spaced repetition, Anki-style.

Everything except the optional DeepL translation runs on the phone: OCR, dictionary, tokenizer,
furigana and the on-device translator.

## Features

**Reader (on top of any app)**
- Detects page turns and reads the Japanese on screen (Google ML Kit OCR, on-device)
- Furigana over kanji and katakana, beside vertical text or above horizontal text; none where the
  page already prints it, and optionally none for words you already know
- **1 tap** on a word: reading, romaji, dictionary form, meanings (JMdict) and JLPT level
- **2 taps** on a bubble: the whole sentence with particles highlighted and two English
  translations — DeepL (optional, your own key) and an offline on-device one
- Save words and sentences silently, with a crop of the bubble and the sentence as context
- Swipes that start on a bubble still turn the page (optional accessibility service)
- Manga mode (a bubble is the sentence) and book mode (the sentence under your finger)

**Study**
- Cards with furigana (per-card toggle), romaji and a color for each part of speech
- Spaced repetition (SM-2) inside each card and a "Review now" session
- Statistics: streak, reviews per day, retention, known cards
- Search (Japanese, kana, romaji or English) and filters by type and source
- Add cards yourself: type romaji (kanji suggestions with conjugations) or scan a photo
- Backup: export / import all cards to a single file

## Install

Download the APK from the [Releases](https://github.com/Otoniel2004/manga-reader-jp/releases) page
and open it on your phone (Android 8.0 or newer; allow installing from your browser or file
manager when asked).

First run:
1. Turn the **Reader** switch on and allow *Display over other apps* and *screen sharing*
   (choose *entire screen*).
2. Optional: **Settings → Translation (DeepL)** — paste a free [DeepL API key](https://www.deepl.com/pro-api)
   for better sentence translations.
3. Optional: **Settings → Swipe on text turns the page** — enable the accessibility service.
4. Samsung and some other phones: **Settings → Battery: unrestricted**, so Android doesn't stop the
   reader in the background.

## Privacy and security

See [SECURITY.md](SECURITY.md). In short: screen frames never leave the phone, the DeepL key is
stored encrypted and sent only to DeepL, and only bubble text you translate with DeepL is sent out.

## Building

Requirements: Android Studio (ships the JDK and SDK).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on a USB-connected phone
```

The first build downloads JMdict and the JLPT word lists and generates the bundled dictionary
(`:tools:dictbuilder:buildDictionary`, ~30 s). It is not committed to the repository.

**Release builds** are signed with a key kept outside the repository. Add to
`~/.gradle/gradle.properties` (or set the matching `MANGA_READER_*` environment variables):

```properties
mangaReader.keystore=/path/to/release.jks
mangaReader.keystorePassword=...
mangaReader.keyAlias=...
mangaReader.keyPassword=...
```

Without them, `assembleRelease` falls back to the debug key (fine for local testing only).

The repository ships a pre-commit hook that blocks commits containing files or text that look
like credentials. Enable it after cloning:

```bash
git config core.hooksPath .githooks
```

The development plan and design notes are in [docs/PLAN.md](docs/PLAN.md).

## Third-party data and libraries

- **JMdict** — Electronic Dictionary Research and Development Group, used under
  [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). https://www.edrdg.org/jmdict/j_jmdict.html
- **JLPT word lists** — [open-anki-jlpt-decks](https://github.com/jamsinclair/open-anki-jlpt-decks) (MIT),
  based on Jonathan Waller's lists at [tanos.co.uk](http://www.tanos.co.uk/jlpt/) (CC BY).
- **Kuromoji** (Apache 2.0) with the **IPADIC** dictionary — Japanese tokenizer.
- **Google ML Kit** text recognition (Japanese) and translation — on-device OCR and translation.
- **DeepL API** — optional online translation, with your own key.

## License

[GPL-3.0](LICENSE): anyone may use, study and modify it, but distributed versions must stay open
source under the same license.
