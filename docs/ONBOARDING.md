# TermOS — onboarding for humans & other assistants

**Read this file first** if you have no access to the repo (e.g. Claude in browser). It mirrors the “state understanding” from the original build chats: vision, scripts, version history, failures, and how to continue.

**Author / machine:** Yihun, Ubuntu PC.  
**Live project:** `~/Desktop/termos` (symlink or copy may also exist at `~/Projects/termos`).  
**Check version on disk:** `grep VERSION app/src/main/java/dev/termos/Registry.kt` — as of last Cursor session this was **`0.8.0`**.  
**Package:** `dev.termos` · **APK:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 1. Original product idea (unchanged)

TermOS is an **Android launcher** that feels like a **minimal Linux / terminal OS**:

- Green-on-black (and themes); prompt like `user@termos:~$`
- Open apps with **`ls`** / **`open`**, browse with **`browse`**, AI with **`ai`** (Gemini)
- **Sudo password** for TermOS (not the phone PIN) — terminal locks on screen-off
- **Vim-style keys** optional (`vim on`)
- **Real Linux** is not inside the APK: command **`linux`** hands off to **Termux + proot-distro** (Ubuntu)
- Roadmap items still open: Accessibility automation, AI that runs commands after confirm, full nvim-modal everywhere, encrypted API key

**Honest limits (tell users):**

| Limit | Why |
|--------|-----|
| Not a real shell | Custom command set in `Shell.kt`; bash only via Termux |
| Android lock screen | App cannot turn off system PIN; user sets **Screen lock → None** (`lockscreen`, `perms lock`) |
| Copy/tap in other apps | Needs Accessibility service (not shipped yet) |
| Battery killers (Tecno/Xiaomi…) | Timers/auto-lock need **Unrestricted battery** + often **Autostart** (`perms battery`, `Oem.tip`) |
| Termux / Gemini | Must be verified on a real phone |

---

## 2. What exists on the phone today (feature stack)

Built incrementally; all commands are in **`Registry.kt`**, help text in **`Guide`**.

| Layer | Version | Features |
|--------|---------|----------|
| Core launcher | setup / 0.2 | HOME app, password, themes (10), vim mode, text browser, apps, sudo, calc, todo, timer, neofetch, Gemini |
| Linux bridge | 0.2+ | `linux`, `linux setup`, `linux run/exec` → Termux |
| Scripts & jobs | 0.3–0.4 | `.tsh` scripts, `at` / `every` / `job`, pipes, aliases |
| Comms | 0.4 | `notifs`, `contacts`, `sms`, `call` |
| Setup & reliability | 0.5 | Prompts on every open (launcher, lock, battery, notifs, alarms, overlay), `keepalive`, `perms`, redact `ai key` |
| Look & feel | **0.6** | `look`, `banner`, per-role colours, lock/login timestamps, autofocus, `logout` |
| UI polish | 0.7 | Custom setup dialogs (`TermDialog`), lock HUD (big clock/battery), emblem app icon, `SettingsNav` for Screen lock |
| Keyboard & UX | **0.8** | Built-in keyboard (`TermKeyboard`), Amharic layer (`AmharicLayout`), FAB swipe-up, formatted **`help`** (`HelpFmt`) |

**Docs in repo:** `docs/USER_GUIDE.md`, `docs/ACCEPTANCE.md`, `docs/VISION.md` (roadmap labels differ from release numbers — e.g. “0.6” in vision = AI agent, **shipped 0.6 = appearance**).

---

## 3. Ubuntu dev environment

First-time **`termos-setup.sh`** (in `~/Downloads` from chat) was designed to:

- Install JDK 17, `adb`, Android SDK platform 34, Gradle wrapper
- Set `ANDROID_HOME` in `~/.bashrc`
- Generate project under `~/Projects/termos` then updates moved it to **Desktop**

**Daily commands:**

```bash
cd ~/Desktop/termos
./test.sh                    # unit tests
./build.sh                   # debug APK
./install.sh                 # adb install -r + HOME hint
./gradlew testDebugUnitTest assembleDebug
```

**Phone USB:**

```bash
adb kill-server && adb start-server && adb devices
# unauthorized → unlock phone, tap Allow USB debugging
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.termos/.MainActivity
```

No USB: copy APK (e.g. Telegram Saved Messages), allow unknown sources.

---

## 4. Update scripts (Downloads folder)

Scripts are **bash patchers**: backup → python/sed patches → unit tests → `assembleDebug` → optional `adb install -r`.

| Script | From → To | Notes |
|--------|-----------|--------|
| `termos-setup.sh` | nothing → 0.2 base | Full project creation |
| `termos-update.sh` | early → 0.3+ | Themes, vim, linux, guides |
| `termos-update-0.3.sh` | → 0.3 | Scripts, automation; **`--move-to-desktop`** |
| `termos-update-0.4.sh` | → 0.4 | Notifs, SMS, contacts; multi-part `p2.sh`…`p8.sh` |
| `termos-update-0_5.sh` | → 0.5 | Setup policy, redact fix |
| `termos-update-0_6.sh` | → 0.6 | On Desktop tree: rebuild if already patched |
| *(no 0.7 script yet)* | → 0.7 | Applied **directly in repo** in Cursor |

**Common flags:** `--install`, `--skip-tests`, `--no-build`, project path as last arg.

**Backups:** `.backups/pre-X.Y.Z-<timestamp>/` under project root.

**Restore example:**

```bash
cp -r .backups/pre-0.5.0-XXXX/src/* app/src/
cp .backups/pre-0.5.0-XXXX/build.gradle.kts app/
```

---

## 5. Version history & incidents (from your logs)

| Event | Symptom | Cause / fix |
|--------|---------|-------------|
| **0.3 update** | `Shell.kt:180` `when` must be exhaustive | `when` last expr inside `let { }` — fixed in **0.4** patch |
| **0.4 update** | 72 tests, **1 failed** | Unit test / redact related — addressed in **0.5** |
| **0.5 update** | `RedactTest` / `ai key` | `Parser.redact` — always show `ai key ***` |
| **0.5 build** | `AcceptanceTest` `Unresolved reference: onView` | Missing Espresso import — fixed in **0.6** |
| **0.5 install** | No phone detected | adb cable / authorization, not a code bug |
| **SDK warning** | “SDK XML version 4” | Harmless during setup |

---

## 6. TermOS **0.6.0** — what that release specifically added

*This is the slice you asked to resume from; 0.7 builds on top.*

### New Kotlin files

- `Style.kt` — colours, block letters (`Banner`), date/title formatting
- `Look.kt` — persisted look settings, banner header, stamps
- `LookCmd.kt` — `look` and `banner` commands
- `StyleTest.kt`, `LookTest.kt` (acceptance)

### Patched files

- `Config.kt` — `autofocus()`
- `Registry.kt` — `look`, `banner`, `logout` alias, guide page `look`, version **0.6.0**
- `Core.kt`, `Modules.kt`, `Shell.kt`, `MainActivity.kt` — see `docs/HANDOFF_0.6.md` section “Files patched”
- `AcceptanceTest.kt` — Espresso `onView` import

### User commands (0.6)

```text
look                          # appearance panel
look color user #ff8800
look style input bold
look title {user} OS {version}
look date yyyy-MM-dd HH:mm
banner YIHUN
look fill #
look sub tagline
look autofocus on|off
look preview | look reset
logout                        # same as lock + timestamps
guide look
```

### Behaviour

- **Start / lock / login** show banner + **date lines** (and last login / last session when applicable)
- **Autofocus:** keyboard + cursor on open (default on)
- **Top bar:** `Look.title()` instead of hardcoded `TermOS vX`

### Gradle

- `versionCode 6`, `versionName "0.6.0"`

---

## 7. TermOS **0.7.0** — applied after 0.6 (current Desktop tree)

If `Registry.kt` says **0.7.0**, you also have:

| Piece | File |
|--------|------|
| Terminal-styled setup dialogs | `TermDialog.kt`, `res/layout/dialog_termos.xml` |
| Screen lock intent chain | `SettingsNav.kt` — not only `ACTION_SECURITY_SETTINGS` |
| Lock screen HUD (clock/battery center) | `activity_main.xml` `lockHud`, `MainActivity.renderLockHud()` |
| App icon | `res/drawable-nodpi/ic_emblem.png` from `Minimalist Terminal T Emblem.png` |
| Lock UI commands | `look lock terminal\|center\|minimal\|emblem`, `look locksize 24-72` |

See `docs/USER_GUIDE.md` → **0.7 changes**.

---

## 7b. TermOS **0.8.0** — keyboard + help (latest UX)

| Piece | File / command |
|--------|----------------|
| In-app keyboard (default) | `TermKeyboard.kt`, `config keyboard term\|system` |
| Bigger keys (~44dp), suggestions | `WordCache.kt` |
| Amharic syllable page | `AmharicLayout.kt` — cycle **?123 → አማ → abc**; **ለ›** (family name + ›) cycles consonant rows |
| FAB | Tap or **swipe up** on emblem (bottom-right) to open; **swipe down** on keyboard to collapse |
| Readable `help` / `cmds` | `HelpFmt.kt` — wrapped columns, category order, `help ls` detail view |

---

## 8. Phone setup flow (first run)

1. Install APK → create **6+ char password**
2. Wizard: theme, vim, Gemini key (Enter = skip)
3. Accept setup dialogs (or **`perms`** / **`perms all`**):
   - Default **HOME** (Always TermOS)
   - **Screen lock → None** (TermOS password = only lock)
   - **Unrestricted battery**, notifications, exact alarms, **overlay** (lock over apps)
4. Optional: Termux F-Droid → `termux/setup-termux.sh` → `linux setup`
5. `sudo ai key <KEY>` for Gemini

**Tecno / Infinix:** follow extra autostart steps from `perms battery`.

---

## 9. Source architecture (where to edit what)

```
app/src/main/java/dev/termos/
  MainActivity.kt    UI, rendering, vim, lock HUD, autofocus
  Shell.kt           Command execution, login/sudo/lock, MOTD
  Registry.kt        VERSION, all commands, Guide pages
  Modules.kt         Timers, perms, scripts, sms, look dispatch
  Setup.kt           SetupPrompter, permission definitions
  SettingsNav.kt     (0.7) settings intents
  TermDialog.kt      (0.7) custom dialogs
  Style.kt Look.kt LookCmd.kt   (0.6) appearance
  TermKeyboard.kt AmharicLayout.kt HelpFmt.kt WordCache.kt   (0.8)
  Script.kt          .tsh language + Oem tips
  Linux.kt Ai.kt Browser.kt …
app/src/test/        JVM unit tests (Parser, Plan, Style, …)
app/src/androidTest/ On-device acceptance (TermTest base)
```

**Single source of truth for commands:** `Registry.all` → drives `help`, `cmds`, TAB complete, AI `Plan.allowed`.

---

## 10. Assets in repo root

| File | Use |
|------|-----|
| `Minimalist Terminal T Emblem.png` | Source for launcher icon (`ic_emblem`) |
| `Neon Android Terminal Emblem.png` | Alternate art (not wired by default) |

---

## 11. What to do next (roadmap ideas from chats)

Not done yet; good next tasks for a new session:

- `termos-update-0_7.sh` idempotent patch script (like 0.5/0.6)
- Custom confirm dialog for SMS / `ai do` plans (not only setup)
- Accessibility service for copy/tap in other apps
- AI confirm-then-run with Registry function-calling
- Keystore for Gemini key
- Full Ge'ez keyboard / Amharic UI strings (0.8 has syllable layer only)

---

## 12. Paste this into another Claude (browser) session

```text
You are helping with TermOS — an Android Kotlin terminal launcher (package dev.termos).
Project on disk: ~/Desktop/termos. Read docs/ONBOARDING.md and docs/HANDOFF_0.6.md for full context.

Vision: terminal-first HOME launcher, sudo password, themes, vim mode, browse, Gemini ai,
Termux linux bridge, scripts (.tsh), notifs/sms/contacts (0.4), setup prompts (0.5).
0.6 added look/banner, role colours, lock/login timestamps, autofocus, logout alias.
0.7: TermDialog, SettingsNav, lock HUD, emblem icon.
0.8 (if VERSION is 0.8.0): TermKeyboard, AmharicLayout, HelpFmt, swipe FAB.

Scripts in ~/Downloads: termos-setup.sh, termos-update-0.3.sh … 0_5.sh, 0_6.sh (bash patch + gradle + adb).

Limits: not real Linux; cannot disable Android PIN in code; battery/OEM kills process.

User: Yihun, Ubuntu. Build: ./gradlew assembleDebug, install adb.

My task for this session: [describe what you want]
```

---

## 13. Related files

| Doc | Purpose |
|-----|---------|
| `docs/HANDOFF_0.6.md` | 0.6-only detail (files, tests, patches) |
| `docs/USER_GUIDE.md` | User-facing commands per version |
| `docs/ACCEPTANCE.md` | AC-xx test matrix |
| `README.md` | Short intro |

---

*Last expanded for cross-session handoff. Update `VERSION` in `Registry.kt` when you ship 0.8.*
