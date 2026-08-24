# Firebase Storage setup (Intro / Splash Video)

Upload `.mp4` from the admin panel requires **Firebase Storage**.

## Current blocker (ikhsana-hotel-tv)

CLI deploy failed with:

> Firebase Storage has not been set up … billing account … disabled / absent

So browser uploads stick at **0%** until Storage is enabled.

## One-time setup (project owner)

1. Open [Firebase Console → Storage](https://console.firebase.google.com/project/ikhsana-hotel-tv/storage)
2. Upgrade the project to **Blaze** (pay-as-you-go) if prompted — Storage needs billing.
3. Click **Get Started** and create the default bucket (prefer `asia-southeast1`).
4. Open **Rules** and publish the contents of [`storage.rules.example`](./storage.rules.example)
   (or from repo root: `firebase deploy --only storage` after setup).
5. Hard-refresh the admin panel and retry **Upload .mp4**.

## Workaround (works today)

Host the `.mp4` on any HTTPS CDN / object host, paste the direct file URL in
**Or paste a direct .mp4 URL** → **Save URL**.  
That writes `Hotels/{hotelId}/Config/intro.introVideoUrl` without Storage.
