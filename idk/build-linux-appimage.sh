#!/usr/bin/env bash
set -euo pipefail

echo "========================================================"
echo " [hifi-tui] Building Linux AppImage (x86_64)..."
echo "========================================================"

if ! command -v npm >/dev/null 2>&1; then
  echo "[ERR] npm is not installed. Please install Node.js (v18+)."
  exit 1
fi

npm install
npx electron-builder --linux AppImage

chmod +x dist/*.AppImage 2>/dev/null || true
echo ""
echo "[OK] Linux AppImage built successfully:"
ls -lh dist/*.AppImage
