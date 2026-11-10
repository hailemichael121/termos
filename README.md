# TermOS

Terminal-style Android launcher: apps, text browser, Gemini assistant, clipboard,
notes, password lock + sudo, all from a command line.

**Handoff for another machine or assistant:** [`docs/ONBOARDING.md`](docs/ONBOARDING.md) (vision, scripts, 0.3–0.7 history, paste-block).

## Project site
Static about page (liquid-glass layout, Fidel-style): open `site/index.html` in a browser or serve the `site/` folder.

## Run
./build.sh        # build debug APK
./install.sh      # build + adb install + try set as home

## First use
1. Create password on first boot (this is the terminal password + sudo password).
2. `sudo ai key <GEMINI_API_KEY>` then `ai how do I ...`
3. Press Home, choose TermOS, "Always".

## Roadmap
- Termux/proot-distro bridge for a real Linux shell (`linux` command)
- vim-style modal keys (j/k scroll, / search, :cmd)
- AI function-calling: assistant proposes a command, you confirm with `y`
- Encrypted key storage (security-crypto), biometric option
- Accessibility service for copy/paste/tap actions inside other apps
