# Hook behaviour and decision tables — SablePureBlack

*by @drkmuqeett* · package `com.drkmuqeett.pureblack`

Every table below was produced by **executing** the shipped pure-Kotlin helpers
(`config/PaletteMath.kt` and the remap rules in `core/PureBlackHook.kt`) off-device — these are the
real outputs, not aspirational documentation.

---

## 1. Background override

Rule order: `alpha < 40` → keep · `luma <= 45` → keep · otherwise → palette colour with the source
alpha re-applied.

| Input | α | luma | Result |
|---|---:|---:|---|
| `FFFFFFFF` white | 255 | 255 | `#000000`, α 255 |
| `FFFAFAFA` Material light surface | 255 | 250 | `#000000`, α 255 |
| `FF808080` mid grey | 255 | 128 | `#000000`, α 255 |
| `FF4A4A4A` | 255 | 74 | `#000000`, α 255 |
| `FF2D2D2D` | 255 | 45 | **kept** (threshold is *strictly greater than* 45) |
| `FF121212` card | 255 | 18 | kept |
| `FF000000` black | 255 | 0 | kept |
| `99FFFFFF` white @ 60 % | 153 | 255 | `#000000`, **α 153** (translucent, not solid) |
| `1AFFFFFF` white @ 10 % | 26 | 255 | **kept** — alpha < 40 overlay |
| `00000000` transparent | 0 | 0 | kept |

`setBackground(Drawable)` rewrites **flat `ColorDrawable`s only**; bitmaps, vectors, gradients,
ripples and state lists are always preserved, which is what keeps imagery and iconography intact.

`setBackgroundResource(int)` resolves the drawable once and caches the verdict per resource id.

## 2. Forced-surface opacity

The opacity slider scales the *alpha* of forced surfaces, so translucency is preserved end to end:

| Slider | 60 % white card becomes |
|---|---|
| 100 % | `#000000` @ 60 % (α 153) |
| 75 % | `#000000` @ 44 % (α 114) |
| 50 % | `#000000` @ 29 % (α 76) |
| 25 % | `#000000` @ 14 % (α 37) |

## 3. Text elevation

| Text colour | luma | Result |
|---|---:|---|
| `#000000` | 0 | → `#EAEAEA` |
| `#212121` | 33 | → `#EAEAEA` |
| `#5A5A5A` | 90 | kept (threshold is *strictly less than* 90) |
| `#8A8A8A` | 138 | kept |
| translucent text (α < 64) | — | kept (designed disabled state) |

## 4. Custom Accent Dark derivation

`blend(#0A0A0A, seed, 12 %)` then each channel is clamped to `0x0A..0x16`, so a bright seed can never
lift the surface out of the AMOLED band:

| Accent seed | Derived surface | luma |
|---|---|---:|
| `#3DD68C` Sable green | `#101616` | 20 |
| `#5AA9FF` ice blue | `#131616` | 21 |
| `#B388FF` violet | `#161616` | 22 |
| `#FF8A65` ember | `#161614` | 21 |
| `#FFD166` amber | `#161615` | 21 |
| `#EAEAEA` neutral | `#161616` | 22 |

## 5. Per-app scope resolution

| State | `shouldProcess("com.x")` |
|---|---|
| Default (all scoped) | true — LSPosed already limits which processes load the module |
| Only selected: `{com.a}` | true for `com.a`, false for `com.b` |
| Only selected, empty set | true (an empty list must never silently disable the module) |
| Master switch off | false |

## 6. Preference contract

File `sablepureblack_config.xml`, written by the UI, read in every hooked process through
`XSharedPreferences` (manifest declares `xposedsharedprefs=true`).

| Key | Type | Default |
|---|---|---|
| `palette_id` | int | `0` = Pitch Black, `1` = Deep Obsidian, `2` = Custom Accent |
| `custom_accent_seed` | int | `0xFF3DD68C` |
| `webview_force_dark` | bool | `true` |
| `text_contrast_boost` | bool | `true` |
| `overlay_opacity_percent` | int | `100` |
| `module_enabled` | bool | `true` |
| `target_mode` | int | `0` (all scoped) / `1` (selected only) |
| `scoped_apps` | string set | empty |
| `install_stamp` | long | first launch |

## 7. Live update path

```
UI edit → SharedPreferences.commit/apply
        → (LSPosed shared-prefs bridge)
hooked process: window attach → hasFileChanged() → reload() → SableSettings.from()
        → new palette applied to the same window, no restart
```

Re-reads are throttled to one per 600 ms so the poll never competes with the frame budget, and an
in-flight animation is untouched because only palette values change.

## 8. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Header stays red | module disabled, or not scoped for itself | enable + tick `SablePureBlack` in Scope |
| Orange "IPC bridge unavailable" | framework injected but prefs bridge missing | re-save any setting and relaunch the manager |
| Target app unchanged | old process still running | force-stop it, then reopen |
| A few surfaces stay light | custom `Drawable` (bitmap/gradient) | expected: imagery is never destroyed |
| Disabled buttons look dim | app uses translucent text | turn off Smart text contrast boost |
