# TermOS 0.3 user guide

In the app: `help`, `help <cmd>`, `guide <page>` (start, lock, linux, vim, themes, ai, pipes, tools), `cmds`.

## First run
Create a 6+ character password -> quick wizard (theme, vim, Gemini key) -> Home -> TermOS -> Always -> `lockscreen`.

## Everyday
| Do | Type |
|----|------|
| List / open apps | `ls`, `ls chrome`, `open 3`, `open maps` |
| Search the web | `browse kotlin coroutines`, `go 2`, `back` |
| Ask AI | `ai explain pipes`, `ai do turn on torch` (confirm with y) |
| Calculate | `calc (2+3)*4^2` |
| Timers / todo / notes | `timer 25m pomodoro`, `todo add milk`, `note idea` |
| Real Linux | `linux`, `linux exec uname -a` (needs Termux, see `guide linux`) |

## Power features
- Pipes: `ls | grep chrome | head 3` (grep, head, tail, wc, sort, uniq)
- Chaining: `battery; date`   Aliases: `alias w='browse weather'`   History: `!!`, `!3`, `!op`
- TAB completes commands, app names, themes, sub-options; ^C cancels; `|` key inserts a pipe.
- Vim mode (`vim on`): ESC, `j k d u gg G`, `/search`, `n N`, `i`.

## Security
Locks on screen-off, 3 wrong passwords add a growing wait, `sudo` remembers your password 5 minutes (`sudo -k` forgets).
Android's own lock screen cannot be removed by an app; set it to None if you want TermOS as the only lock.

## For developers
`./test.sh` unit | `./check.sh` unit+lint | `./e2e.sh [--emulator]` acceptance | `./stress.sh` monkey+memory | docs/ACCEPTANCE.md

## 0.4 additions
- **Setup prompts** on open: default launcher, Android lock -> None, unrestricted battery, notifications, exact alarms, lock over apps. `perms` shows status, `perms <name>` fixes one.
- **Reliability:** `keepalive on` (default) + unrestricted battery keep timers, jobs and the auto-lock alive. Timers and jobs use the system alarm clock and survive reboot.
- **Messages:** `notifs`, `contacts`, `sms`, `call <name>`.
- **Scripts:** `script new morning` ... `.` then `tsh morning`. `at 07:30 tsh morning`, `every 2h notify drink water`, `job`.
- See `guide scripts`, `guide comms`, `guide setup`.

## 0.5 changes
- **Setup prompts on every open** until done: default launcher (system picker), Android lock -> None, unrestricted battery, notifications, exact alarms, lock over apps. "Later" (or tapping outside) asks again in 1 hour; "Never ask" / "Keep both locks" stops that item. `perms` shows what is missing, `perms <name>` fixes one.
- Fixed: `ai key` redaction in history/echo now always prints `ai key ***`.

## 0.6 changes
- **`look`** (like Ubuntu Settings > Appearance): `look` shows everything. `look color user #ff8800`, `look style input bold`, roles: user input output error dim banner time.
- **`banner YIHUN`**: your words become block letters on the start/lock screen. `banner default|off|plain`, `look fill #` (letter symbol), `look sub <tagline>`.
- **Top bar text**: `look title {user} OS {version}` (tokens {version} {user}), `look title reset`.
- **Date and time** appear under the banner (or your custom text) when the terminal starts, when it locks (screen off or `logout`) and at login, with your last login. Format: `look date yyyy-MM-dd HH:mm`.
- **Autofocus**: opening TermOS puts the cursor in the terminal and raises the keyboard. `look autofocus off` turns it off.
- `logout` = `lock`. `look preview` draws a sample, `look reset` restores defaults. See `guide look`.

## 0.8 changes
- **Built-in keyboard** (default): QWERTY + symbols (⇧ / ?123), shortcut chips (`ls`, `sudo`, `|`, …), word suggestions from history.
- **Collapse** with ⌄ on the keyboard; **floating emblem** bottom-right reopens it. On **lock**, keyboard auto-collapses; tap emblem to type password.
- **`config keyboard system`** — use the phone's normal keyboard again (`term` = built-in).
- Layer cycle on keyboard: **?123** (symbols) → **አማ** (Amharic families, **ለ›** cycles family) → **abc**. **Swipe up** on emblem FAB opens keyboard; **swipe down** on keyboard collapses. Taller keys (~44dp).
- Terminal **output** lines use theme `look` **output** colour on every theme (not only prompts/errors).

## 0.7 changes
- **Custom setup dialogs** (launcher, lock, battery, …) use the TermOS terminal theme and emblem — not Android's default popups.
- **`lockscreen` / `perms lock`** opens **Screen lock** directly when the OS allows it (not only the generic Security screen).
- **App icon** uses `Minimalist Terminal T Emblem.png` from the project root.
- **Lock screen HUD** (default **center**): large centered clock + battery (+ emblem). Styles: `look lock terminal|center|minimal|emblem`; size: `look locksize 24-72`.
