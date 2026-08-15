# Firestore Data Contract and Migration Guide

## What Stays Outside GitHub

The FHL Electronics repository stores application code, native projects, build automation, and non-secret configuration. The live Firebase data store is intentionally separate. It contains operational stock information, imported workbooks, price history, scan logs, user records, role assignments, and the release-update record. Do **not** export these records to GitHub, commit them as fixtures, or copy service-account JSON into the repository.

> To retain the existing live data when moving to another platform, keep the app connected to the current Firestore project. Changing a Firebase web configuration does not move data; it points the application at a different database.

| Firestore path | Role in the application | Primary writer |
|---|---|---|
| `stockScannerApp/sharedData` | Shared operational state: product overrides, scans, imports, reports, prices, user/role configuration, notifications, stock movements, audit entries, and A4/A7 data. | Authenticated FHL clients using the shared bundle. |
| `appSettings/updateInfo` | Current Android release version, tag, download URL, visible release name, and release timestamp. | GitHub Android release workflow via a service-account secret. |

The canonical shared bundle initializes the client against the existing `stock-take-9a67a` Firebase project and reads those two document paths. The client also maintains device-local caches for offline continuity; such caches are not authoritative backups and must not be relied on for migration.

## Preserve the Existing Data

For a new Android, iOS, or static web build that should display today’s business data, retain the existing Firebase project and Firestore paths. Start from the canonical bundle and do not replace its Firebase project configuration merely to create a new build. Confirm that the native app identity and browser host are allowed by the Firebase configuration and current Firestore rules before production use.

Firestore Security Rules evaluate mobile and web client requests before reads and writes. The server-side release workflow instead authenticates through its service account, which bypasses client rules and therefore must remain in protected CI secrets. [1]

## Migrate to a New Firebase Project

If the user intentionally moves to a new Firebase project, migrate the database before changing the application configuration. Firestore’s managed export/import service can export documents to Cloud Storage and import them into another Firestore database. The service requires the appropriate IAM permissions, a Cloud Storage bucket, and billing/Blaze-plan eligibility. [2]

The high-level process is as follows.

| Step | Action | Safety condition |
|---|---|---|
| 1 | Create the destination Firebase/Google Cloud project and Firestore database. | Do not point a production build at the new project yet. |
| 2 | Configure Firestore Security Rules and test them with the Rules simulator. | Never deploy an `allow read, write: if true` rule to production. [1] |
| 3 | Create a controlled Cloud Storage backup location and export the source Firestore database. | Keep export objects private and restrict IAM access. |
| 4 | Import the export into the destination Firestore database. | Validate document counts and the two required paths before changing app configuration. |
| 5 | Update the Firebase configuration only in the canonical shared bundle. | Retest Android, iOS, and web builds against the destination project. |
| 6 | Create a new service account for GitHub Actions and add its JSON only as `FIREBASE_SERVICE_ACCOUNT_JSON`. | Never commit the JSON file. |

An owner can start a managed full export with `gcloud` after completing the project, permissions, and Cloud Storage setup described in the official documentation:

```bash
gcloud config set project SOURCE_PROJECT_ID
gcloud firestore export gs://PRIVATE_BACKUP_BUCKET/fhl-firestore-export \
  --database='(default)'
```

Import into the destination project only after validating the export and target database settings:

```bash
gcloud config set project DESTINATION_PROJECT_ID
gcloud firestore import gs://PRIVATE_BACKUP_BUCKET/fhl-firestore-export/EXPORT_FOLDER \
  --database='(default)'
```

Use the actual export folder emitted by Firestore. Maintain a tested restore procedure before changing production data. The managed export/import service is suitable for recovery and inter-project transfer, but export operations can incur Firestore reads and need the required billing configuration. [2]

## Minimum Post-Migration Validation

After migration, validate the operational data—not merely that the app opens. Sign in with authorized roles, search a known barcode, confirm product/stock details, view a known party’s price log, open A4/A7 records, import a test workbook in a non-production copy, and verify that `appSettings/updateInfo` points to a valid current APK release. Validate Firestore rules with both allowed and denied client paths before exposing the new project.

## References

[1] [Get Started with Cloud Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)

[2] [Cloud Firestore Export and Import Data](https://firebase.google.com/docs/firestore/manage-data/export-import)
