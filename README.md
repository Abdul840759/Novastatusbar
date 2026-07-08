# Nova Status Bar (Star Status Bar clone)

A from-scratch Kotlin/Android clone of "Star Status Bar: Custom Icons" — same
One UI–inspired look, extended with live system-state icons, real installed-app
notification badges, and lock-screen support. Original code throughout.

## Feature list

- **Clock, battery %** — real charging state (charging bolt vs plain outline),
  live percentage.
- **Fake transparency (not real transparency)** — the bar is **always
  opaque** and never lets the real system UI show through. On Android 11+ it
  samples the actual on-screen pixels just below the bar (via
  `AccessibilityService.takeScreenshot()`, the only public API that exposes
  rendered pixel colors) and smoothly animates the bar's solid background to
  match, once every 2.5s at most, only on app switches - not continuously,
  to protect battery on your Hot 10 Lite. Below Android 11, or whenever
  sampling fails (e.g. secure lock screens), it falls back to a fixed color
  you choose in settings.
- **Lock screen support** — the overlay window uses `FLAG_SHOW_WHEN_LOCKED`
  and recalculates its height against the real status bar dimen (including
  the landscape-specific one where available), so it should sit aligned with
  where the system bar would be, on both the lock screen and home/apps.
- **Landscape support** — bar height/width recompute on rotation.
- **Auto-hide in fullscreen/immersive apps** — heuristic based on the
  foreground app's window bounds (see caveat below).
- **Carrier name** — shows the real SIM operator name by default, or your own
  custom text if you set one in settings.
- **Network type (1G–5G)** — auto-detect from the real connection (needs the
  Phone permission, requested on first launch) with a manual override you can
  flip to any generation any time.
- **Live system-state icons** — appear only while the feature is actually on,
  same as the real status bar: mobile data, Bluetooth, hotspot, airplane mode,
  ringer mode (silent/vibrate), on-call, Do Not Disturb, power saver, NFC,
  location, VPN, next alarm set.
- **Fake notification icons (max 5)** — since there's no NotificationListener
  permission involved, you pick up to 5 apps from a searchable picker
  (`Choose Notification Icons`) and their **real, currently-installed icons**
  are shown as static badges — no trademarked assets bundled by us, just
  referencing what's already on your phone (like a launcher does).
- **Battery/RAM conscious by design** — everything is broadcast/callback
  driven, nothing polls in a loop; the clock only ticks once a second if your
  chosen format includes seconds, otherwise once a minute.

## What's NOT possible (be aware before testing)

- **Screen-sharing/casting detection** — no public Android API exists for one
  app to detect that another app is mirroring the screen. Not implemented,
  can't be.
- **Real swipe-down notification shade interaction** — the bar is
  `FLAG_NOT_TOUCHABLE` on purpose, so your real notification shade/quick
  settings still open normally. Making it touchable would break that.
- **Hotspot detection** relies on an undocumented system broadcast
  (`android.net.wifi.WIFI_AP_STATE_CHANGED`) that AOSP and most OEMs fire, but
  XOS could customize it — test on your actual device.
- **Auto-hide heuristic** checks whether the foreground app's window covers
  the full screen from y=0. Some OEM skins (XOS included) may report window
  bounds slightly differently — if it doesn't trigger correctly, tell me and
  I'll tune the threshold.
- **Guaranteed background survival** — accessibility services get a real
  exemption from standard Doze/App Standby (Android treats them as
  system-bound), which helps a lot. But XOS has its own separate aggressive
  killer outside stock Android's control that no app can 100% override from
  code alone. Use the in-app "Ignore Battery Optimization" and "Autostart /
  Lock App Settings" buttons on first launch — the autostart one tries known
  Infinix/Transsion component names and falls back to the plain App Info
  screen if XOS has renamed them again.

## Getting an APK onto your phone — no Android Studio needed

Every push to `main` builds the APK and commits it to `releases/nova-status-bar.apk`
in this repo, so you get a **permanent direct-download link** — no zip, no
Releases page needed:

```
https://raw.githubusercontent.com/<your-username>/<repo-name>/main/releases/nova-status-bar.apk
```

Open that link straight in your phone's browser and it downloads the APK
directly. Every time you push a change and the build succeeds, that same link
always points at the latest build.

Alternatively:

```bash
cd StarStatusClone
git init
git add .
git commit -m "Nova Status Bar - initial build"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main
```

Then:

1. Go to your repo on GitHub → the **Actions** tab. You'll see the "Build APK"
   workflow running (takes a few minutes).
2. When it finishes, open the run → scroll to **Artifacts** → download
   `nova-status-bar-debug-apk` (this comes as a `.zip` — unzip it, e.g. with
   a file manager app or ZArchiver, to get `app-debug.apk`).
3. **Easier option:** tag a release instead, and get a direct, un-zipped APK:
   ```bash
   git tag v1.0
   git push origin v1.0
   ```
   Then check the **Releases** section of your repo on GitHub — the workflow
   attaches `app-debug.apk` directly there, downloadable straight to your
   phone's browser, no unzipping needed.
4. On your Infinix Hot 10 Lite: open the downloaded APK → Android will ask to
   enable "Install unknown apps" for your browser/file manager the first
   time → allow it → install.

## First-run setup on the phone

1. Open the app → tap **Grant Overlay Permission** → allow "Display over
   other apps".
2. Tap **Grant Accessibility Permission** → find "Nova Status Bar" in
   Accessibility settings → enable it.
3. Toggle **Enable Custom Status Bar** on.
4. Tap **Ignore Battery Optimization** and **Open Autostart / Lock App
   Settings** to give it the best shot at surviving XOS's background killer.
5. Set your carrier name, network type, colors, and pick your 5 notification
   icons.

## Project structure

- `StatusBarAccessibilityService.kt` — draws and drives the overlay bar.
- `SystemStateWatcher.kt` — detects live on/off state for every system
  toggle listed above.
- `StatusIconManager.kt` — adds/removes icons in the bar based on that state.
- `NetworkTypeHelper.kt` — real network-generation detection.
- `InstalledAppsRepository.kt` / `AppPickerAdapter.kt` /
  `NotificationIconPickerActivity.kt` — the searchable icon picker.
- `PrefsManager.kt` — shared settings storage read by both the activity and
  the service, with live-apply via a `SharedPreferences` listener.
- `MainActivity.kt` — the settings screen.

## Legal note

This is an original implementation of the *feature concept* (overlay status
bar styling), not decompiled or copied code/assets from the original Play
Store app — safe to build on and publish under your own branding. The
notification icons use your own installed apps' real icons at runtime rather
than bundling anyone's logos.
