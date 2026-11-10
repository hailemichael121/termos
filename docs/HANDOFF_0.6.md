# TermOS 0.6.0 — handoff for continuing development

> **Start with [`ONBOARDING.md`](./ONBOARDING.md)** for the full picture: original idea, all `termos-update-*.sh` scripts, version history, failed builds you hit, phone setup, and a paste-block for another Claude session.

Use this file for **0.6-only** file/test detail. **Project path:** `~/Desktop/termos` (moved from `~/Projects/termos` during 0.3 update). **Package:** `dev.termos`.

**Note:** The Desktop copy may already include **0.7.0** on top of 0.6 (custom dialogs, lock HUD, app icon). This doc describes **0.6 only**; see `ONBOARDING.md` §7 and `USER_GUIDE.md` → “0.7 changes”.

---

## What TermOS is

Android **HOME launcher** that behaves like a **terminal OS** (not a real Linux shell). Kotlin, **plain Views** (no Compose). Commands live in `Shell.kt` + `Modules.kt`. Command list/docs in `Registry.kt` + `Guide`.

---

## Version line (0.5 → 0.6)

| Item | 0.5 | 0.6 |
|------|-----|-----|
| `Registry.kt` `VERSION` | `"0.5.0"` | `"0.6.0"` |
| `app/build.gradle.kts` | `versionCode 5`, `versionName "0.5.0"` | `6`, `"0.6.0"` |
| Update script | `termos-update-0_5.sh` | `termos-update-0_6.sh` (rebuild/install on already-patched tree) |

---

## New files added in 0.6

| File | Role |
|------|------|
| `Style.kt` | `TxtStyle`, `LookRole`, `ColorParse`, `Banner` (block letters), `LookFmt` (dates, title tokens, prompt split) |
| `Look.kt` | Read/write look settings from `Store`; `header()`, `stampLine()`, `signature()` for UI refresh |
| `LookCmd.kt` | Commands `look` and `banner` |
| `app/src/test/java/dev/termos/StyleTest.kt` | Unit tests for colours, banner, dates, commands |
| `app/src/androidTest/java/dev/termos/LookTest.kt` | AC-28..AC-31 |

---

## Files patched in 0.6

| File | Changes |
|------|---------|
| `Config.kt` | `autofocus(c)` — default **on** unless `Store "autofocus" == "off"` |
| `Registry.kt` | New commands `look`, `banner`; `lock` alias `logout`; `config` gains `autofocus`; new `guide` page `"look"`; version bump |
| `Core.kt` | Tab-completion subs for `look`, `banner`, `config autofocus` |
| `Modules.kt` | `dispatch`: `"look", "banner" -> LookCmd.run(...)` |
| `Shell.kt` | `start()`/`lock()` use `Look.header()` + time stamps; `loginStamp()` on unlock; `lock(reason)`; screen-off → `lock("screen off")`; config panel shows autofocus |
| `MainActivity.kt` | Per-role colours/styles (`roleColor`, `paint`, banner/time line prefixes); `focusInput()` + IME on open; `last_active` on `onStop`; title bar uses `Look.title()`; theme refresh includes `Look.signature()` |
| `AcceptanceTest.kt` | Import `androidx.test.espresso.Espresso.onView` (compile fix) |
| `RegistryTest.kt` | Version `0.6.0`; guide page `look`; commands `look`, `banner`, `logout` |
| `docs/USER_GUIDE.md` | Section “0.6 changes” |
| `docs/ACCEPTANCE.md` | AC-28..AC-31, NF-13, NF-14 |

`Shell.kt` still has `companion object { const val BANNER = ... }` — ASCII art used when `banner default`.

---

## User-facing features in 0.6

### `look` (appearance)

Like Ubuntu **Settings → Appearance**.

- `look` — panel: theme, title, banner, tagline, date format, autofocus, colour/style table per role
- `look color <role> <#rrggbb|name|reset>` — roles: `user input output error dim banner time`
- `look style <role> normal|bold|italic|bolditalic`
- `look title <text|reset>` — tokens `{version}`, `{user}` (top status bar text)
- `look fill <char|reset>` — character for block letters (default block █)
- `look sub <tagline|off>` — line under banner
- `look date <pattern|reset>` — Java `SimpleDateFormat` pattern
- `look autofocus on|off` or `config autofocus on|off`
- `look preview`, `look reset`

### `banner`

- `banner <words>` — block letters (max 24 chars), stored as custom banner on start/lock
- `banner default|off|plain`
- `banner text <words>` — if word would collide with `off`/`default`

### Time / lock UX

- **Start:** banner + `started: <date>` + optional `last session closed: …` from `last_active`
- **Lock / logout:** banner + `logged out: <date>` (or `screen off`) + “terminal locked.”
- **Login:** `login: …` + `last login: …` after password OK
- Markers: banner lines prefixed with U+200B (`Banner.MARK`), time lines with U+200C (`Banner.TMARK`) for colouring in `MainActivity.styled()`

### Autofocus

On resume / window focus: focus input; if autofocus on, show keyboard (`WindowInsetsCompat.Type.ime()`). Tap terminal output also focuses input.

---

## Commands registry (0.6 additions)

```text
look   — visual settings (see guide look)
banner — block-letter splash on start/lock
logout — alias for lock (with timestamp header)
```

---

## How to build / install (0.6 tree)

```bash
cd ~/Desktop/termos
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or: `chmod +x termos-update-0_6.sh && ./termos-update-0_6.sh --install`

---

## Tests (0.6)

| Suite | What |
|-------|------|
| `StyleTest` | `ColorParse`, `Banner.render`, `LookFmt`, `Registry.find("logout")`, completion |
| `LookTest` (device) | AC-28..AC-31 |
| Existing | 0.5: `SetupPolicyTest`, `RedactTest` (ai key redaction), 0.4 scripts/comms |

**0.5 fix** that blocked 0.4/0.5 builds: `UtilTest` / `Parser.redact` — `ai key` always → `***`.

**0.5 fix:** setup dialogs every open (not once/day); `SetupPolicy` 1h snooze.

---

## What 0.6 did *not* change

- Still **not** a real shell (Termux bridge: `linux` command unchanged).
- Cannot remove Android system lock in code — `lockscreen` / `perms lock` still open settings.
- SMS/plan confirm still **terminal** y/n, not custom UI (0.7 changed setup dialogs only).
- Notifications, contacts, SMS, scripts, timers (0.4 layer) unchanged in 0.6 except docs.

---

## Prior versions (context)

| Ver | Highlights |
|-----|------------|
| 0.3 | Scripts/automation, compile fix for `Shell.kt` `when` in lambda |
| 0.4 | Notifs, contacts, SMS, call; patch scripts p2–p8 |
| 0.5 | Setup prompts on every open, redact test, `termos-update-0_5.sh` |
| **0.6** | **look/banner, colours, dates on lock/login, autofocus, logout alias** |

---

## Suggested prompts for another Claude session

Use **`ONBOARDING.md` §12** (full paste block). Short form:

- “Read `~/Desktop/termos/docs/ONBOARDING.md` + this file. TermOS 0.6 = look/banner. Task: …”

---

## Key architecture (quick map)

```
MainActivity.kt     — UI, themes, vim keys, look rendering
Shell.kt            — modes SET1/SET2/LOGIN/SUDO/NORMAL, execute(), lock/start
Registry.kt         — VERSION, commands, Guide pages
Modules.kt          — timer, perms, script, sms, look/banner dispatch
Setup.kt + Policy   — first-run permission prompts
Store               — key/value prefs (in Core or separate)
Session.kt          — holds Shell + output listeners
```

Gradle: JDK 17, `compileSdk 34`, `minSdk 26`, Jsoup for browser.

---

*Generated for handoff; align with `const val VERSION` in `Registry.kt` on your machine.*
