# Vellum

An offline personal finance manager built for Ethiopia: 25% inflation, a falling birr, gold, iqub, iddir, suitcase trade and ERCA tax. All data stays on the device.

- `web/` — the app (one HTML file plus service worker, manifest and icons). Netlify deploys this folder.
- `android/` — Android wrapper with native notifications. GitHub Actions builds the APK.

## Get the APK
Every push to `main` builds a signed APK and publishes it under **Releases**. Open the latest release on your phone and download the `.apk`.

## Signing secrets (one time)
In **Settings → Secrets and variables → Actions**, add:
- `KEYSTORE_BASE64` — the release keystore, base64-encoded
- `KEYSTORE_PASSWORD` — its password
- `KEY_ALIAS` — `vellum`

Keep a copy of the keystore somewhere safe. Every future update must be signed with the same key, or Android will refuse to install it over the old version.

## Notifications
The web app computes upcoming reminders (daily nudge, gold envelope, iqub turn and contributions, bills, insurance renewals, supplier credit, unsold stock, monthly review) and the Android app schedules them with AlarmManager. They survive reboots and work with the app closed.
