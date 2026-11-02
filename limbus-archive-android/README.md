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

Plus **Files** (an app-only page of the Windows app), rebuilt for Android — see below.

Still only in the Windows app: skill renders with effects and Versus. They run on the author's private code (a Unity
player and the Versus modules, not in the public repository), so they cannot be rebuilt from it.

## Files (game files on the phone)

The 📁 button over the site (drag it aside; long-press hides it), or a long press on the app icon → **파일 탐색**.

- Connect a folder that holds the game's files: the mobile game's `Android/data/com.ProjectMoon.LimbusCompany`
  (Android 11–12 let you pick it; Android 13+ block it — copy the folder elsewhere with a PC or a Shizuku file
  manager first), or files copied from a PC install (`LimbusCompany_Data`, or the downloaded bundles in
  `%USERPROFILE%\AppData\LocalLow\Unity\ProjectMoon_LimbusCompany`). Single files can be opened too.
- Lists the bundles (Unity cache folders shown by their bundle's name), then every object in one with its type,
  name and container path; filter by type, search.
- Preview and save: Texture2D and Sprite → PNG (sprites cut from their texture or SpriteAtlas, also from another
  bundle), TextAsset → its text / JSON / bytes, VideoClip → MP4, Font → TTF/OTF, AudioClip → WAV for PCM sounds
  (Vorbis ones are saved as the raw FSB5 file), anything else → its fields as JSON; and any object's raw bytes.
  **Save all** exports what the list shows. Files go to `Downloads/LimbusArchive/Files/<bundle>`.

How it is done: upstream reads the files with UnityPy (Python). Here the same formats are read in Java
(`app/src/main/java/.../unity/`, ported from UnityPy): UnityFS bundles (LZ4, LZMA), serialized files v5–v26 (tested on v17–v22) with
or without type trees (built-in type trees for Unity 6000.3, the game's version, in `assets/typetrees.json.gz`, made
from UnityPy's type tree package). Compressed textures (ASTC, ETC/ETC2/EAC, DXT/BC1–7, PVRTC, ATC, crunch) are
decoded by K0lb3's MIT [texture2ddecoder](https://github.com/K0lb3/texture2ddecoder), built with the NDK
(`app/src/main/cpp`). Checked against UnityPy on its sample bundles (Unity 2017–2018, re-saved as serialized
v19/21/22, LZ4/LZMA/none, bundle format 6/7): the same objects, names and container paths, and the same pixels
(ASTC within 1/255).

Not yet: masking tightly packed sprites to their outline (they come out as their rectangle), Vorbis → OGG,
meshes as OBJ, FMOD `.bank` files.

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

Needs JDK 17 and the Android SDK (platform 35) with the NDK and CMake:

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
