# Releasing Pulse (APK via GitHub Releases)

End users do **not** need Flutter. You build an Android APK once, then upload it to GitHub Releases.

## 1) Build the APK

From the repo root:

```bash
flutter pub get
flutter build apk --release
```

APK output:

```text
build/app/outputs/flutter-apk/app-release.apk
```

## 2) Use a real release signing key (recommended)

If you don’t provide a release keystore, the project falls back to **debug signing** for `--release` builds. That’s fine for personal testing, but it’s not ideal for sharing because:

- Users can’t reliably install updates if you later change signing keys.
- Debug keys are not meant for distribution.

Before you share widely, switch to a proper release keystore:

1. Create a keystore (example):

   ```bash
   keytool -genkeypair -v -keystore pulse-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pulse
   ```

2. Move the keystore to `android/pulse-release.jks` (or keep it elsewhere, just update `storeFile` accordingly).
3. Create `android/key.properties` (do not commit it):

   ```properties
   storePassword=YOUR_STORE_PASSWORD
   keyPassword=YOUR_KEY_PASSWORD
   keyAlias=pulse
   storeFile=pulse-release.jks
   ```

4. The app module (`android/app/build.gradle.kts`) will use this signing config automatically when `android/key.properties` exists.
5. Keep the keystore safe and backed up. If you lose it, you can’t ship updates over the same app install.

## 3) Create a GitHub Release (manual)

1. Push your code to GitHub.
2. On GitHub, open your repo → **Releases** → **Draft a new release**.
3. Create a new tag like `v1.0.0` (match the app version you want to ship).
4. Attach `build/app/outputs/flutter-apk/app-release.apk`.
5. Publish the release.

(Optional) Add a checksum in the release notes:

```bash
certutil -hashfile build\\app\\outputs\\flutter-apk\\app-release.apk SHA256
```

## 4) What users do (install)

1. Download the APK from your GitHub Release on their Android phone.
2. Android will prompt to allow installs from that source (browser/files app).
3. Open the app and enable the Accessibility Service:
   - **Settings → Accessibility → Pulse Controls**

## Automation (GitHub Actions)

This repo includes a workflow at `.github/workflows/android-release.yml` that:

- builds a release APK on tag pushes (example `v1.0.0`)
- uploads the APK + a `.sha256` file to the GitHub Release

### Enable signing in CI (recommended)

Add these GitHub repo secrets:

- `ANDROID_KEYSTORE_BASE64` — base64 of `android/pulse-release.jks`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

To generate the base64 value locally:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("android\\pulse-release.jks"))
```

Or on macOS/Linux:

```bash
base64 -w 0 android/pulse-release.jks
```

### Run it

- Create and push a tag, e.g. `v1.0.0`.
- GitHub Actions will build and publish the Release assets automatically.
