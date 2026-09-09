#!/usr/bin/env bash
# Downloads recommended open GGUF models for JARVIS into the current directory.
# Then:  adb push <file>.gguf /sdcard/Android/data/com.jarvis.assistant/files/models/
set -euo pipefail

declare -A MODELS=(
  # default brain — best small all-rounder, strong in Hindi/Hinglish (Apache-2.0)
  ["Qwen3-1.7B-Q4_K_M.gguf"]="https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"
  # budget phones (4–6 GB RAM)
  ["gemma-3-1b-it-Q4_K_M.gguf"]="https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf"
  # flagship option (8–12 GB RAM)
  ["Qwen3-4B-Q4_K_M.gguf"]="https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf"
)

echo "JARVIS model downloader"
echo "1) all   2) qwen1.7b   3) gemma1b   4) qwen4b"
read -r -p "choice [1]: " choice || choice=1
choice=${choice:-1}

pick() {
  case "$1" in
    2) echo "Qwen3-1.7B-Q4_K_M.gguf" ;;
    3) echo "gemma-3-1b-it-Q4_K_M.gguf" ;;
    4) echo "Qwen3-4B-Q4_K_M.gguf" ;;
    *) echo "ALL" ;;
  esac
}

selection=$(pick "$choice")
for name in "${!MODELS[@]}"; do
  if [ "$selection" = "ALL" ] || [ "$selection" = "$name" ]; then
    echo "→ downloading $name"
    curl -L -C - -o "$name" "${MODELS[$name]}"
  fi
done

echo "done. push to phone with:"
echo "  adb push <file>.gguf /sdcard/Android/data/com.jarvis.assistant/files/models/"
