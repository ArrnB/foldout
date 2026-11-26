# Putting Foldout on the Google Play Store

What is already done in this repository, and the steps that need you (your Google account, your money, your identity documents, your signing key).

Policies change often. Check each step against the linked Google pages before you rely on it. This guide was written in November 2026.

## Already done

| Requirement | Where |
|---|---|
| Targets Android 16 (API level 36). Play has required this for new apps and updates since 31 August 2026. | `android/app/build.gradle.kts` |
| Builds an Android App Bundle (`.aab`), the format Play requires for new apps | `./gradlew bundleRelease` |
| Release signing reads your upload key from `android/keystore.properties`, which git ignores | `android/app/build.gradle.kts`, `android/.gitignore` |
| Code and resources shrunk with R8 (about 300 KB) | release build type |
| Only one permission (the camera, for the live viewfinder), no network access, no data collected. This makes the Data safety form and the review simple. | `AndroidManifest.xml` |
| Privacy policy page (Play requires one even for apps that collect nothing) | https://arrnb.github.io/foldout/privacy.html |
| App icon (adaptive, plus a 512×512 PNG for the listing) | `android/app/src/main/res`, `icons/icon-512.png` |

## Steps for you

### 1. Choose a permanent package name

The app is currently `app.foldout`. A package name can never change once the app is on Play, and it must not already be used by another app. Since you don't own the domain `foldout.app`, a safer choice is one based on your GitHub Pages domain, for example `io.github.arrnb.foldout`. Change `applicationId` in `android/app/build.gradle.kts` before your first upload. Also search Play for "Foldout" to check the name isn't confusingly close to an existing app.

### 2. Create a Google Play developer account

- https://play.google.com/console/signup. There is a one-time registration fee of US$25.
- Pick **personal** (just you) or **organization** (needs a D-U-N-S number).
- Google verifies your identity (government ID, and for personal accounts a verified phone number and email). Android's new developer-verification program, which Google is enforcing country by country from late 2026, is handled through Play Console too. Follow whatever verification steps it asks for there.

### 3. Make your upload key (once, and keep it safe)

```
keytool -genkeypair -v -keystore ~/keys/foldout-upload.jks -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000
```

Then create `android/keystore.properties` (never commit it, never share it):

```
storeFile=/home/you/keys/foldout-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Back up the `.jks` file and the passwords somewhere safe outside this folder. Play App Signing (on by default) keeps the real app-signing key on Google's side, so a lost upload key can be reset through Play support. Losing it is still a hassle.

### 4. Build the bundle

```
cd android
./gradlew bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

Raise `versionCode` (and `versionName`) in `android/app/build.gradle.kts` for every upload.

### 5. Create the app in Play Console and fill in the forms

- **App content**:
  - **Privacy policy:** `https://arrnb.github.io/foldout/privacy.html`
  - **Data safety:** "No data collected" and "No data shared". Camera frames are processed on the device only, and the app has no internet permission.
  - **Ads:** No ads.
  - **Content rating questionnaire:** a utility with no user-generated sharing; expect "Everyone".
  - **Target audience:** adults/general (not designed for children).
  - **App access:** all features available without login.
- **Store listing**:
  - App name (up to 30 characters) and short description (up to 80)
  - Full description
  - App icon 512×512 (`icons/icon-512.png`)
  - Feature graphic 1024×500
  - At least 2 phone screenshots
- Category: *Productivity*.

### 6. Closed testing (new personal accounts)

Personal developer accounts created after 13 November 2023 must run a **closed test with at least 12 testers who stay opted in for 14 days in a row** before Google lets the app go to production. Upload the `.aab` to a closed testing track, invite testers by email (friends, classmates; recruit a few extra in case some drop out), and keep them opted in for the full 14 days. Then apply for production access from the Dashboard. Google says the review usually takes 7 days or less.

Organization accounts don't have this requirement.

### 7. Release to production

Create a production release with the same (or newer) bundle, roll it out, and wait for review. A first review can take several days.

## After launch

- Each update: raise `versionCode`, rebuild with `bundleRelease`, upload.
- Each year Google raises the minimum target API level (usually by 31 August). Bump `targetSdk`/`compileSdk` and test before then.
- Installing the Play version on a phone that has a USB-installed copy needs an uninstall first, because the signatures differ.

## Sources

- Target API levels: https://developer.android.com/google/play/requirements/target-sdk
- Testing requirement for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
- Android developer verification: https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html
