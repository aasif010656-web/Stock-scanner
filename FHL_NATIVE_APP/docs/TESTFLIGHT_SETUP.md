# FHL ELECTRONICS v6.1 — TestFlight Setup

The iOS source is configured for the bundle identifier `com.abdulasif.pdtstockscanner`, iOS 18.0, and the visible marketing version **6.1**. The GitHub workflow at `.github/workflows/ios-release.yml` creates a signed IPA and uploads it to TestFlight only when an authorized maintainer deliberately selects **Run workflow** on the `main` branch. This prevents ordinary source or documentation commits from producing unintended uploads or expected failures before Apple credentials exist.

## One-time Apple setup

Enroll the account in the Apple Developer Program and create an App Store Connect app record with the exact bundle identifier `com.abdulasif.pdtstockscanner`. In the Apple Developer portal, create an **Apple Distribution** certificate and an **App Store** provisioning profile for the same App ID. The provisioning profile’s App ID must not use a wildcard.

Create an App Store Connect **Team API key** with the **App Manager** role. Record its issuer ID and key ID, and immediately download the `AuthKey_<key-id>.p8` private key. Apple only allows that private key to be downloaded once, so store it securely and never add it to this repository.[1]

## Repository configuration

In the GitHub repository, open **Settings → Secrets and variables → Actions**. Add the following encrypted **repository secrets**.

| Secret | Value |
| --- | --- |
| `APPLE_TEAM_ID` | The 10-character Apple Developer Team ID. |
| `APPLE_DISTRIBUTION_CERTIFICATE_BASE64` | Base64 text of the Apple Distribution `.p12` certificate. |
| `APPLE_DISTRIBUTION_CERTIFICATE_PASSWORD` | The password used when exporting that `.p12` certificate. |
| `APPLE_PROVISIONING_PROFILE_BASE64` | Base64 text of the App Store `.mobileprovision` profile. |
| `APPLE_KEYCHAIN_PASSWORD` | A new random password used only for the temporary CI keychain. |
| `APPSTORE_API_PRIVATE_KEY` | Entire contents of the downloaded `AuthKey_<key-id>.p8` file. |

Add the following **repository variables**.

| Variable | Value |
| --- | --- |
| `APPSTORE_ISSUER_ID` | The issuer ID shown beside the App Store Connect Team API key. |
| `APPSTORE_API_KEY_ID` | The key ID shown for that Team API key. |
| `APPSTORE_USES_NON_EXEMPT_ENCRYPTION` | Set to `true` only if the completed Apple export-compliance assessment says that the app uses non-exempt encryption; otherwise set `false`. |

To create Base64 values on macOS, use the following commands locally. Copy the resulting text into the matching secret, then delete any temporary copies.

```bash
base64 -i AppleDistribution.p12 | pbcopy
base64 -i FHL_AppStore.mobileprovision | pbcopy
```

## First delivery

After all identifiers and secrets have been configured, open the **Actions** page, select **iOS TestFlight**, and choose **Run workflow** on the `main` branch. Each workflow execution uses its GitHub run number as the unique iOS build number, so rerunning the workflow creates a distinct TestFlight build while the visible version remains **6.1**. Apple processes the upload before it appears in TestFlight.[2]

Before adding external testers, complete the TestFlight test information and any required export-compliance prompts in App Store Connect. The first build for external testing is subject to Apple’s beta review.[3]

## Local Xcode verification

On a Mac with current Xcode, install the dependencies and open the generated workspace—not the project file—because the workspace includes CocoaPods dependencies.

```bash
cd FHL_NATIVE_APP
pnpm install --frozen-lockfile
pnpm run sync:ios
npx cap open ios
```

In Xcode, select the **App** scheme, choose an iOS 18+ device or simulator, set the same Apple team for signing, and build. Verify barcode scanning, price-notification permission, Excel import/export, and the app icon before the first TestFlight upload.

## References

[1] Apple, [Creating API Keys for App Store Connect API](https://developer.apple.com/documentation/appstoreconnectapi/creating-api-keys-for-app-store-connect-api)

[2] Apple, [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)

[3] Apple, [TestFlight](https://developer.apple.com/testflight/)
