#!/usr/bin/env bash
# Downloads fully-offline voice models for JARVIS (sherpa-onnx) and pushes
# them to the phone with adb. App must be installed & opened once first.
#
#   ASR  → /sdcard/Android/data/com.jarvis.assistant/files/voice/asr/
#   TTS  → /sdcard/Android/data/com.jarvis.assistant/files/voice/tts/
#   KWS  → /sdcard/Android/data/com.jarvis.assistant/files/voice/wake/
#
# JARVIS auto-detects them: sherpa engines replace the system STT/TTS, and the
# KWS model powers the always-on "Hey JARVIS" wake word (no training needed —
# keywords are CMU phoneme lines JARVIS installs itself).
set -euo pipefail

BASE=https://github.com/k2-fsa/sherpa-onnx/releases/download
APP_DIR=/sdcard/Android/data/com.jarvis.assistant/files/voice

ASR_MODEL=sherpa-onnx-streaming-zipformer-en-2023-06-26     # English streaming, ~45 MB
TTS_MODEL=vits-piper-en_US-amy-medium                       # English Piper voice, ~65 MB
WAKE_MODEL=sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20    # KWS (en+zh), ~38 MB
# More models: https://github.com/k2-fsa/sherpa-onnx/releases
#   ASR tag "asr-models", TTS tag "tts-models" (incl. Indic voices),
#   wake tag "kws-models".

echo "JARVIS voice model downloader (sherpa-onnx)"
echo "  1) full package: ASR + TTS + wake word (default)"
echo "  2) ASR only   3) TTS only   4) wake-word KWS only"
read -r -p "choice [1]: " choice || choice=1
choice=${choice:-1}

mkdir -p jarvis-voice && cd jarvis-voice

get_asr() {
  echo "→ $ASR_MODEL"
  curl -L -C - -O "$BASE/asr-models/$ASR_MODEL.tar.bz2"
  tar xjf "$ASR_MODEL.tar.bz2"
  adb shell mkdir -p "$APP_DIR/asr"
  adb push "$ASR_MODEL/." "$APP_DIR/asr/"
}

get_tts() {
  echo "→ $TTS_MODEL"
  curl -L -C - -O "$BASE/tts-models/$TTS_MODEL.tar.bz2"
  tar xjf "$TTS_MODEL.tar.bz2"
  adb shell mkdir -p "$APP_DIR/tts"
  adb push "$TTS_MODEL/." "$APP_DIR/tts/"
}

get_wake() {
  echo "→ $WAKE_MODEL"
  curl -L -C - -O "$BASE/kws-models/$WAKE_MODEL.tar.bz2"
  tar xjf "$WAKE_MODEL.tar.bz2"
  adb shell mkdir -p "$APP_DIR/wake"
  adb push "$WAKE_MODEL/." "$APP_DIR/wake/"
}

case "$choice" in
  2) get_asr ;;
  3) get_tts ;;
  4) get_wake ;;
  *) get_asr; get_tts; get_wake ;;
esac

echo
echo "done. restart JARVIS, then tap 👂 in the top bar to arm the wake word."
echo "(status line will show 'listening (sherpa-asr)' and replies use sherpa-vits)"
