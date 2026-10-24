# MusicFeed

An Android app that plays your Instagram reels feed as **music only**: no video,
just audio, with an equalizer, background playback and next/previous controls.

## Features

- **Instagram login** on the real instagram.com page in a WebView. The app never
  sees your password; it reuses the session cookie.
- **Your personalised reels feed** (the Reels tab, which Instagram tailors to your
  interests and watch history), with Explore reels and home-feed videos as fallbacks.
- **Audio only**: video tracks are disabled. When Instagram provides a DASH
  manifest, only the audio stream is downloaded, which saves data and battery.
- **Highest available audio quality**: the player always picks the
  highest-bitrate audio stream.
- **Equalizer** with device presets and per-band sliders, plus bass boost,
  surround (virtualizer) and loudness. Settings are saved.
- **Play / pause / next / previous**, a seek bar and an "Up next" queue (tap to
  jump). The same controls appear in the notification, on the lock screen and on
  Bluetooth headsets.
- **Endless playback**: more reels load automatically as the queue runs low.
  Song title, artist and cover art come from the reel's music info.

## Getting the APK

Every push runs the **Android build** GitHub Action. Open the run under the
repo's *Actions* tab, download the `MusicFeed-debug-apk` artifact, unzip it and
install `app-debug.apk` on your phone (allow "install unknown apps").

Or build locally with Android Studio (or JDK 17 + Android SDK 35):

```
./gradlew assembleDebug
```

## Important caveats

- **No official API.** Instagram offers no public API for a user's reels feed,
  so the app calls the same private endpoints instagram.com uses
  (`ReelsApi.kt`). Instagram can change or block them at any time; if the feed
  stops loading, the endpoint list in `ReelsApi.kt` is the place to update.
- **Terms of Service.** Automated access to Instagram goes against its Terms of
  Use. Your account could be rate-limited, asked to verify, or restricted. Use it
  for personal listening at your own risk; this is not suitable for the Play Store.
- **"High quality" is capped by the source.** The app plays the best stream
  Instagram serves (usually AAC at roughly 64–128 kbps). It can't add quality
  that isn't there, but the equalizer helps shape the sound.
- Listening here probably doesn't count as a watch on Instagram, so it may not
  influence your recommendations the way watching does.

## Project layout

```
app/src/main/java/com/musicfeed/app/
  MainActivity.kt            screen switching (login → player ↔ equalizer)
  instagram/                 login session, feed client, JSON parsing
  playback/PlaybackService   Media3 ExoPlayer + MediaSession (background audio)
  playback/AudioEffects      equalizer, bass boost, virtualizer, loudness
  ui/                        Compose screens
```
