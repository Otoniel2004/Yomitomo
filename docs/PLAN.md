# Plan — Manga Reader JP

An Android app (Kotlin) that reads the screen while you read Japanese manga or
books in **another app**. It shows furigana over kanji/katakana, translates a
word (1 tap) or a speech bubble (2 taps) into **English**, and saves vocabulary
and sentences to a study home screen.

- Target device: **Samsung Galaxy S24 FE** (Exynos 2400e, 8 GB RAM, 1080×2340 screen, One UI / Android 14+)
- Works on any Android **8.0+** (minSdk 26), with no Samsung-only dependencies
- Distribution: personal use via APK, code on GitHub. Play Store may come later.
- The whole app (UI, code, docs) is in **English**.

---

## 1. Decisions

| # | Topic | Choice |
|---|---|---|
| 1 | Touch conflict | **B**: only detected bubbles capture touches; everything else goes to the reader app |
| 2 | Sentence translation | **DeepL API** (Free plan: 500k characters/month) |
| 3 | Furigana on the page | On by default, can be turned off in **Home → Reader options** |
| 4 | Save button | Saves silently ("Saved ✓") without leaving the manga |
| 5 | Card | Word, reading, meanings (EN), context sentence, bubble crop (optional) |
| 6 | Furigana on cards | Per-card toggle |
| 7 | Romaji | Every translation popup (1 or 2 taps) and every card also shows the romaji reading |
| 8 | Repository | Private on GitHub during development; public when finished, **GPL-3.0** |
| 9 | Study | The app is its own "Anki": spaced repetition (SM-2) inside each card + a "Review now" session |
| 10 | Language | App, code and docs in English |

---

## 2. Glossary

| Term | Meaning |
|---|---|
| **Kotlin** | Android's official language. |
| **Jetpack Compose** | Library for building screens in Kotlin code (no XML). |
| **Gradle** | Build tool: compiles the project and downloads libraries. |
| **APK** | An Android app installer file. |
| **MediaProjection** | Android API that captures the screen (needs user consent on every activation). |
| **Foreground Service** | A process that keeps running with the app closed, always with a visible notification. |
| **Overlay** | A window drawn on top of other apps ("Display over other apps" permission). |
| **OCR** | Reading text from an image. |
| **ML Kit** | Google's on-device (offline) ML library. Has Japanese OCR. |
| **ONNX Runtime** | Engine that runs ML models (trained in Python) inside an Android app. |
| **manga-ocr** | OCR model trained specifically on manga (vertical text, stylized fonts). |
| **Bubble detector** | ML model that draws boxes around the bubbles/text of a page. |
| **Kuromoji** | Java Japanese tokenizer: splits a sentence into words with part of speech and reading. |
| **JMdict** | Free Japanese→English dictionary (~200k entries, includes expressions). Bundled in the app. |
| **Deinflection** | Turning a conjugated word back into its dictionary form (食べられなかった → 食べる). |
| **Room** | Android's local database (SQLite for Kotlin). |
| **Cache** | Stored results, so they don't need to be computed again. |
| **Hash** | An image "fingerprint": equal images → equal hash. Used to recognize a page already seen. |
| **AccessibilityService** | Accessibility service; among other things it can dispatch gestures on screen. |

---

## 3. Architecture

```
┌──────────────────────────── APP (a single APK) ────────────────────────────┐
│                                                                             │
│  UI (Compose)                    Background (Foreground Service)            │
│  ├─ Home: saved cards            ├─ ScreenCapturer   (MediaProjection)      │
│  ├─ Enable/Disable toggle ─────► ├─ PageChangeDetector (compares frames)    │
│  ├─ Reader options               ├─ Pipeline:                               │
│  └─ Settings (DeepL key)         │    detection → OCR → tokenization        │
│                                  │    → dictionary → translation (DeepL)    │
│                                  ├─ PageCache (page hash → result)          │
│                                  └─ OverlayManager (furigana, popups,       │
│                                       touch areas, status dot)              │
│                                                                             │
│  Data: Room (saved cards + page cache) · JMdict (bundled SQLite)            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Main flow

```
[Home] ─ tap "Enable"
   │
   ├─► ask for the Overlay permission (first time only)
   ├─► ask for screen capture consent (every time — Android rule)
   ▼
[Service starts] → persistent notification "Reading screen…"
   │
   ▼  LOOP every ~400 ms
 1. Capture a downscaled frame
 2. Compare with the previous frame ── same? ──► back to 1
 3. Changed → wait for the screen to settle (~250 ms without changes, page-turn animation over)
 4. Hash it → page already seen? ──► use the cache (instant)
 5. New page:
      a) detect text blocks / bubbles
      b) OCR each block
      c) filter junk (noise, Latin text, furigana already printed)
      d) Kuromoji: words + reading + part of speech
      e) JMdict: meaning of each word (offline)
      f) DeepL: ALL bubbles of the page in one request (with context)
      g) store everything in the cache
 6. Draw furigana (if enabled) + create touch areas over the bubbles
 7. Status dot: 🟠 processing → 🟢 ready → 🔴 error
   │
   ▼  TOUCHES
 ├─ 1 tap on a word      → popup: word, reading, romaji, meanings + [Save]
 ├─ 2 taps on a bubble   → popup: translation, romaji, highlighted particles + [Save]
 ├─ tap outside a popup  → closes the popup
 └─ outside the bubbles  → the touch goes straight to the reader (page turns normally)
```

---

## 4. Ideas taken from FrankYomik

FrankYomik (akitaonrails) runs everything on a **GPU server** and its app only
shows finished images. Ours does everything **on the phone**. Still, several of
its ideas are worth it:

| FrankYomik idea | How it fits our app |
|---|---|
| **Status dot** (amber/green/red) | Small corner indicator: you know whether the page is ready before tapping. |
| **RT-DETR bubble detector** (`ogkalu/comic-text-and-bubble-detector`) | Phase 7: converted to ONNX, running on the phone. Much better at bubbles than ML Kit. |
| **manga-ocr** as main OCR | Phase 7: quantized ONNX (~120 MB). Far more accurate on vertical text. |
| **Whole-page translation in one request** | Send all bubbles in a single DeepL request, plus the previous page's bubbles in the `context` parameter (not billed). More coherent translation, fewer calls. |
| **Cache by image hash** | Going back to a page = instant result, no DeepL quota spent. |
| **Reading overrides** (e.g. 私 → わたし, not わたくし) | Override table for casual manga speech. |
| **Furigana only over kanji** (食べる → たべ over 食べ, not over る) | Okurigana splitting logic. |
| **OCR validation** (drop noise, Latin text, punctuation-only) | Filter before tokenizing/translating. |
| **Gesture rule: more than 12 px of travel = drag** | Fixes option B's weak spot (below). |
| **Book mode** (vertical novel columns) | Phase 8: separate pipeline for books, which have a regular layout. |

### Improving option B

Option B's weak spot: a drag that **starts on a bubble** does not turn the page.
The fix, inspired by FrankYomik's gesture rule:

```
finger lands on a bubble
   ├─ moved > 12 px before lifting? ──► DRAG → the app re-dispatches the gesture
   │                                    to the reader via AccessibilityService
   │                                    (dispatchGesture) → the page turns
   └─ lifted without moving ──► TAP → wait ~250 ms for a 2nd tap
                                  ├─ 2nd tap came → bubble translation
                                  └─ it didn't    → word translation
```

The accessibility service is **optional**: without it the app still works, but
drags starting on a bubble won't turn the page.

### What we will NOT copy
- **Server/GPU/Ollama**: the goal is to run on the phone, no home server.
- **Repainting the translated page (inpainting)**: we keep the original intact, translation on demand only.
- **Source code**: FrankYomik is **AGPL-3.0**. We only take *ideas* and implement from scratch in Kotlin. ML models (detector, manga-ocr) have their own licenses, to be checked in Phase 7.

---

## 5. Extra ideas

- ~~Export to Anki~~ → **decided: the app is its own "Anki"**. Each card has a study
  mode (front/back + Again/Hard/Good/Easy) with spaced repetition (SM-2), and the
  home has "Review now (N)" with every due card. **Done in Phase 1.**
- **JLPT level** on each word (N5…N1).
- **Home filters**: by type (word / expression / sentence), by source manga, by date.
- **Hide furigana only for words you have mastered**: a middle ground that follows your progress.
- **DeepL quota counter** in Settings.

---

## 6. Phases

Each phase ends with something **testable on the phone**.

### Phase 0: Environment ✅
- [x] Install Android Studio (with the default SDK)
- [x] Enable USB debugging on the S24 FE
- [x] Create the GitHub repository (private)
- [x] Create a DeepL API Free account (the key **never** goes to GitHub; it is typed into the app)

### Phase 1: Skeleton + Home + study ✅
- Kotlin + Compose + Room + DataStore project
- Home with Enable/Disable toggle (no effect yet), filters and the card list
- Vocabulary cards with furigana (per-card toggle) and sentence cards with highlighted particles
- Card detail: meanings, romaji, per-word sentence breakdown, context, study
- Spaced repetition (SM-2): flashcard in each card + "Review now (N)" session
- Reader options screen (furigana on page, status dot)
- Sample data inserted on first launch only
- Unit tests: SM-2 scheduler, furigana splitting, romaji
- ✅ Test: open the app, see the cards, toggle furigana, study a card

### Phase 2: Background screen capture ✅
- Foreground Service + MediaProjection + notification with a "Stop" action
- Page change detector (downscaled frames + pixel difference)
- Debug overlay showing "page changed"
- ✅ Test: enable, open the manga reader, turn pages and see the notice

### Phase 3: OCR (ML Kit Japanese) ✅
- OCR on frames, boxes drawn on the overlay (debug mode)
- Noise filter
- ✅ Test: see boxes over the bubbles

### Extra: manual cards ✅
- Type romaji (or Japanese) → kana → kanji suggestions from JMdict, with conjugated
  forms (deinflection), particles and katakana for CAPITALS; tap a word to pick another spelling
- Scan with the camera or pick an image → OCR → tap the text blocks → same card editor, with a crop
- Bundled JMdict (SQLite, built by `:tools:dictbuilder`) and Kuromoji tokenizer — shared with Phase 4

### Phase 4: Furigana + dictionary + 1 tap ✅
- Kuromoji (tokenization + readings) + reading overrides
- Bundled JMdict (SQLite) + deinflection
- Furigana drawn over kanji/katakana
- Touch areas over bubbles; 1 tap → word popup (with romaji) + [Save]
- ✅ Test: tap a word, see its meaning, save it and find it on Home

### Phase 5: Bubble translation (DeepL) + 2 taps ✅
- Settings screen with the DeepL key (stored encrypted on the device)
- Batched whole-page translation + previous-page context
- Page cache (hash) in Room
- 2 taps → sentence popup (with romaji) + [Save sentence]
- Status dot
- ✅ Test: double tap a bubble → instant translation

### Phase 6: Reader options + polish ✅
- Reader options: furigana on/off, furigana size, status on/off, capture interval
- Optional AccessibilityService to forward drags (option B improvement)
- Battery tuning (pause when the screen is still, lower resolution)
- ✅ Test: long reading session without hiccups

### Phase 7: Quality upgrade (on-device ML) — postponed
ML Kit OCR is good enough for now (decided 2026-10-14); revisit if stylized text becomes a problem.
- Bubble detector (ONNX) + manga-ocr (quantized ONNX), downloaded on first run
- ML Kit kept as fallback
- ✅ Test: compare accuracy before/after on the same pages

### Phase 8: Extras
- Book mode (vertical columns)
- JLPT level, study statistics
- Replace SM-2 with FSRS (Anki's newer algorithm), if worth it

---

## 7. Samsung S24 FE notes

- **Battery**: One UI aggressively kills background services. Set
  Settings → Apps → Manga Reader JP → Battery → **Unrestricted**.
  The app will guide you there.
- **Screen sharing (Android 14+)**: on enable, the system asks "Share one app"
  or "Share entire screen". Choose **entire screen**.
- **Apps that block capture** (FLAG_SECURE): the capture comes out black. The app
  will detect it (all-black frame) and warn you.
- **Screen off ends capture (Android 15+)**: the system stops screen sharing when the
  screen turns off, and a new capture always needs consent. The reader keeps the screen
  awake while on ("Keep screen on" option) and, if capture still ends, shows a
  "Reader paused — tap to resume" notification.
- **120 Hz display**: irrelevant for capture; we sample ~4 frames/s and only
  process when the page changes.

---

## 8. Risks

| Risk | Mitigation |
|---|---|
| ML Kit OCR weak on stylized vertical text | Phase 7 (manga-ocr) |
| Printed furigana confusing the OCR | Filter very small characters next to kanji |
| Running out of DeepL quota | Hash cache + translate only valid bubbles + quota counter |
| Battery drain | Process only on page change, low-resolution capture |
| Drag on a bubble doesn't turn the page | Optional AccessibilityService (Phase 6) |
