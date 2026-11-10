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
