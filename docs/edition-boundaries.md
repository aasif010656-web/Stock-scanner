# FHL Edition Boundaries

The project has three deliberately separate editions. **Only the User edition is the production source of truth for Android and Firebase Hosting.**

| Edition | Display name | Android package | Data scope | Update / web behavior |
|---|---|---|---|---|
| User | `User` | `com.abdulasif.pdtstockscanner` | Shared live Firestore data | Signed Android release and Firebase Hosting website deploy together. In-app update remains enabled. |
| Admin | `ADMIN` | `com.abdulasif.pdtstockscanner.admin` | Shared live Firestore data | Separate native APK, administrator-role login only, no in-app update and no website. |
| Test | `Test` | `com.abdulasif.pdtstockscanner.test` | Isolated local demo data only | Separate future task, native APK only, no Firebase credentials, no live reads/writes, no website, and no in-app update. |

## Production release rule

For any User-facing change, edit the canonical bundle at `FHL_NATIVE_APP/app/src/main/assets/public/index.html`, synchronize `client/public/legacy/index.html`, update the service-worker cache and regression tests, then push `main`. The Android and Firebase workflows must both succeed.

The Test edition is intentionally outside this production repository workflow. Its local-demo handoff package must not be merged into the User or Admin release paths.
