# JARVIS — a local-first AI assistant for Android

**Core experience: say "Hey JARVIS" → ask your question → it searches the
internet → answers by voice.** The LLM, speech recognition, wake word and
voice all run entirely on your phone (sherpa-onnx + llama.cpp). The internet
is used only to fetch facts from free, key-less sources — and flip to ✈ mode
and *nothing* leaves the device.

> Design doc: see `docs/local-ai-assistant-idea.md` for the full concept & roadmap.

---

## What works in this scaffold

| Capability | Status |
|---|---|
| Local LLM chat (llama.cpp, GGUF, streaming) | ✅ via JNI bridge (`app/src/main/cpp/`) |
| Qwen3 / Gemma / Llama / Phi chat templates | ✅ auto-detected from filename |
| Agent tool loop (`TOOL_CALL {json}`) | ✅ prompt+parse loop, up to 3 rounds |
| Tools: alarm, timer, flashlight, datetime, memory | ✅ offline |
| Web tools: weather (Open-Meteo), Wikipedia, web search (DDG) | ✅ free sources, cached, logged |
| News headlines (Google News RSS, India edition) | ✅ |
| Mini-RAG web answers (search → read pages → rank passages) | ✅ `ParagraphRanker` |
| Voice-optimized spoken answers (short, no markdown, cited) | ✅ voice mode |
| Hands-free loop: reply spoken → mic re-opens automatically | ✅ 🎧 toggle / overlay |
| Fully-offline STT (sherpa-onnx streaming zipformer) | ✅ auto-selected when model present |
| Fully-offline TTS (Piper/VITS via sherpa-onnx) | ✅ auto-selected when voice present |
| **"Hey JARVIS" wake word** (KWS, CMU-phoneme keywords, no training) | ✅ 👂 toggle, foreground service |
| Wake fallback driver (ASR phrase match) | ✅ works with just the ASR model |
| System STT/TTS fallback | ✅ zero-setup graceful degrade |
| Long-term memory (facts, SQLite) | ✅ lexical retrieval (embeddings later) |
| System Assistant role (home-swipe / power-hold) | ✅ `VoiceInteractionService` |
| Voice-first overlay screen (auto-mic) + Quick Settings tile | ✅ |
| Cross-screen conversation continuity | ✅ shared `ChatLog` |
| Screen context ("what's on my screen?"), boot auto-start | 🔜 |

## Project layout

```
jarvis/
├── app/src/main/
│   ├── cpp/                        ← llama.cpp JNI bridge + CMake (FetchContent)
│   ├── java/com/jarvis/assistant/
│   │   ├── MainActivity.kt
│   │   ├── assistant/              ← system-Assistant role + voice overlay
│   │   ├── tile/JarvisTileService.kt ← Quick Settings tile
│   │   ├── wake/                   ← "Hey JARVIS": KWS + ASR-phrase drivers,
│   │   │                              microphone foreground service
│   │   ├── core/ServiceLocator.kt  ← the dependency graph (engine selection)
│   │   ├── llm/                    ← engine, JNI wrapper, chat templates, model mgr
│   │   ├── agent/                  ← orchestrator, tool registry, parser, web-intent router
│   │   ├── tools/                  ← weather, wikipedia, web_search, news, alarm,
│   │   │                              timer, flashlight, datetime, save_memory
│   │   ├── web/                    ← WebFetcher (cache+access log), Readability, ranker
│   │   ├── memory/MemoryStore.kt   ← SQLite facts + chat log
│   │   ├── speech/                 ← VoiceInput/SpeechOutput, sherpa STT & TTS,
│   │   │                              system fallbacks, speech formatter
│   │   ├── chat/                   ← ViewModel + shared ChatLog + UI state
│   │   └── ui/                     ← Compose theme + chat screen
│   └── res/                        ← arc-reactor icon, themes, assistant XML
├── scripts/get_models.sh           ← LLM GGUF models
├── scripts/get_voice_models.sh     ← sherpa ASR + Piper TTS + KWS wake model
└── README.md
```

## Quick start

### 1. Open & build

1. Install **Android Studio** (Ladybug or newer) with default SDK + NDK.
2. **File → Open** → select this `jarvis/` folder. (If Studio asks about the
   missing Gradle wrapper jar, let it fix/generate it — or run `gradle wrapper`.)
3. First build downloads llama.cpp via CMake FetchContent (compiles
   `libjarvis_llama.so`) and pulls the sherpa-onnx AAR from JitPack
   (`com.k2fsa.sherpa.onnx:sherpa-onnx`). If JitPack is unavailable, grab the
   AAR from [sherpa-onnx releases](https://github.com/k2-fsa/sherpa-onnx/releases)
   into `app/libs/` and use `implementation(files("libs/sherpa-onnx-<ver>.aar"))`.

### 2. Get the LLM (1–3 GB, one time)

```bash
./scripts/get_models.sh          # or pick manually:
curl -L -O https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf
adb push Qwen3-1.7B-Q4_K_M.gguf \
  /sdcard/Android/data/com.jarvis.assistant/files/models/
```

### 3. Get offline voice + wake word (recommended, ~150 MB total)

```bash
./scripts/get_voice_models.sh    # ASR + Piper voice + KWS wake model (default)
# pushes into .../files/voice/{asr,tts,wake}/
```

4. Launch **JARVIS** → pick your GGUF in the top bar → tap **👂** to arm the
   wake word (grants the notification permission prompt on Android 13+).
   Now, from anywhere: *"Hey JARVIS"* → short vibration → voice overlay opens,
   mic listening → ask your question → spoken answer → mic re-opens.

   - *"What's the weather in Mumbai?"* → `weather` tool → spoken answer
   - *"Latest news on the election?"* → `news` tool
   - *"Who is CV Raman?"* → Wikipedia, answered locally
   - *"Set an alarm for 6:30"* → real system alarm
   - *"Remember that I'm vegetarian"* → saved to on-device memory

   Without voice models, JARVIS silently falls back to system STT/TTS, and
   without the KWS model the wake word reuses the ASR model (phrase match).

### 4. Make JARVIS your system assistant (recommended)

1. Tap the **⚙ gear** in the top bar (or Settings → Apps → Default apps →
   **Digital assistant app**) and choose **JARVIS**.
2. A home-button swipe / power-hold also summons the voice overlay.
3. Optional: add the **JARVIS** Quick Settings tile.

## The voice Q&A loop (primary focus)

```
 👂 "Hey JARVIS" (KWS always-on, or ASR phrase match, or home-swipe)
   → mic opens → sherpa-onnx streaming STT (offline)
   → router: needs live data?  (WebIntents heuristic + LLM tool choice)
   → fetch from free sources: Open-Meteo / Wikipedia / Google News RSS / DuckDuckGo
   → extract (Readability) + rank passages (BM25-lite, on-device)
   → LOCAL LLM composes a short spoken-style answer with source
   → TTS: Piper/VITS (offline)  →  mic re-opens automatically (hands-free)
```

- 🎧 = hands-free conversation; 👂 = wake word; 🌐/✈ = internet on/off
  (**on by default** — every fetched URL is logged in `WebFetcher.accessLog`;
  nothing else leaves the device; **voice is never uploaded**)
- The fresh-data router understands English *and* Hindi/Hinglish cues
  ("aaj ka mausam", "latest khabar", "score").
- Barge-in: speaking while JARVIS talks cuts playback and opens the mic.
- The wake service pauses its mic while the overlay is talking/listening, then
  resumes — no mic contention.

## The privacy contract

- The only networking class in the app is `web/WebFetcher.kt` — audit it in one read.
- Wake word, STT, LLM and TTS never touch the network (all sherpa-onnx + llama.cpp).
- Fetches carry no user data: just the URL; understanding happens on-device.
- All memory/chat history stays in `jarvis.db` on the device.

## Tuning

| Knob | Where |
|---|---|
| Threads / context size | `ChatViewModel.THREADS`, `engine.load(...)` args |
| Sampling (temp/top-p/top-k) | `Orchestrator.generationConfig` |
| Persona & tool prompt | `Orchestrator.buildPromptMessages()` |
| End-of-turn sensitivity (STT) | `EndpointConfig` rules in `SherpaSttEngine` |
| Wake sensitivity | `keywordsThreshold` / `keywordsScore` in `WakeDrivers.kt` |
| Wake phrase | `DEFAULT_KEYWORDS` in `WakeDrivers.kt` (CMU phonemes) |
| Voice speed | `speed` in `SherpaTtsEngine.speak` |
| Add a tool | implement `Tool`, register in `ServiceLocator` |

## Known limits (honest ones)

- Always-on listening costs battery: the KWS driver is a 3M-param int8 model on
  one thread (light), the ASR fallback is heavier — expect noticeable drain with
  the fallback driver. Wake is OFF by default for this reason.
- KWS keyword phonemes are hand-written; if detection is flaky, tweak the
  phonemes/threshold in `voice/wake/keywords.txt` (delete the file to let
  JARVIS regenerate its default).
- 1–4B models are mediocre at structured tool calls — the parser is deliberately
  lenient; grammar-constrained decoding (GBNF) is a planned upgrade.
- `html.duckduckgo.com` is an unofficial endpoint; swap for SearXNG if it breaks.
- The bundled ASR is English-only (Hindi needs the offline-whisper pipeline —
  future work). Indic Piper voices for TTS drop into `voice/tts/`.
- Chat templates are hand-rolled for the big four families; exotic GGUFs may
  need a template entry in `ChatTemplate.kt`.

## The one-file dossier

Everything — narrative, session-by-session changelog, architecture, decisions,
and the **complete source code of every file** — is consolidated into
`JARVIS-PROJECT-DOSSIER.md` (one level above this folder). After any debug or
feature session:

1. Append the session to `docs/CHANGELOG.md` (canonical narrative).
2. Run `./dossier.sh` — regenerates the dossier with fresh stats and code.

## Roadmap

See `docs/local-ai-assistant-idea.md` §6 — next up: embedding-based memory,
RSS+citations UI, in-app model downloader, screen context for the assistant
role, boot auto-start of the wake service, ExecuTorch/QNN NPU path.

## License notes

Code: MIT. Models keep their own licenses (Qwen3 = Apache-2.0, safest default;
Gemma has usage conditions; sherpa-onnx = Apache-2.0 — but the zh-en KWS
model may carry its own terms, check its release page before redistribution;
Piper voices vary). Wikipedia content is CC BY-SA — attribute.
