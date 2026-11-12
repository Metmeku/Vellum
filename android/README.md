# Vellum for Android

Offline WebView wrapper around the Vellum web app. All app files are inside the APK
(`app/src/main/assets/www`, copied from `web/` at build time). The page blocks all outside connections.

## Build the APK (Android Studio)
1. Install Android Studio (it includes the Android SDK and Java 17).
2. File → Open → choose this `vellum-android` folder. Let Gradle sync (needs internet once).
3. Build → Build App Bundle(s) / APK(s) → Build APK(s).
4. The APK is at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and install
   (allow "Install unknown apps" for your file manager).

For a release build to share with others: Build → Generate Signed App Bundle / APK → APK,
create a keystore, and keep that keystore safe — every future update must be signed with it.

## Updating the app
Replace `app/src/main/assets/www/index.html` with the new version, bump `versionCode`
in `app/build.gradle`, and rebuild. Data on the phone is kept across updates.

## Notes
- Backups and CSV exports save to Downloads/Vellum.
- Import (backup restore, Telebirr CSV) opens the Android file picker.
- The phone's Back button closes open panels first, then goes back a screen.
