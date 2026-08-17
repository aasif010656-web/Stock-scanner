# FHL ELECTRONICS v6.1

This repository is the **portable source package** for the FHL Electronics stock-scanner application. It contains the maintained Android project, the iOS 18+ Capacitor project, the shared browser bundle, CI workflows, and the instructions required to rebuild the app on a trusted development platform.

> **The source code is in GitHub; live business data is not.** Products, stock, users, logs, imports, and release metadata remain in the existing Firestore project. Android signing keys, Apple certificates, provisioning profiles, and Firebase service-account JSON must remain in protected local storage or GitHub Secrets.

| Need | Canonical path | Notes |
|---|---|---|
| Shared FHL interface | [`FHL_NATIVE_APP/app/src/main/assets/public/`](FHL_NATIVE_APP/app/src/main/assets/public/) | The native Android app reads this bundle directly. It is also the source copied into Capacitor iOS `www/`. |
| Android native layer | [`FHL_NATIVE_APP/app/src/main/java/`](FHL_NATIVE_APP/app/src/main/java/) | WebView, barcode scanner, native notifications, download/install flow, and file bridge. |
| iOS 18+ project | [`FHL_NATIVE_APP/ios/`](FHL_NATIVE_APP/ios/) | Capacitor project with iOS permissions, app icon, scanner, notifications, and sharing integration. |
| Android build configuration | [`FHL_NATIVE_APP/app/build.gradle`](FHL_NATIVE_APP/app/build.gradle) | App ID `com.abdulasif.pdtstockscanner`, Java 17, Android SDK 34, visible version 6.1. |
| Portable build guide | [`FHL_NATIVE_APP/docs/PORTABLE_BUILD.md`](FHL_NATIVE_APP/docs/PORTABLE_BUILD.md) | Android, iOS, static web, release automation, and secret handling. |
| Firestore data contract | [`FHL_NATIVE_APP/docs/FIRESTORE_DATA_CONTRACT.md`](FHL_NATIVE_APP/docs/FIRESTORE_DATA_CONTRACT.md) | Current document paths, data boundaries, preservation, and migration guidance. |
| Android release automation | [`.github/workflows/release.yml`](.github/workflows/release.yml) | Signed APK release plus Firestore update metadata. |
| iOS TestFlight automation | [`.github/workflows/ios-release.yml`](.github/workflows/ios-release.yml) | Ready for Apple credentials and a Developer Program membership. |

The root-level `index.html` is a legacy static export. New feature work and mobile/web parity changes must begin in `FHL_NATIVE_APP/app/src/main/assets/public/` and then be synchronized to the desired target bundle.

## Manus, Android, and Firebase editing rule

For this project, GitHub is the approved shared release source. A user-requested edit made through the managed Manus web project must be applied to the canonical bundle at `FHL_NATIVE_APP/app/src/main/assets/public/`, reviewed, and pushed to `main`. That one bundle is loaded by the Android WebView and deployed by Firebase Hosting, so the signed Android APK and **https://stock-take-9a67a.web.app/** remain aligned.

Direct visual-editor changes inside the managed Manus preview are not themselves a Firebase deployment source and must not be treated as completed product releases. Use that preview for review and testing; put approved application changes in the canonical GitHub bundle. The Firebase workflow verifies this contract before each live deployment.

## Fast Start

Clone this repository and enter the native project directory. Use a current Node.js 20 LTS installation and Corepack-managed pnpm, then restore the lockfile-defined dependencies.

```bash
git clone https://github.com/aasif010656-web/Stock-scanner.git
cd Stock-scanner/FHL_NATIVE_APP
corepack enable
pnpm install --frozen-lockfile
```

For Android, install JDK 17 and Android SDK Platform 34, then run `pnpm run build:android:debug` for a device-testing APK or provide the four signing environment variables documented in the build guide for a signed release. For iOS, use macOS with Xcode, run `pnpm run sync:ios`, and open `ios/App/App.xcworkspace` in Xcode. Capacitor synchronizes the configured web asset directory into its native projects. [1]

## Security Status

The repository has repository-level ignore rules for signing keys, Apple profiles, Apple API keys, local SDK paths, generated dependencies, and CocoaPods output. The Android signing keystore has also been removed from the current source and rewritten out of the public Git history. Do not commit or paste signing material into source, issues, pull requests, or release notes.

Anyone who previously cloned an older revision must delete their local copy of the removed signing file. If the repository was copied or forked while the file was public, assume that copy remains outside this repository’s control and limit access to the signing identity accordingly.

## References

[1] [Capacitor Workflow](https://capacitorjs.com/docs/basics/workflow)
