# TermOS 0.2 user guide

In the app: `help`, `help <cmd>`, `guide <page>`, `cmds` (availability).

## First run
1. Create a password (terminal unlock + sudo).
2. Press Home -> TermOS -> Always.
3. `lockscreen` -> set Android screen lock to None so TermOS is the only lock.

## Commands
apps: `ls [filter]`, `open <name|n>`, `sudo uninstall <app>`
web: `browse <url|words>`, `go <n>`, `back`
ai: `sudo ai key <KEY>`, `ai <question>`, `ai model <name>`
linux: `linux`, `linux shell`, `linux setup`, `linux run <cmd>`, `linux exec <cmd>`
clipboard/notes: `copy`, `paste`, `note`, `notes [clear]`
system: `battery`, `neofetch`, `date`, `whoami`, `history`, `clear`, `echo`, `wifi`, `bt`, `call`, `sudo settings`
security: `lock`, `lockscreen`, `sudo`, `su`, `exit`, `sudo passwd`
look: `theme [name]`, `config [theme|vim|font|autolock|username]`, `vim on|off`

## Vim mode (`vim on`)
ESC -> NORMAL: `j k` line, `d u` half page, `gg G` top/bottom, `/text` search, `n N`, `i` insert.

## Linux (Termux + proot-distro)
Install Termux (F-Droid), run `termux/setup-termux.sh` inside it, reopen, then `linux`.

## Limits
Android's own lock screen can't be removed by an app; TermOS only replaces it when you set it to None.
TermOS itself is not a Linux kernel/userland; `linux` hands off to Termux's real Ubuntu.
