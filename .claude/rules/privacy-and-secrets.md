# Privacy and secrets

- Never commit a keystore, password, OAuth client secret or token. `*.jks` and `*.keystore` are
  git-ignored; signing material lives only in the repo secrets `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, which `.github/workflows/build.yml` reads.
- Don't change the signing setup in `app/build.gradle.kts` without a reason: the OAuth Android
  client is bound to the package name plus that key's SHA-1, and a different key breaks sign-in.
- Google scopes are read-only `gmail.readonly` and `calendar.readonly`, plus `drive.appdata` for
  backup. Don't add scopes casually; the app is in Testing mode (re-login every 7 days) and
  restricted scopes would need Google verification to publish.
- Task data stays on the phone: SQLCipher database, `android:allowBackup="false"`, screenshots and
  recents preview blocked. The only off-device copy is the passphrase-encrypted Drive backup.
- Gemini Nano and speech recognition run on-device; don't send email text, transcripts or tasks
  to any network service.
- Photo to task reads images with on-device ML Kit (text recognition is bundled; entity extraction
  downloads only its model, once). Don't store, upload or back up the photos themselves.
- The repo is public: no real email content, names beyond the family defaults already in the
  code, or personal data in tests, fixtures or commit messages.
