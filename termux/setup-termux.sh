#!/data/data/com.termux/files/usr/bin/bash
# Run INSIDE Termux (once). Enables TermOS -> Termux commands and installs Ubuntu.
set -e
mkdir -p ~/.termux
grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null \
  || echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings 2>/dev/null || true
pkg update -y
pkg install -y proot-distro
proot-distro install ubuntu || true
echo "Done. Fully close and reopen Termux, grant 'Display over other apps', then in TermOS run: linux"
