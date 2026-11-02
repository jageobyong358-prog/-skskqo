# Limbus Archive for Android

An Android app (APK) for [Limbus Archive](https://github.com/Schirke/limbus-archive) v0.12.17 by Schirke (MIT, see
`LICENSE-upstream`).

## What it is

Upstream is a **Windows** app (Python + pywebview) that reads Limbus Company's game files from a PC install,
renders skills with a private Unity player and uses ffmpeg. None of that can run on a phone, and a phone has no
PC install of the game to read. Upstream's own answer is [limbus-archive.com](https://limbus-archive.com), a copy
of the app that runs in a browser. This APK opens that site full screen as an app:

- patch reports, news, change history, Identities & E.G.O, enemy handbook, story map, animations
- team builder, Mirror Dungeon planner, damage / upgrade calculators, music player, games

Only in the Windows app (they need the game on a PC): skill renders with effects, Versus, the Files browser.

## Android extras

- Back button goes back a page; links that leave the site (GitHub, YouTube, the wiki) open in your browser
- Downloads from the site (card PNGs, frame zips, JSON) are saved to `Downloads/LimbusArchive`
- Full-screen video, file pickers, an offline screen with a retry button
- Your progress is kept in the app's own storage, like a browser's (separate from your phone's browser)
- `limbus-archive.com` links can open in the app

## Getting the APK

Every push that touches this folder runs `.github/workflows/limbus-archive-android.yml`, which builds the APK and
publishes it as a GitHub Release (`limbus-archive-android-v<version>`). Open the release on your phone, download
`LimbusArchive-<version>.apk` and install it. Android 7.0 or newer.

## Building it yourself

Needs JDK 17 and the Android SDK (platform 35):

```
cd limbus-archive-android
gradle :app:assembleRelease      # Gradle 8.9 or newer
# -> app/build/outputs/apk/release/app-release.apk
```

The APK is signed with `app/limbus-archive.keystore` (password `limbusarchive`), kept in the repo on purpose so
every new build installs over the last one. It is not a secret key, so do not reuse it for anything else.

## Updating to a new upstream release

Nothing in the app needs to change for the site's content to update. To mark a new upstream version, change
`versionCode` / `versionName` in `app/build.gradle` and push.
