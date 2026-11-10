# TermOS acceptance criteria (0.3.0)

Run on a device/emulator: `./e2e.sh` (or `./e2e.sh --emulator`). Unit-level logic: `./test.sh`.

| ID | Criterion (Given/When/Then, short) | Verified by |
|----|------------------------------------|-------------|
| AC-01 | Fresh install: user must create a 6+ char password, confirm it, then a setup wizard (theme, vim, AI key) completes | FirstBootTest |
| AC-02 | Locked terminal denies a wrong password and accepts the right one | AcceptanceTest.ac02 |
| AC-03 | After 3 wrong passwords, even the correct one is refused for a cool-down | ac03, UtilTest.lockoutPolicy |
| AC-04 | `help` lists every command group | ac04, RegistryTest |
| AC-05 | `cmd \| filter` pipes output through grep/head/tail/wc/sort/uniq | ac05, FiltersTest |
| AC-06 | `a; b` runs every statement, quotes protect `;` and `\|` | ac06, ParserTest |
| AC-07 | Aliases can be defined, resolved and listed; builtins cannot be shadowed | ac07, ParserTest |
| AC-08 | `theme x` re-colours the UI immediately and persists | ac08 |
| AC-09 | Vim option: ESC enters NORMAL, `i` returns to INSERT, status bar shows the mode | ac09 |
| AC-10 | `lock` re-locks immediately and hides the session | ac10 |
| AC-11 | Rotation / UI recreation never locks the user out or loses output | ac11 |
| AC-12 | Mistyped command suggests the nearest command | ac12, UtilTest |
| AC-13 | Root-only commands are refused without sudo; `sudo -k` clears the cached password | ac13 |
| AC-14 | To-do list add / done / list round-trips and persists | ac14 |
| AC-15 | `!!` repeats the previous command; API keys are masked in history/echo | ac15, ParserTest.redactsKeys |
| AC-16 | `cmds` shows what works and what still needs setup (key, Termux, permission) | ac16 |
| NF-01 | 8 UI recreations leave at most 1 old Activity reachable | LeakTest |
| NF-02 | Session holds no UI reference after onStop | LeakTest |
| NF-03 | 1500 random monkey events: no crash, no ANR, no Activity pile-up, PSS < 2x | `./stress.sh` |
| NF-04 | AI plans can never contain sudo/su/passwd/linux/uninstall/settings/lock/ai | UtilTest.plan* |
| NF-05 | Accessibility: every key-bar button, input and output has a content description | manual TalkBack pass |

Manual checks (not automatable here): becoming the default Home app; Termux `linux` flow on a real phone;
Gemini calls with a real key; screen-off auto-lock on a physical device.

## 0.4.0 additions

| ID | Criterion | Verified by |
|----|-----------|-------------|
| AC-17 | `perms` lists home, lock, battery, notifications, alarms, overlay, notifs, contacts, sms | AutomationTest.ac17 |
| AC-18 | Timers are alarm-based, listed with time left, and can be cancelled | ac18 |
| AC-19 | `at`/`every`/`job` schedule, list and remove jobs; only background-safe commands are accepted | ac19, ScheduleTest |
| AC-20 | Scripts: create with the line editor, variables, if/else, repeat, run with `tsh` | ac20, ScriptTest |
| AC-21 | Scripts cannot nest or use sudo/su/passwd | ac21 |
| AC-22 | `keepalive on/off` toggles the foreground service | ac22 |
| AC-23 | `notify` posts a notification (or explains permission is off) | ac23 |
| AC-24 | `notifs watch` toggles echoing without special access | ac24 |
| AC-25 | `contacts <filter>` reports no match cleanly | CommsTest.ac25 |
| AC-26 | `sms send` always asks y/n unless `-y`; `n` cancels | ac26 |
| AC-27 | `sms read <n>` refuses a missing message | ac27 |
| NF-06 | Notifications/timer/job text produced while locked is held and shown only after unlock | manual |
| NF-07 | After a reboot, scheduled jobs and pending timers are re-armed | manual (reboot the phone) |
| NF-08 | First open (setup prompts on): dialogs appear for default launcher, lock, battery, notifications, alarms, overlay; "Never ask" and "Later" are respected | manual |
| NF-09 | With overlay allowed, pressing the power button then waking shows TermOS's password prompt | manual |
| NF-10 | With keep-alive and battery exemption on, a 10-minute timer still fires after the app is swiped away | manual |

## 0.5.0 additions

| ID | Criterion | Verified by |
|----|-----------|-------------|
| NF-11 | Opening TermOS shows the next missing setup dialog; "Later" and tap-outside snooze it 1 hour | SetupPolicyTest + manual |
| NF-12 | `ai key` secrets never appear in history or the echoed prompt | RedactTest |

## 0.6.0 additions

| ID | Criterion | Verified by |
|----|-----------|-------------|
| AC-28 | `look` shows title, banner, date format, autofocus and the colour table | LookTest.ac28 |
| AC-29 | `banner <words>` draws block letters with the chosen fill | ac29, StyleTest |
| AC-30 | An invalid colour is rejected | ac30, StyleTest |
| AC-31 | `logout` locks and prints "logged out: <date time>" under the banner | ac31 |
| NF-13 | Opening the app puts the cursor in the terminal and raises the keyboard (autofocus on) | manual |
| NF-14 | Colours/styles for user, input, output, error, dim, banner, time apply to the screen immediately | manual |
