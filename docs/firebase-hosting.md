# Firebase Hosting deployment

The same canonical browser bundle used by the Android Capacitor app is deployed as a static website from `FHL_NATIVE_APP/app/src/main/assets/public`.

## Live website

The first live deployment completed successfully on 16 August 2026. The website is available at **https://stock-take-9a67a.web.app/**. The GitHub Actions deployment run was `31940915709`.

## One-time activation

1. Firebase Hosting has already been enabled for the `stock-take-9a67a` project and the default Hosting site is live.
2. In the GitHub repository **Settings → Secrets and variables → Actions**, retain `FIREBASE_SERVICE_ACCOUNT_JSON`. The existing Android release workflow already uses this secret for Firestore release metadata, and the Hosting workflow uses it to deploy the website.
3. Future eligible pushes to `main`, or a manual **Deploy FHL Electronics Website** workflow run, deploy the `live` Firebase Hosting channel.
4. Optionally add a custom domain in **Firebase Console → Hosting → Add custom domain** and complete the DNS verification steps shown there.

## Automatic deployments

After activation, a push to `main` that changes the canonical browser bundle, Firebase Hosting configuration, or Hosting workflow triggers a live Firebase Hosting deployment. The Android release workflow remains separate and continues to build the signed APK from the same bundle.

## Safety and behaviour

- `firebase.json` serves only the canonical static web bundle and rewrites client-side routes to `index.html`.
- The Firebase credentials are referenced only through the encrypted GitHub secret; no credential is committed to the repository.
- `index.html` is configured not to be browser-cached by Firebase Hosting so future releases are discovered promptly.

## Sources

- Firebase documentation: https://firebase.google.com/docs/hosting/github-integration
- Firebase Hosting configuration reference: https://firebase.google.com/docs/hosting/full-config
- Firebase Hosting GitHub Action: https://github.com/FirebaseExtended/action-hosting-deploy
