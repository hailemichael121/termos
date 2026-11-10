# TermOS: vision, scope and roadmap

## Idea in one line
A keyboard-first, text-first phone shell: every action on the phone is a command you can type, pipe,
script, alias and delegate to an AI that proposes commands which you confirm.

## Principles
1. **Text is the interface.** Anything the GUI can do should be a command; output is plain text you can pipe.
2. **Safe by default.** No root-like action without the password; AI never executes silently.
3. **Scriptable.** Aliases today, scripts and schedules next, then automation across apps.
4. **Private and offline-first.** Only `browse` and `ai` touch the network; nothing is uploaded otherwise.
5. **Honest about limits.** It is a launcher, not an OS: Android's kernel, lock screen and permission model still apply.

## Who it is for
- Power users and developers who want a pocket Linux workflow (with Termux/proot behind `linux`).
- Minimalists who want a distraction-free phone (text launcher + timers, todo, notes).
- Accessibility users: a linear text UI works well with screen readers and hardware keyboards.
- Non-English users: the shell can be localised (Amharic/Ge'ez UI strings and input are a candidate phase-1.0 goal).

## Architecture
```
 MainActivity (view only, attaches in onStart / detaches in onStop)
        |
     Session  (process-lifetime: output buffer, listeners, screen-off lock)
        |
      Shell   (modes: login / sudo / wizard / normal; job control; timers)
        |
 Parser -> Pipeline -> Command registry (single source of truth: help, TAB, cmds, AI allow-list)
        |
 Providers:  Apps | Browser(Jsoup) | AI(Gemini) | Linux bridge(Termux) | Tools | Storage | Settings intents
```
Rules that keep it healthy: pure logic (Parser, Filters, Calc, Completer, Plan, Lockout) has **no Android imports**
so it is JVM-unit-tested; Android code is thin; the Registry drives help, completion, availability and AI safety.

## Roadmap
| Version | Theme | Highlights |
|---------|-------|-----------|
| 0.3 (this) | Polish + proof | pipes, aliases, history, wizard, lockout, leak-safe Session, 60 unit + 20 acceptance tests |
| 0.4 | Awareness | `notifs` (NotificationListener): list/clear/reply; `contacts`, `sms`, `call` with runtime permissions; battery/network alerts |
| 0.5 | Automation | `.tsh` scripts (variables, `&&`, `\|\|`, if), `every`/`at` via WorkManager, shareable script packs |
| 0.6 | Agent v2 | Gemini function-calling over the Registry schema, multi-step plans with per-step confirm, short-term memory, undo where possible |
| 0.7 | Cross-app actions | Accessibility service: read screen, tap, copy/paste between apps (least privilege, per-app allow-list, visible indicator) |
| 0.8 | Files + sharing | `cd/ls/cat/cp/mv` over Storage Access Framework and Termux home; `share`; `open-with` |
| 0.9 | Hardening | Keystore-backed AI key, biometric unlock option, optional device-owner kiosk mode for managed phones, crash-free-sessions gate |
| 1.0 | Release | F-Droid + Play (policy review for launcher/accessibility), i18n (Amharic), TalkBack audit, docs site |

## Security model (threat -> mitigation)
| Threat | Mitigation | Status |
|--------|-----------|--------|
| Shoulder-surf / guess password | PBKDF2-SHA256 120k + salt, escalating lockout 5/15/30/60s | done |
| Walk-up access after screen-off | Process-wide screen-off receiver locks even if UI is gone | done |
| AI proposes something destructive | Plan allow-list, no root/linux/uninstall, user confirm, max 6 steps | done |
| Key leakage in history/screen | `ai key` masked in echo + history | done |
| API key at rest | App-private prefs today; Android Keystore next | 0.9 |
| Notification/quick-settings bypass while locked | Not controllable by an app; documented; kiosk mode planned | 0.9 |
| Accessibility abuse | Opt-in, allow-list, always-on indicator | 0.7 |

## Quality gates
- `./check.sh` (unit + lint) green on every commit; `./e2e.sh` green before each release; `./stress.sh` clean.
- Budgets: cold start < 600 ms, command echo < 50 ms, idle heap < 80 MB, no leak across 50 recreations.
- Every new command ships with: Registry entry (usage + example), unit tests for its logic, an acceptance row if user-visible.

## Risks and open questions
- Play Store policy for launchers using QUERY_ALL_PACKAGES / Accessibility (use the `<queries>` form now; justify accessibility per policy).
- OEM battery killers can kill the process: timers and screen-off lock rely on the process being alive (a foreground service is a later option).
- Termux's RUN_COMMAND needs the user to enable `allow-external-apps`; keep the guided setup short.

## Status update: 0.4.0
Shipped: setup prompts (default launcher, lock mode, battery, notifications, alarms, overlay), keep-alive service,
alarm-based timers, boot re-arming, notifications/contacts/SMS commands, scripting (.tsh) and scheduling (at/every/job).
Still open: Keystore-backed AI key, Accessibility cross-app actions, files (SAF), Amharic UI, store policy review for SMS.
Design rule kept: anything that runs unattended (jobs) is limited to a small allow-list of background-safe commands.
