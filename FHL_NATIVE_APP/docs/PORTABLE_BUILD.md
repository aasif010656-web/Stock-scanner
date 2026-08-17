# Portable Build Guide

## Purpose and Boundaries

This guide makes the FHL Electronics source reproducible on a trusted development machine or CI platform. It covers the Android application, iOS 18+ Capacitor project, shared web bundle, GitHub Actions release workflows, and the live Firestore integration. It **does not** place business data, passwords, signing keys, Apple certificates, or service-account credentials in Git.

> The maintained shared interface begins at [`app/src/main/assets/public/index.html`](../app/src/main/assets/public/index.html). Android loads that directory directly. The `sync:web` script copies the same files into `www/`, and `sync:ios` then copies them into the Capacitor iOS project. Capacitor’s synchronization model copies the configured `webDir` bundle and updates native dependencies. [1]

| Target | Supported build host | Primary command or IDE | Output |
|---|---|---|---|
| Android debug | Windows, macOS, or Linux | `pnpm run build:android:debug` | Device-testing APK under `app/build/outputs/apk/debug/` |
| Android release | Windows, macOS, Linux, or GitHub Actions | `pnpm run build:android` with signing environment variables | Signed release APK under `app/build/outputs/apk/release/` |
| iOS 18+ debug | macOS with Xcode | `pnpm run sync:ios`, then Xcode | Device build from `ios/App/App.xcworkspace` |
| iOS TestFlight | Apple Developer Program plus macOS runner | `.github/workflows/ios-release.yml` | Signed IPA uploaded to TestFlight |
| Static web bundle | Any HTTPS static host | Copy the canonical shared asset directory | Browser/PWA version; native-only behavior depends on browser capabilities |

The Android project uses Java 17, `compileSdk 34`, `minSdk 24`, and application ID `com.abdulasif.pdtstockscanner`, as defined in [`app/build.gradle`](../app/build.gradle). The iOS project is configured for the same application identity and iOS 18 deployment target. Keep the visible app version aligned across the Android Gradle file, iOS Xcode project, shared bundle constants, and release workflow.

## Common Setup

Install Git, Node.js 20 LTS, Corepack, pnpm, and the target-platform toolchain. The lockfile uses pnpm lockfile format 9, so installation should use the lockfile rather than resolving new package versions.

```bash
git clone https://github.com/aasif010656-web/Stock-scanner.git
cd Stock-scanner/FHL_NATIVE_APP
corepack enable
pnpm install --frozen-lockfile
```

The repository already approves the required build dependencies through `pnpm-workspace.yaml`. Do not work around that policy by committing `node_modules` or changing the lockfile merely to install a local tool.

## Android Build

Install a JDK 17 distribution and Android SDK Platform 34 with Build Tools 34.0.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` for the local SDK; `local.properties` is intentionally ignored because it is machine-specific. The Android configuration is a maintained Gradle project, not a generated Capacitor `android/` directory, so use its Gradle wrapper directly.

```bash
cd FHL_NATIVE_APP
chmod +x gradlew                 # macOS/Linux only
pnpm run build:android:debug
```

For a signed release, keep the keystore outside the repository and pass its path and credentials only through the local environment or protected CI secrets.

```bash
export ANDROID_KEYSTORE_PATH="/secure/path/fhl-release.keystore"
export ANDROID_KEYSTORE_PASSWORD="..."
export ANDROID_KEY_ALIAS="..."
export ANDROID_KEY_PASSWORD="..."
pnpm run build:android
```

The repository’s GitHub Actions Android workflow uses the corresponding protected secrets shown below. It restores the keystore only in the temporary runner directory, builds the APK, creates a GitHub release, and updates Firestore release metadata.

| GitHub Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded existing Android release keystore; never commit the binary file. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. |
| `ANDROID_KEY_ALIAS` | Release key alias. |
| `ANDROID_KEY_PASSWORD` | Alias password. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Service-account JSON used only by the release workflow to update the Firestore update document. |

Capacitor Android supports API 24+ and is normally managed through Android Studio, although this repository’s native Gradle project can also be built from the command line. [2]

## iOS Build and TestFlight

The iOS project is in `ios/App/` and must be built on macOS with Xcode. From the native project directory, synchronize the canonical FHL browser bundle and open the workspace, not the `.xcodeproj` file.

```bash
cd FHL_NATIVE_APP
pnpm run sync:ios
open ios/App/App.xcworkspace
```

For personal on-device testing, select a signing team in Xcode and use a compatible Apple ID. TestFlight distribution requires an Apple Developer Program membership, an App Store Connect record for `com.abdulasif.pdtstockscanner`, and the Apple distribution credentials described in [`TESTFLIGHT_SETUP.md`](TESTFLIGHT_SETUP.md). The automation is already at [`.github/workflows/ios-release.yml`](../../.github/workflows/ios-release.yml); it is deliberately run manually and will remain blocked until those protected values exist.

| GitHub Secret or Variable | Purpose |
|---|---|
| `APPLE_TEAM_ID` | Apple Developer team ID. |
| `APPLE_DISTRIBUTION_CERTIFICATE_BASE64` and `APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD` | Exported Apple Distribution `.p12` signing certificate. |
| `APPLE_PROVISIONING_PROFILE_BASE64` | App Store provisioning profile for the configured bundle ID. |
| `APPLE_KEYCHAIN_PASSWORD` | Temporary CI keychain password. |
| `APPSTORE_API_PRIVATE_KEY` | App Store Connect API private key. |
| `APPSTORE_ISSUER_ID` and `APPSTORE_API_KEY_ID` | Repository variables identifying the App Store Connect API key. |

The iOS workflow archives the project on a macOS runner, exports a signed IPA, uploads it as a workflow artifact, and submits it to TestFlight only after every required secret/variable has been supplied. Never commit `.p12`, `.mobileprovision`, or `.p8` files. GitHub’s macOS signing guidance describes the same temporary-keychain approach. [3]

## Static Web Bundle

The current app interface can be hosted as a static HTTPS site by publishing the contents of the canonical asset directory. A web host must serve `index.html`, `manifest.json`, icons, and the FHL logo with HTTPS. Do not use the root-level legacy `index.html` as the canonical mobile source.

```bash
mkdir -p dist-web
cp -a FHL_NATIVE_APP/app/src/main/assets/public/. dist-web/
# Upload dist-web/ to a trusted HTTPS static hosting provider.
```

The published web version uses the same Firestore project and therefore the same live data contract. The web site intentionally uses an **in-app notification centre** rather than browser push. Native APK installation, Android native notifications, and native scanner overlay behavior are not portable to an ordinary browser; test the target browser separately before relying on a device feature.

## Release and Version Discipline

For any product change, edit the canonical bundle first, validate it, and then synchronize it to iOS. Android directly consumes the canonical asset directory. Keep the visible version `6.1`; the app update-comparison identity may remain separately configured as `6.1.1` to preserve the existing release comparison behavior. The Android release workflow injects the CI build number into the shared bundle during release and writes the same release identity to Firestore.

When a user requests an edit through the managed Manus web project, treat it as an approved-change request rather than a separate website source. Apply the change to `app/src/main/assets/public/`, push `main`, and let the Android and Firebase workflows release the common bundle. The Firebase workflow runs `pnpm run verify:firebase-sync` equivalent contract validation before deployment, preventing the live website from being redirected to a non-canonical directory.

Before publishing an Android update, preserve the existing Android signing identity. A release signed with a different certificate cannot update the installed release outside a managed signing-migration process. Before publishing an iOS update, increase the Xcode/CI build number while retaining the registered bundle ID and valid provisioning profile.

## Repository Security Rules

The root `.gitignore` blocks keystores, certificates, Apple API keys, provisioning profiles, local Android paths, generated dependencies, and CocoaPods output. Do not override these rules. The public repository history was rewritten to remove the previously tracked Android signing keystore; collaborators must pull the rewritten `main` branch or freshly clone the repository before contributing.

If the repository is forked, copied, or downloaded, treat every copy as untrusted until you verify that no secret files were preserved. The original Android signing identity must be kept in controlled secure storage for future in-place updates; do not regenerate or rotate it without assessing installed-app update compatibility.

## References

[1] [Capacitor Workflow](https://capacitorjs.com/docs/basics/workflow)

[2] [Capacitor Android Documentation](https://capacitorjs.com/docs/android)

[3] [GitHub Actions: Installing an Apple Certificate on macOS Runners](https://docs.github.com/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development)
