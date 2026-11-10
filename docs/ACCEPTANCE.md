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
