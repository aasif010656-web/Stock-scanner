# Firebase Hosting deployment

The same canonical browser bundle used by the Android Capacitor app is deployed as a static website from `FHL_NATIVE_APP/app/src/main/assets/public`.

## One-time activation

1. Open the Firebase Console for the `stock-take-9a67a` project and enable **Hosting**. The first activation creates the default Hosting site and its `web.app`/`firebaseapp.com` address.
2. In the GitHub repository **Settings → Secrets and variables → Actions**, confirm that `FIREBASE_SERVICE_ACCOUNT_JSON` is available. The existing Android release workflow already uses this secret for Firestore release metadata. It must be a Firebase service-account JSON key authorized to deploy to Hosting.
3. Run the **Deploy FHL Electronics Website** workflow manually once, or push the prepared files to `main`. The workflow deploys the `live` Firebase Hosting channel.
4. Optionally add a custom domain in **Firebase Console → Hosting → Add custom domain** and complete the DNS verification steps shown there.

## Automatic deployments

After activation, a push to `main` that changes the canonical browser bundle, Firebase Hosting configuration, or Hosting workflow triggers a live Firebase Hosting deployment. The Android release workflow remains separate and continues to build the signed APK from the same bundle.

## Safety and behaviour

- `firebase.json` serves only the canonical static web bundle and rewrites client-side routes to `index.html`.
- The Firebase credentials are referenced only through the encrypted GitHub secret; no credential is committed to the repository.
- `index.html` and `sw.js` are configured not to be browser-cached by Firebase Hosting so future releases are discovered promptly.
- The service worker registers only in a browser; it is explicitly skipped when the Android native bridge is present.

## Sources

- Firebase documentation: https://firebase.google.com/docs/hosting/github-integration
- Firebase Hosting configuration reference: https://firebase.google.com/docs/hosting/full-config
- Firebase Hosting GitHub Action: https://github.com/FirebaseExtended/action-hosting-deploy
