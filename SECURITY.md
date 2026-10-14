# Security and privacy

Yomitomo reads your screen, so it is built to keep what it sees on your phone.

## What stays on the phone

| Data | Where | Leaves the phone? |
|---|---|---|
| Screen frames | Memory only, while the reader is on | Never |
| OCR text, furigana, dictionary lookups | Memory / app-private storage | Never |
| Saved cards and their image crops | App-private storage | Only in Android Auto Backup and in backup files you export yourself |
| Camera photos | App-private cache, deleted as soon as they are read | Never |
| DeepL API key | App-private storage, AES-GCM encrypted with an Android Keystore key | Only to DeepL, in the request header |
| Text sent for translation | — | To DeepL, if you added a key (the bubble text and the previous page as context) |

The on-device translator (Google ML Kit) downloads its language model once and then translates
offline; it does not send your text anywhere.

## How it is protected

- **Secrets**: the DeepL key is encrypted with a key that lives in the Android Keystore and can't
  be exported, not even by the app. The encrypted file is excluded from Android backups. The key
  is never logged and is never written to the repository (a pre-commit hook blocks commits that
  look like they contain credentials).
- **Network**: HTTPS only (`network_security_config.xml`). The app talks only to DeepL and, for the
  offline model download, to Google.
- **Exposed components**: only the launcher screen is open to other apps. The reader service, the
  resume screen and the camera file provider are private; the optional accessibility service can
  only be bound by the system.
- **Accessibility service** (optional, for swipes that start on a bubble): it can perform gestures
  but cannot read screen content (`canRetrieveWindowContent="false"`) and only receives events from
  this app.
- **Camera**: the app has no camera permission. It asks the phone's own camera app for one photo,
  which is written to a private cache file and deleted after it's read.
- **Screen capture**: Android asks for your consent every time the reader starts, and shows that
  the screen is being shared. Capture stops when you turn the reader off or the screen goes off.
- **Build**: the bundled dictionary is downloaded over HTTPS and checked for completeness; the
  release signing key lives outside the repository.

## Reporting a vulnerability

Please open a [GitHub security advisory](https://github.com/Otoniel2004/yomitomo/security/advisories/new)
(private) rather than a public issue. Include the Android version, the app version and steps to
reproduce.
