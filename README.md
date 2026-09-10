# JARVIS — Model-jarvis

![Android build](https://github.com/codexwith-raj-sudorm/Model-jarvis/actions/workflows/android-build.yml/badge.svg)

**JARVIS is a local-first, voice-first AI assistant for Android, wearing a Stark-style HUD.**

Say *"Hey JARVIS"* → ask anything → JARVIS fetches only the facts it needs
(free, key-less sources) → answers **by voice**. The brain (llama.cpp, GGUF),
the ears (sherpa-onnx streaming STT), the mouth (Piper TTS) and the wake word
all run **entirely on the device** — inside a holographic arc-reactor interface.

---

## What it does

| Capability | Detail |
|---|---|
| 🧠 Local LLM chat | llama.cpp via JNI, GGUF streaming; Qwen3 / Gemma / Llama / Phi templates auto-detected |
| 👂 "Hey JARVIS" wake word | sherpa-onnx KWS (CMU phonemes, zero training) + ASR-phrase fallback; survives reboot when armed |
| 🎙 Fully offline STT | sherpa-onnx streaming zipformer, endpoint-detected turn-taking, live partials |
| 🔊 Fully offline TTS | Piper/VITS via sherpa-onnx — default voice: `en_GB-alan` (the butler) |
| 🎧 Hands-free loop | reply spoken → mic re-opens automatically; barge-in cuts playback |
| 🌐 Hybrid web layer | weather (Open-Meteo), Wikipedia, web search (DuckDuckGo), news (Google News RSS) — free sources, cached, logged |
|  Agent tool loop | `TOOL_CALL {json}` parser, up to 3 rounds; tools: weather, Wikipedia, web search, news, alarm, timer, flashlight, datetime, memory |
| 🧾 Citations + fetch log | every answer shows which tools produced it ("via weather" chips); 🛰 sheet lists every URL fetched |
| 🗓 Daily briefing | date + home weather + top headlines, spoken on the first summon of the morning |
| 🧠 Long-term memory | on-device SQLite facts + chat log ("remember I'm vegetarian") |
| 📱 System Assistant role | home-swipe / power-hold summons, Quick Settings tile, cross-screen continuity |
| ⬇ In-app model lab | resumable downloads of LLMs (RAM-tier catalog), voice packs and ASR "ears" packs |
| ✈ Offline mode | one tap → *nothing* leaves the device; voice is **never** uploaded |

## How a question travels

```
👂 "Hey JARVIS" (KWS / ASR phrase / home-swipe / in-app mic)
  → streaming STT (offline)
  → router: needs live data? (heuristic + LLM tool choice, English & Hinglish)
  → fetch free sources: Open-Meteo / Wikipedia / Google News RSS / DuckDuckGo
  → Readability extract + on-device passage ranking (BM25-lite)
  → LOCAL LLM composes a short, spoken-style answer with sources
  → Piper TTS (offline) → mic re-opens (hands-free)
```

## Get it

**Option A — prebuilt APK (fastest).** Every push to `main` builds the debug
APK (all ABIs) in GitHub Actions. Download the latest **`jarvis-debug-apk`**
artifact from the [Actions tab](https://github.com/codexwith-raj-sudorm/Model-jarvis/actions) —
the zip contains the APK **and the [user manual](jarvis/docs/USER_MANUAL.md)**.
Sideloader-friendly; enable "install unknown apps" for your file manager.

**Option B — build from source.** The buildable project is the
[`jarvis/`](jarvis/) folder — open *that* in Android Studio (JDK 17, NDK
27.0.12077973, CMake 3.22.1). First build compiles llama.cpp via CMake
FetchContent and pulls the pinned sherpa-onnx 1.12.40 AAR.
`./gradlew assembleDebug` inside `jarvis/` is the same thing CI runs.

**Models (one time):** `jarvis/scripts/get_models.sh` for the LLM (1–3 GB GGUF),
`jarvis/scripts/get_voice_models.sh` for offline STT + TTS + wake word
(~150 MB) — or use the in-app **⬇ download models…** lab instead of adb.

## Documentation

| Doc | What it is |
|---|---|
| 📖 [User manual](jarvis/docs/USER_MANUAL.md) | End-user guide: install, first run, voice, wake word, models, troubleshooting |
| 🛠 [Developer README](jarvis/README.md) | Architecture, project layout, tuning knobs, known limits |
| 🗂 [Session changelog](jarvis/docs/CHANGELOG.md) | The canonical engineering log, session by session |
| 🧠 [Design doc](jarvis/docs/local-ai-assistant-idea.md) | Original concept, model tiers, privacy contract, roadmap |
| 🎨 [Summon-UI concept](jarvis/docs/jarvis-ui-concept.html) | Arc-Reactor HUD concept + interactive mockup |
| 📜 One-file dossier | `./jarvis/dossier.sh` regenerates `JARVIS-PROJECT-DOSSIER.md` (all sources inlined; generated, not committed) |

## Privacy contract

- The only networking class in the app is `web/WebFetcher.kt` — audit it in one read.
- Wake word, STT, LLM and TTS **never** touch the network.
- Fetches carry no user data — just the URL; understanding happens on-device.
- All memory and chat history stays in `jarvis.db` on the device.
- **Voice is never uploaded. Ever.**

## Team

| Name | Role |
|---|---|
| **Master Raj Thakur** | **Administrator & Developer** · [github.com/codexwith-raj-sudorm](https://github.com/codexwith-raj-sudorm) |

## License

Code: **MIT**. Models keep their own licenses (Qwen3 = Apache-2.0; Gemma has
usage conditions; sherpa-onnx = Apache-2.0; check the zh-en KWS model release
page before redistribution; Piper voices vary). Wikipedia content is
CC BY-SA — attribute it.
