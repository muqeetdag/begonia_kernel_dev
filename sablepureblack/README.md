# SablePureBlack

**by [@drkmuqeett](https://github.com/muqeetdag)**

A production-grade Android Xposed / LSPosed module that forces **true pitch-black `#000000` AMOLED
panels** and **custom dark palettes** on any user-selected app, at runtime, without ever touching
screen brightness or applying a SurfaceFlinger colour filter.

```
Name        SablePureBlack
Package     com.drkmuqeett.pureblack
SDK         minSdk 26 · targetSdk 34 · Java 17
Framework   Xposed API 82 (compileOnly) · LSPosed / Zygisk Vector / EdXposed
Build       Gradle Kotlin DSL · AGP 8.2.2 · Kotlin 1.9.22 · Gradle 8.2
UI          Material 3 (#000000), single flat RecyclerView + DiffUtil, real spring physics
```

---

## What it does

### PART 1 — hook engine (`core/PureBlackHook.kt`)

| Layer | Hooks | Behaviour |
|---|---|---|
| **Live IPC config** | `XSharedPreferences` via the `xposedsharedprefs` manifest bridge | Palette, toggles and per-app scope are read from the same preference file the UI writes. `hasFileChanged()` + `reload()` are polled on every window attach, so a change lands **without restarting the hooked app**. Falls back to a direct `SharedPreferences` read, then to defaults — never crashes the host |
| **Window / DecorView** | `Activity.onCreate`, `Activity.onResume`, `Dialog.show` | `window.setBackgroundDrawable(ColorDrawable(BLACK))`, `statusBarColor = BLACK`, `navigationBarColor = BLACK`, `decorView.isForceDarkAllowed = true` |
| **Luminance + alpha engine** | `View.setBackgroundColor`, `View.setBackground(Drawable)`, `View.setBackgroundResource` | Light surfaces (**luma > 45**) become the palette colour. **alpha < 40 stays untouched** so icons, strokes and images survive. Source alpha is **scaled, not discarded** — a 60 % white card becomes a 60 % black card, never an opaque slab. Resource verdicts are cached per resource id |
| **WebView + text** | `WebSettings.setForceDark`, `WebView.onAttachedToWindow`, `TextView.setTextColor` | `FORCE_DARK_ON` on every WebView (page opt-outs are overridden); text below **luma < 90** is lifted to `#EAEAEA` |
| **Recursive sweep** | `decorView.post` + 350 ms late pass | Catches async content, RecyclerView rebinds and dialogs that appear after `onCreate` |

**Zero hardware interference.** `Settings.System.SCREEN_BRIGHTNESS`, `SCREEN_BRIGHTNESS_MODE`,
`WindowManager.LayoutParams.screenBrightness`, SurfaceFlinger colour matrices and display
calibration are never read, written or hooked. The only system-bar properties touched are
`statusBarColor` / `navigationBarColor` inside the hooked app's own window.

### PART 2 — companion UI (`MainActivity.kt` + `ui/`)

* **True `#000000` theme**, `#121212` elevated cards, crisp white/grey typography.
* **Header + live probe** — module title, `by @drkmuqeett` badge, and a real `Active` /
  `Not Activated in LSPosed` pill fed by actual class-loader injection plus a separate **IPC bridge**
  indicator. No root, no `su`, no guessing.
* **Per-app selector with search** — bottom sheet listing every launchable app, filtered live over a
  pre-built lowercase haystack (no `PackageManager` traffic while typing), with a "only selected
  apps" switch that writes to the same config the payload reads.
* **Palette switcher** — Pitch Black (`#000000`), Deep Obsidian (`#0A0A0A`), Custom Accent Dark
  (your accent, clamped to an AMOLED-safe band) with six accent seeds.
* **Toggles** — WebView force-dark, smart text contrast, and a forced-surface opacity slider.
* **Deep link** — one-tap jump into LSPosed's manager (with App-info fallback).
* **Spring animations** — genuine `SpringAnimation`/`SpringForce` on render-thread properties only
  (alpha, scale), so every interaction is a zero-layout-pass, 60fps+ operation.

---

## Install

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

1. Open **LSPosed Manager** (or Zygisk Vector) → **Modules** → enable **SablePureBlack**.
2. Tap **Scope** and tick the apps you want. Include `SablePureBlack` itself if you want the header
   pill to turn green.
3. **Force-stop** each ticked app (App info → Force stop) so hooks attach on its next launch.
4. Reopen it — light surfaces become your chosen palette instantly.

The in-app **Per-app scope** sheet writes the same allow-list, which the payload honours when
"Only selected apps" is on.

> Release builds are R8-minified and resource-shrunk. `proguard-rules.pro` pins the entry point named
> in `assets/xposed_init` (`com.drkmuqeett.pureblack.core.PureBlackHook`), so shrinking can never
> break hook loading. Signing uses `signing.properties` when present, otherwise an auto-generated
> debug keystore so `assembleRelease` always emits an installable APK.

---

## Project layout

```
sablepureblack/
├── settings.gradle.kts · build.gradle.kts · gradle.properties
├── gradlew · gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├── docs/{HOOK_BEHAVIOUR.md,ui-preview.png}
└── app/
    ├── build.gradle.kts · proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml · assets/xposed_init
        ├── java/com/drkmuqeett/pureblack/
        │   ├── SableApplication.kt      prefs seeding
        │   ├── MainActivity.kt          companion screen
        │   ├── config/  PureBlackPrefs, SableSettings, PaletteMath
        │   ├── core/    PureBlackHook, Probe, Locales
        │   ├── apps/    AppRepository, ScopeStore
        │   └── ui/      SableAdapter, AppScopeAdapter, ScopeSheet, Springs, UiState
        └── res/  layout(12) · drawable(15) · values(5) · mipmap · xml
```

See [`docs/HOOK_BEHAVIOUR.md`](docs/HOOK_BEHAVIOUR.md) for the exact decision tables and the
preference contract, and [`docs/ui-preview.png`](docs/ui-preview.png) for a geometry-accurate mockup of
the companion screen plus the scope sheet (rendered from the real `res/layout` dimensions, not a device
screenshot).

---

## Licence

Provided as-is for personal and educational use. The credit **by @drkmuqeett** must be retained in
any redistribution.
