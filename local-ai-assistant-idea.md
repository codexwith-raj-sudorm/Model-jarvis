# JARVIS (working title)
### A Local-First, Open-Source Personal AI Assistant for Android

> **One-line pitch:** "Your private JARVIS — an AI assistant that runs entirely on your phone, with *optional* internet lookup. No cloud LLM. No API keys. No subscriptions. Your data stays yours."

---

## 1. The Idea

Every AI assistant today (Google Assistant/Gemini, Alexa, Siri, ChatGPT app) sends your voice, messages, calendar, and questions to a paid cloud LLM. **JARVIS** flips this: the LLM, speech-to-text, text-to-speech, and personal memory all run **on-device**, offline. When you *want* live information, an opt-in web layer fetches it from free public sources — and your **local** model reads and summarizes it. The brain never leaves the phone.

**Design principle: local-first, hybrid on demand.**
- Offline is the default and always works (airplane mode = full assistant).
- Internet is a *tool* the assistant may use, only when you enable it, visible every time it's used.
- No external LLM calls, ever — fetching a web page is a plain HTTP request; *understanding* it stays local.

**Why now (2026):**
- Small open models (1B–4B class) are now genuinely useful — Qwen3 1.7B/4B, Gemma 3 1B/3n, Phi-4-mini, LFM2 run at 10–40 tokens/sec on recent phones, fully offline.
- Inference engines are mature: `llama.cpp` runs GGUF models on Android with ARM KleidiAI-optimized kernels; LiteRT (MediaPipe) and ExecuTorch offer NPU paths.
- On-device speech models (Whisper, sherpa-onnx streaming ASR, Piper/VITS TTS) are production-ready.
- Rich free, key-less public data APIs (Wikipedia, Open-Meteo, RSS, SearxNG/DuckDuckGo) make live answers possible without any paid service.
- Privacy awareness + recurring subscription fatigue = a real audience for "your AI, your device."

**Target user:** Privacy-conscious users, people with poor/expensive connectivity, tinkerers — and multilingual users (strong angle for India: Hindi/Marathi/Hinglish support via Qwen3-family and AI4Bharat speech models).

---

## 2. Core Feature Set

| # | Feature | How |
|---|---------|-----|
| 1 | **Offline voice chat** | Wake word → streaming STT → LLM → TTS, works in airplane mode |
| 2 | **Ask my phone** | "What's my next meeting?", "Did Priya message me?" — reads Calendar, SMS, notifications via Android APIs |
| 3 | **Do things** (agent) | Set alarms, timers, send SMS/WhatsApp, call contacts, toggle flashlight/Wi-Fi/BT, set reminders |
| 4 | **Personal memory (RAG)** | Remembers facts about you ("I'm vegetarian", "my gym is at 7am") in an on-device vector store; recalls context later |
| 5 | **Live web answers** *(opt-in)* | "What's the weather?", "Latest news on X?", "Who won the match?" — fetches from free public APIs/pages, **local** LLM summarizes with citations |
| 6 | **Summarize & draft** | Summarize long SMS/emails/notes/web pages shared to it; draft replies in your tone |
| 7 | **Daily briefing** | Morning summary: live weather + headlines (fetched once on Wi-Fi if online) + calendar + tasks — assembled on-device |
| 8 | **Screen-aware help** (optional) | Uses Notification Listener / (opt-in) Accessibility to answer "what's on my screen" |
| 9 | **Model swap** | User picks engine + model: speed (Gemma 3 1B) vs brains (Qwen3 4B / Phi-4-mini) based on their RAM |

**Explicitly out of scope:** cloud LLM calls (ever), account creation, ads, telemetry.

---

## 3. System Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                          │
│                                                                  │
│  ┌───────────┐   ┌─────────────┐   ┌───────────────────────────┐ │
│  │ Wake Word │──▶│  Streaming  │──▶│       ORCHESTRATOR        │ │
│  │  Engine   │   │  STT (ASR)  │   │   (Kotlin agent loop)     │ │
│  │  "Hey     │   │ sherpa-onnx │   │   - intent routing        │ │
│  │  JARVIS"  │   │  or whisper │   │   - tool/function calling │ │
│  └───────────┘   └─────────────┘   │   - "needs fresh info?"   │ │
│                                    └─────┬──────────┬─────────┘ │
│              ┌───────────────────────────┤          │           │
│              │            ┌──────────────▼───────┐  │           │
│              ▼            ▼                      ▼  ▼           │
│  ┌────────────────────┐ ┌────────────────────┐ ┌────────────┐   │
│  │  LOCAL LLM ENGINE  │ │  MEMORY LAYER      │ │ DEVICE I/O │   │
│  │  llama.cpp (GGUF)  │ │  SQLite + vectors  │ │ Calendar,  │   │
│  │  Q4, 1B–4B models  │ │  + small embedder  │ │ SMS, Alarm,│   │
│  │  CPU/NPU + KleidiAI│ └────────────────────┘ │ Contacts…  │   │
│  └─────────┬──────────┘                        └────────────┘   │
│            │          ┌────────────┐                             │
│  ┌─────────▼───┐     │  WEB TOOLS │◀── user-enabled, per-use    │
│  │     TTS     │     │  (opt-in)  │    "🌐 ONLINE" indicator    │
│  │ Piper/VITS  │     └─────┬──────┘                             │
│  └─────────────┘           │                                    │
│  UI: Jetpack Compose + Assistant role (home-button swipe)        │
└────────────────────────────┼─────────────────────────────────────┘
                             │  plain HTTPS fetches only
                             ▼  (no queries to any LLM cloud)
┌──────────────────────────────────────────────────────────────────┐
│              FREE PUBLIC WEB SOURCES (no API keys)               │
│  Wikipedia REST · Open-Meteo (weather) · RSS (news/scores) ·     │
│  SearxNG / DuckDuckGo Lite (general search)                      │
│  fetch → readability-extract → embed → LOCAL LLM answers         │
└──────────────────────────────────────────────────────────────────┘
   Offline by default · Web tools opt-in & visible · No cloud LLM ever
```

**Request lifecycle — "Hey JARVIS, what's the weather in Pune tomorrow?"**
1. Wake word detected by tiny always-on model (~2 MB).
2. Streaming ASR transcribes speech in real time.
3. Orchestrator classifies: *needs fresh data* → checks web toggle → **ON**, so it fires the weather tool (Open-Meteo, free, no key). UI shows "🌐 fetching…".
4. Fetched JSON/page text is cleaned, chunked, embedded locally; top passages are injected into the prompt.
5. **Local LLM** composes the spoken answer with attribution ("Open-Meteo says…") → TTS.
6. The fetch result is cached locally (offline re-ask later); LLM unloads after ~2 min idle.

Device-only commands ("Set an alarm for 6am") skip step 3–4 entirely and never touch the network.

---

## 4. Tech Stack (everything free & open-source)

### 4.1 The Brain — LLM engine + models
| Component | Recommendation | Why |
|-----------|----------------|-----|
| **Inference engine** | **llama.cpp** (compiled via Android NDK/JNI, or a wrapper like Cactus/ChatterUI as reference) | Broadest model support (GGUF), ARM KleidiAI-optimized CPU kernels, LoRA adapters, GBNF grammars, huge community |
| Fast-path alternative | LiteRT/MediaPipe LLM API | Quick prototype, but locked to supported models |
| NPU path (later) | ExecuTorch + Qualcomm QNN / MediaTek delegates | Real NPU acceleration on flagships, harder pipeline |
| **Model: budget phones (4–6 GB RAM)** | Gemma 3 1B (~0.8 GB Q4) or LFM2 350M–1B | Fast, tiny, fits low RAM |
| **Model: mid-range (6–8 GB)** | **Qwen3 1.7B** (~1.2 GB) — the default pick | Best small all-rounder, strong multilingual (Hindi!), thinking mode |
| **Model: flagships (8–12 GB+)** | Qwen3 4B, Phi-4-mini (3.8B), or Gemma 3n E4B | Near-7B quality offline |
| Quantization | Q4_K_M GGUF (4-bit) | Best size/quality tradeoff for mobile |

### 4.2 The Ears — Speech-to-Text
- **sherpa-onnx** (k2-fsa): streaming, offline, tiny zipformer models (~50 MB), VAD included, supports Indic languages (AI4Bharat models).
- Alternative: **whisper.cpp** (tiny/base, multilingual, non-streaming but very accurate).

### 4.3 The Mouth — Text-to-Speech
- **Piper / VITS voices via sherpa-onnx**: fully offline, ~30 MB per voice, runs in real time on CPU.
- Fallback: Android system TTS engine (zero extra size).
- India angle: AI4Bharat / IndicTTS voices for Hindi & Marathi.

### 4.4 The Trigger — Wake word
- **microWakeWord** or **openWakeWord**: open-source, trains custom 1–3 MB models ("Hey JARVIS").
- Cheaper v1 alternative: Vosk/zipformer keyword spotting, or just a floating bubble + headset button.

### 4.5 Memory & Knowledge
- **SQLite** (structured facts, chat history) + **sqlite-vec** or **ObjectBox** for on-device vector search.
- **Embedding model**: EmbeddingGemma-300M or Qwen3-Embedding-0.6B via llama.cpp — small enough for mobile. Doubles as the embedder for fetched web content.
- Optional: user can drop in personal documents (notes, PDFs) → chunked, embedded, searchable 100% locally.

### 4.6 The Connection — opt-in web retrieval layer
The rule: **the internet is a tool, not the brain.** Only deterministic HTTP fetches go out; all reading, ranking, and answering happens on-device.

| Capability | Source | Cost / Key |
|-----------|--------|-----------|
| Encyclopedia lookups | Wikipedia REST API | Free, no key, CC BY-SA |
| Weather (global) | Open-Meteo | Free, no key, CC BY 4.0 |
| News headlines, cricket scores, markets | RSS feeds (TOI, BBC, ESPNcricinfo RSS, etc.) | Free |
| General web search | SearxNG public instances / DuckDuckGo Lite HTML | Free (respect rate limits) |
| Page reading | Jsoup + Readability4J extract → local chunks → local embedder → top-k into LLM | Free, offline |

**Privacy design for the web layer:**
- Master toggle (default **OFF**) + quick "airplane" pill in the UI; assistant announces when it's going online.
- Persistent "🌐 ONLINE" badge whenever a fetch is in flight.
- Fetch log: every URL requested is shown to the user, exportable, nothing hidden.
- Only the search keywords / URL leave the device — never your voice, contacts, or memory.
- Fetched pages cached locally, so follow-up questions work offline.
- Rate limiting + per-domain allowlist to keep behaviour predictable.

### 4.7 App layer
- **Kotlin + Jetpack Compose**, MVVM.
- **Foreground service** for the always-on assistant; model loads lazily.
- **VoiceInteractionService** → register as the system Assistant so a home-button swipe / power-button hold launches *your* assistant instead of Gemini. This is the killer integration.
- Quick Settings tile, notification quick-reply actions, share-sheet target ("Summarize this").

---

## 5. The Hard Parts (and how to solve them)

| Challenge | Mitigation |
|-----------|------------|
| **Small models are bad at tool calling** | Don't rely on free-form JSON. Use llama.cpp **GBNF grammars** to constrain output to a valid function schema; add a fast intent-classifier (fine-tuned 0.5B or even a TF-Lite classifier) as a fallback router for the top 20 commands |
| **Deciding when to go online** | Router prompt/classifier for "fresh-data-needed" intents (weather, news, scores, "latest", "today"); ask-for-permission flow the first time each web tool fires; everything else stays offline |
| **Messy web pages, ads, SEO junk** | Readability4J extraction, strict content truncation, local top-k passage selection by the embedder; prefer structured sources (Wikipedia, Open-Meteo, RSS) over raw scraping |
| **RAM pressure / app kills** | Load model on demand, mmap the GGUF, unload after idle; `largeHeap` + foreground service; pick model tier by `ActivityManager.memoryClass` |
| **Battery drain** | Always-on wake word uses a tiny model with almost no CPU; LLM loads only on interaction; KleidiAI/QNN kernels cut energy per token |
| **Thermal throttling** | Cap token throughput targets; prefer 1B model for voice (short bursts), 4B only for text chat |
| **First-run model download (1–3 GB)** | Bundle via Google Play asset packs, or in-app downloader from Hugging Face with resume — one-time, Wi-Fi only, then forever offline |
| **Multilingual quality** | Qwen3 family for Hinglish/Hindi; AI4Bharat ASR/TTS for Indian languages; optional per-language model packs |
| **Making it feel smart** | Great prompts + memory + retrieval matter more than raw model size; aggressive context curation (last 10 turns + top-5 memories only) |

---

## 6. Build Roadmap

**Phase 0 — Proof of concept (1–2 weekends)**
- Compile llama.cpp for Android (NDK + JNI) or fork an open app (ChatterUI / PocketPal AI) as reference.
- Load Qwen3 1.7B Q4 GGUF → text chat screen in Compose. Measure tokens/sec on your phone.

**Phase 1 — Voice loop (2–4 weeks)**
- Add sherpa-onnx streaming ASR + Piper TTS. Hold-to-talk button → speak → answer.
- Foreground service, model load/unload lifecycle.

**Phase 2 — Agent + memory (1–2 months)**
- 10–15 tools: alarm, timer, SMS, call, calendar query, flashlight, Wi-Fi/BT, open app, reminders.
- GBNF-constrained function calling + memory layer (SQLite + embeddings).
- Daily briefing feature (offline parts).

**Phase 3 — Web layer + system assistant (1 month)**
- ✅ *Shipped in scaffold:* Assistant role (`VoiceInteractionService` + session), voice-first overlay with auto-mic, Quick Settings tile, shared conversation log, model swap UI, 3 web tools (Wikipedia/Open-Meteo/DDG) with cache + access log
- ⏳ Still pending: wake word ("Hey JARVIS"), share-sheet target, RSS + citations UI, live daily briefing, in-app model downloader
- Live daily briefing (weather + headlines fetched on Wi-Fi).
- Model manager UI (download/swap Gemma 3 1B ↔ Qwen3 4B based on device RAM).

**Phase 4 — Polish & differentiation (ongoing)**
- ExecuTorch/QNN NPU path for supported chipsets.
- Hindi/Marathi voice packs.
- Optional PC-side LoRA fine-tune of your own chat export → side-load the adapter (llama.cpp supports LoRA at runtime) → an assistant that literally talks like you want.

---

## 7. What Makes This Idea Defensible

1. **Offline by default** — the assistant still fully works in airplane mode; connectivity is opt-in, visible, and revocable at a tap. No cloud assistant can make that claim.
2. **Zero running cost** — no LLM API bills and no paid data APIs means it can be free forever (or a one-time "Pro" unlock).
3. **Hybrid privacy** — when it does go online, only bare search keywords leave the device, every fetch is logged and user-visible, and understanding never leaves the phone.
4. **Works where connectivity fails** — metros, flights, remote areas, roaming.
5. **Personalization depth** — on-device memory + optional LoRA tuning of your own assistant is something cloud apps won't do.
6. **Open-source community flywheel** — custom models, voices, wake words, web-source plugins, and tool plugins are all swappable; others can extend it.

---

## 8. License/Cost Hygiene (your "no paid LLMs" constraint)

| Piece | License | Cost |
|-------|---------|------|
| llama.cpp | MIT | Free |
| Qwen3 / LFM2 / SmolLM | Apache 2.0 | Free, commercial OK |
| Gemma 3 / 3n, Phi-4-mini | Gemma license / MIT | Free with conditions |
| sherpa-onnx, whisper.cpp, Piper | Apache 2.0 | Free |
| openWakeWord / microWakeWord | MIT / Apache | Free |
| Wikipedia REST API | CC BY-SA (content) | Free, no key |
| Open-Meteo | CC BY 4.0 | Free, no key |
| RSS / SearxNG / DDG Lite | Free (ToS/rate limits apply) | Free |
| SQLite, ObjectBox CE, Jetpack | Various OSS | Free |

⚠️ Watch-outs: Gemma's license has usage conditions (Qwen3 / Apache 2.0 is the safest brain); scraping search engines long-term is ToS-gray — prefer SearxNG instances or bring-your-own endpoint; attribute Wikipedia content.

---

*Name note: "JARVIS" is the obvious Iron Man nod — perfect for a personal project. If you ever publish to the Play Store, Marvel's trademark may apply, so keep a fallback store name in mind (e.g., "Jarvis Local", or the original "Sahayak"). Next step if you want to proceed: Phase 0 above, and I can scaffold the Android project structure for you.*
