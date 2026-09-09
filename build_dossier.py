#!/usr/bin/env python3
"""JARVIS dossier generator — rebuilds the one-file project record.

Usage:  python3 scripts/build_dossier.py [output.md]
        (or just ./dossier.sh from the project root)

Assembles: stats → idea → session log (from docs/CHANGELOG.md) →
architecture → decisions → privacy → COMPLETE SOURCE CODE of every file →
build/run → verification → limits → roadmap → licenses → manifest → design doc.

Run after every debug/feature session (append to docs/CHANGELOG.md first).
"""

import os
import sys
from datetime import date

HERE     = os.path.dirname(os.path.abspath(__file__))
ROOT     = os.path.dirname(HERE)                      # .../jarvis
CHANGELOG = os.path.join(ROOT, "docs", "CHANGELOG.md")
DESIGN_DOC = os.path.join(ROOT, "docs", "local-ai-assistant-idea.md")
DEFAULT_OUT = os.path.join(os.path.dirname(ROOT), "JARVIS-PROJECT-DOSSIER.md")

# (path, section-title, one-line note) — ordered deliberately
FILES = [
    ("settings.gradle.kts", "Build system", "Gradle settings + JitPack repo (sherpa-onnx AAR)"),
    ("build.gradle.kts", "Build system", "Root build script"),
    ("gradle.properties", "Build system", "Gradle properties"),
    ("gradle/libs.versions.toml", "Build system", "Version catalog (AGP, Kotlin, Compose, OkHttp, Jsoup, sherpa-onnx)"),
    ("gradle/wrapper/gradle-wrapper.properties", "Build system", "Wrapper pin (Gradle 8.9)"),
    ("gradlew", "Build system", "Gradle wrapper script (Unix) — official Gradle v8.9.0 file"),
    ("gradlew.bat", "Build system", "Gradle wrapper script (Windows) — official Gradle v8.9.0 file"),
    ("app/build.gradle.kts", "Build system", "App module: NDK ABIs, CMake hookup, dependencies"),
    ("app/proguard-rules.pro", "Build system", "R8 keep rules for the JNI bridge"),

    ("app/src/main/AndroidManifest.xml", "Manifest & resources", "Permissions, activities, assistant services, wake FGS, QS tile"),
    ("app/src/main/res/values/strings.xml", "Manifest & resources", "App name"),
    ("app/src/main/res/values/colors.xml", "Manifest & resources", "Arc-reactor palette"),
    ("app/src/main/res/values/themes.xml", "Manifest & resources", "Base + translucent overlay themes"),
    ("app/src/main/res/xml/voice_interaction_service.xml", "Manifest & resources", "VoiceInteractionService metadata (system Assistant role)"),
    ("app/src/main/res/drawable/ic_launcher_foreground.xml", "Manifest & resources", "Arc-reactor launcher icon (vector)"),
    ("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", "Manifest & resources", "Adaptive icon"),
    ("app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", "Manifest & resources", "Adaptive icon (round)"),

    ("app/src/main/cpp/CMakeLists.txt", "Native engine (llama.cpp)", "FetchContent build of llama.cpp + our JNI shim"),
    ("app/src/main/cpp/llama_jni.cpp", "Native engine (llama.cpp)", "JNI bridge: mutex+abort safety, UTF-8 sanitizing, streaming"),

    ("app/src/main/java/com/jarvis/assistant/MainActivity.kt", "App layer", "Entry activity: permissions + Compose UI"),
    ("app/src/main/java/com/jarvis/assistant/core/ServiceLocator.kt", "Core wiring", "Dependency graph, engine auto-selection, web/wake state"),

    ("app/src/main/java/com/jarvis/assistant/llm/LlmEngine.kt", "LLM layer", "Engine interface + generation config + cooperative stop"),
    ("app/src/main/java/com/jarvis/assistant/llm/LlamaCppEngine.kt", "LLM layer", "Kotlin wrapper over libjarvis_llama.so (async unload)"),
    ("app/src/main/java/com/jarvis/assistant/llm/ChatTemplate.kt", "LLM layer", "ChatML/Gemma/Llama3/Phi3 prompt rendering + stop strings"),
    ("app/src/main/java/com/jarvis/assistant/llm/ModelManager.kt", "LLM layer", "GGUF discovery, active-model preference"),

    ("app/src/main/java/com/jarvis/assistant/agent/Tool.kt", "Agent layer", "Tool contract + ToolContext"),
    ("app/src/main/java/com/jarvis/assistant/agent/ToolRegistry.kt", "Agent layer", "Registry + manifest + guarded execution"),
    ("app/src/main/java/com/jarvis/assistant/agent/ToolCallParser.kt", "Agent layer", "Lenient TOOL_CALL JSON parser + <think> stripping"),
    ("app/src/main/java/com/jarvis/assistant/agent/WebIntents.kt", "Agent layer", "Fresh-data router (English + Hindi/Hinglish cues)"),
    ("app/src/main/java/com/jarvis/assistant/agent/Orchestrator.kt", "Agent layer", "Agent loop + tool-result clamp + history char budget"),

    ("app/src/main/java/com/jarvis/assistant/tools/WeatherTool.kt", "Tools", "Open-Meteo weather (free, key-less)"),
    ("app/src/main/java/com/jarvis/assistant/tools/WikipediaTool.kt", "Tools", "Wikipedia search + REST summary"),
    ("app/src/main/java/com/jarvis/assistant/tools/WebSearchTool.kt", "Tools", "DDG search → read pages → BM25 passage ranking (mini-RAG)"),
    ("app/src/main/java/com/jarvis/assistant/tools/NewsTool.kt", "Tools", "Google News RSS headlines (India edition)"),
    ("app/src/main/java/com/jarvis/assistant/tools/AlarmTool.kt", "Tools", "System alarm via AlarmClock intent"),
    ("app/src/main/java/com/jarvis/assistant/tools/TimerTool.kt", "Tools", "Countdown timer via AlarmClock intent"),
    ("app/src/main/java/com/jarvis/assistant/tools/FlashlightTool.kt", "Tools", "Torch toggle (CameraManager, no permission)"),
    ("app/src/main/java/com/jarvis/assistant/tools/DateTimeTool.kt", "Tools", "Exact current date/time (offline)"),
    ("app/src/main/java/com/jarvis/assistant/tools/MemoryTool.kt", "Tools", "save_memory: long-term facts about the user"),

    ("app/src/main/java/com/jarvis/assistant/web/WebFetcher.kt", "Web layer", "The ONLY networking class: OkHttp, 24h cache, access log"),
    ("app/src/main/java/com/jarvis/assistant/web/Readability.kt", "Web layer", "Article text extraction (Jsoup heuristics)"),
    ("app/src/main/java/com/jarvis/assistant/web/ParagraphRanker.kt", "Web layer", "BM25-lite passage ranking for mini-RAG"),

    ("app/src/main/java/com/jarvis/assistant/memory/MemoryStore.kt", "Memory", "SQLite: facts + chat log, lexical top-k retrieval"),

    ("app/src/main/java/com/jarvis/assistant/speech/VoiceEngines.kt", "Speech layer", "VoiceInput / SpeechOutput interfaces"),
    ("app/src/main/java/com/jarvis/assistant/speech/SttEngine.kt", "Speech layer", "System SpeechRecognizer fallback (prefers on-device)"),
    ("app/src/main/java/com/jarvis/assistant/speech/AndroidTtsEngine.kt", "Speech layer", "System TTS fallback with onDone callbacks"),
    ("app/src/main/java/com/jarvis/assistant/speech/SherpaSttEngine.kt", "Speech layer", "Fully-offline streaming STT (zipformer, endpoint detection, non-blocking stop)"),
    ("app/src/main/java/com/jarvis/assistant/speech/SherpaTtsEngine.kt", "Speech layer", "Fully-offline Piper/VITS TTS, streaming playback, daemon-thread release"),
    ("app/src/main/java/com/jarvis/assistant/speech/SpeechFormatter.kt", "Speech layer", "Cleans model output for spoken delivery"),

    ("app/src/main/java/com/jarvis/assistant/wake/WakeDrivers.kt", "Wake word", "KWS driver (CMU phoneme keywords) + ASR-phrase fallback"),
    ("app/src/main/java/com/jarvis/assistant/wake/JarvisWakeService.kt", "Wake word", "Always-on microphone foreground service + overlay handoff"),

    ("app/src/main/java/com/jarvis/assistant/assistant/JarvisVoiceService.kt", "System integration", "VoiceInteractionService — the system Assistant role"),
    ("app/src/main/java/com/jarvis/assistant/assistant/JarvisSessionService.kt", "System integration", "Session host; launches the overlay on show"),
    ("app/src/main/java/com/jarvis/assistant/assistant/AssistantActivity.kt", "System integration", "Translucent voice-first overlay (auto-mic, wake-service pause/resume)"),
    ("app/src/main/java/com/jarvis/assistant/tile/JarvisTileService.kt", "System integration", "Quick Settings tile"),

    ("app/src/main/java/com/jarvis/assistant/chat/ChatMessage.kt", "Chat & UI", "Message model"),
    ("app/src/main/java/com/jarvis/assistant/chat/ChatLog.kt", "Chat & UI", "App-wide shared conversation log"),
    ("app/src/main/java/com/jarvis/assistant/chat/ChatViewModel.kt", "Chat & UI", "UI state, model loading, hands-free loop, mic/wake/web toggles"),
    ("app/src/main/java/com/jarvis/assistant/ui/Theme.kt", "Chat & UI", "Arc-reactor dark Material3 theme"),
    ("app/src/main/java/com/jarvis/assistant/ui/ChatScreen.kt", "Chat & UI", "Compose chat screen: bubbles, status bar, toggles, input bar"),

    ("scripts/get_models.sh", "Scripts & tooling", "LLM GGUF downloader + adb push"),
    ("scripts/get_voice_models.sh", "Scripts & tooling", "sherpa ASR + Piper TTS + KWS wake model downloader"),
    ("scripts/build_dossier.py", "Scripts & tooling", "This generator — rerun after every session"),
    ("dossier.sh", "Scripts & tooling", "One-command dossier regeneration"),
    ("docs/CHANGELOG.md", "Scripts & tooling", "Canonical session log — embedded in §2 below, not duplicated in §6"),

    ("README.md", "Docs", "Project README (build, setup, usage, privacy, tuning, dossier workflow)"),
    ("docs/jarvis-ui-concept.html", "Docs", "Summon-UI concept: interactive Arc-Reactor-HUD mockup + Compose mapping"),
]
# NOTE: gradle/wrapper/gradle-wrapper.jar (43 KB binary) is tracked in git but
# deliberately not inlined in the dossier — it is the official Gradle v8.9.0
# wrapper jar, byte-identical to the one in the gradle/gradle repo.

LANG = {
    ".kt": "kotlin", ".kts": "kotlin", ".cpp": "cpp", ".xml": "xml",
    ".sh": "bash", ".toml": "toml", ".properties": "properties",
    ".pro": "text", ".md": "markdown", ".py": "python",
}

def lang_for(path):
    name = os.path.basename(path)
    if name.startswith("CMakeLists"):
        return "cmake"
    if name == ".gitignore":
        return "text"
    return LANG.get(os.path.splitext(path)[1].lower(), "text")

def read_changelog_body():
    with open(CHANGELOG, encoding="utf-8") as f:
        text = f.read().strip()
    # drop the H1 title line; keep everything else verbatim
    lines = text.split("\n")
    if lines and lines[0].startswith("# "):
        lines = lines[1:]
    return "\n".join(lines).strip()

def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT

    manifest, missing, total_loc = [], [], 0
    for rel, _, _ in FILES:
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            missing.append(rel)
            continue
        with open(p, encoding="utf-8") as f:
            n = sum(1 for _ in f)
        manifest.append((rel, n))
        total_loc += n

    kt_loc  = sum(n for (r, n) in manifest if r.endswith(".kt"))
    cpp_loc = sum(n for (r, n) in manifest if r.endswith(".cpp"))
    changelog = read_changelog_body()

    out = []
    w = out.append

    w(f"""# JARVIS — Complete Project Dossier
### A local-first, voice-first AI assistant for Android — full record: concept, decisions, architecture & complete source code

**Dossier generated:** {date.today().isoformat()} · **Project root:** `jarvis/` · **Regenerate anytime:** `./dossier.sh`

| Stat | Value |
|---|---|
| Source files documented below | {len(manifest)} |
| Total lines (all tracked files) | {total_loc:,} |
| Kotlin | {kt_loc:,} lines |
| C++ (JNI bridge) | {cpp_loc:,} lines |
| External engines | llama.cpp (MIT), sherpa-onnx (Apache-2.0) |
| Paid services / API keys / accounts | **0** |

---

## 1. The idea

**Pitch:** *Say "Hey JARVIS" → ask anything → it searches the internet → answers by voice.* The LLM, speech recognition, wake word and voice all run **entirely on the phone** (llama.cpp + sherpa-onnx). The internet is used only to fetch facts from free, key-less public sources (Open-Meteo, Wikipedia, Google News RSS, DuckDuckGo). Flip to ✈ mode and nothing leaves the device.

**Design principles (never violated):**
1. **Local-first** — the brain never leaves the phone; offline is always fully functional.
2. **The internet is a tool, not the brain** — plain HTTPS GETs only; reading/ranking/answering happens on-device.
3. **No cloud LLMs, no API keys, no accounts, no telemetry.**
4. **Privacy is checkable** — one networking class in the whole app (`WebFetcher`), every fetch logged.
5. **Graceful degradation everywhere** — missing voice models → system STT/TTS; no KWS model → ASR-phrase wake; no network → offline answers.

**Why it's feasible now:** small open models (Qwen3-1.7B/4B, Gemma-3-1B, Phi-4-mini) run at usable speeds on phones via llama.cpp's ARM-optimized kernels; sherpa-onnx ships production-grade streaming ASR, Piper/VITS TTS and keyword spotting as one Android AAR.

---

## 2. Project journey (session log)

> Source of truth: `docs/CHANGELOG.md` — append there after each session, then regenerate this dossier.

{changelog}

---

## 3. Architecture

### 3.1 System overview

```
┌──────────────────────────────────────────────────────────────────┐
│                          ANDROID DEVICE                          │
│                                                                  │
│  👂 WakeWord Service (FGS, always-on)                            │
│     KWS zipformer / ASR-phrase ──"Hey JARVIS"──▶ overlay launch  │
│                                          │                       │
│  🎙 STT: sherpa streaming zipformer (offline) ◀─┤ voice-first    │
│     (fallback: system SpeechRecognizer)        │ overlay         │
│                                          ▼                       │
│  ORCHESTRATOR (Kotlin agent loop)                                │
│    ├─ WebIntents router (needs live data?)                       │
│    ├─ TOOL_CALL parse → ToolRegistry → execute                   │
│    │   └─ 1500-char clamp on results + 8k history budget         │
│    └─ prompt assembly (persona + tools + memories + history)     │
│           │                        │                             │
│           ▼                        ▼                             │
│  LLM: llama.cpp (GGUF, Q4)   Tools: alarm/timer/flash/…         │
│  local, streaming, mutex+     web: weather/wiki/news/search      │
│  abort-safe JNI bridge                                          │
│           │                        │                             │
│           ▼                        ▼                             │
│  🗣 TTS: Piper/VITS (offline)  WebFetcher (only net class:       │
│  (fallback: system TTS)        free sources, cached, logged)     │
│                                                                  │
│  Memory: SQLite (facts + chat log)   ChatLog: shared state       │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 Voice Q&A loop (the product's core)

```
👂 "Hey JARVIS" (KWS / ASR-phrase / home-swipe / QS tile)
  → overlay opens, mic hot → sherpa STT (offline, streaming)
  → WebIntents: fresh data needed? → LLM picks a web tool (if 🌐)
  → fetch free source → Readability extract → BM25 rank passages
  → LOCAL LLM composes short spoken-style answer (+source)
  → Piper/VITS speaks it → mic re-opens automatically (hands-free)
```

### 3.3 Request lifecycle (tool round)

```
user text ──▶ orchestrator ──▶ prompt (system+tools+memories+history,
                                  char-budgeted, oldest-first trim)
  ──▶ llama.cpp generate (mutex-held, abort-able, UTF-8-sanitized stream)
  ──▶ TOOL_CALL {{"name":"weather","args":{{…}}}}
  ──▶ registry executes (IO dispatcher, web-guarded)
  ──▶ result clamped to 1500 chars → "[TOOL_RESULT …]" next user turn
  ──▶ generate again ──▶ final answer ──▶ TTS ──▶ mic re-opens
  (max 3 tool rounds; stops stripped; lenient JSON parse)
```

---

## 4. Technology choices & rationale

| Choice | Alternatives | Why this |
|---|---|---|
| **llama.cpp** (GGUF, JNI) | MediaPipe/LiteRT, ExecuTorch, MLC | Broadest model support, ARM KleidiAI CPU kernels, LoRA later, GBNF grammars later, biggest community; JNI gives full control |
| **Qwen3-1.7B Q4_K_M** default | Gemma-3-1B, Phi-4-mini, Llama-3.2 | Best small all-rounder, Apache-2.0 (safest license), strong Hindi/Hinglish; tier table: Gemma-3-1B (4–6 GB RAM) / Qwen3-4B, Phi-4-mini (8–12 GB) |
| **sherpa-onnx AAR** (JitPack 1.12.40) | whisper.cpp, Vosk, CMU pocketsphinx | One AAR = streaming ASR + TTS + KWS + VAD; Apache-2.0; Kotlin API verified against upstream |
| **KWS zipformer for wake** | microWakeWord (custom training), openWakeWord | Zero training — keywords are CMU phoneme text lines; 4.4 MB int8; already in our AAR |
| **Text TOOL_CALL protocol** | OpenAI function calling, GBNF (planned) | Small models handle one explicit line best; parser is lenient; grammar-constrained decoding is the P1 upgrade |
| **System Assistant role** | app-only launcher | Home-swipe/power-hold summon from anywhere — the "real assistant" feel |
| **Foreground service wake** | JobScheduler polling | Only way to hold the mic always-on; user-visible notification keeps it honest |
| **Web defaults ON** | opt-in (original design) | Voice Q&A is the product; ✈ toggle + fetch log preserve the privacy promise |

---

## 5. Privacy contract (verifiable)

- `web/WebFetcher.kt` is the **only** networking class — audit it in one read.
- Wake word, STT, LLM, TTS: never touch the network.
- Fetches carry no user data — just URLs; every URL logged in `WebFetcher.accessLog`; responses cached 24 h.
- No accounts, no keys, no telemetry; memory/chat history stays in `jarvis.db`.

---

## 6. Complete source code

Every file below is copied verbatim from the project (4-backtick fences so embedded markdown survives). Paths are relative to `jarvis/`. (`docs/CHANGELOG.md` is embedded in §2 and not duplicated here.)
""")

    section = None
    sections = list(dict.fromkeys(s for _, s, _ in FILES))
    for rel, sec, note in FILES:
        p = os.path.join(ROOT, rel)
        if not os.path.isfile(p):
            continue
        if sec != section:
            w(f"\n\n### 6.{sections.index(sec) + 1} {sec}")
            section = sec
        with open(p, encoding="utf-8") as f:
            body = f.read().rstrip("\n")
        w(f"\n**`{rel}`** — {note}\n")
        w(f"````{lang_for(rel)}\n{body}\n````\n")

    design = ""
    if os.path.isfile(DESIGN_DOC):
        with open(DESIGN_DOC, encoding="utf-8") as f:
            design = f.read().rstrip("\n")

    w(f"""

---

## 7. Build & run (condensed)

1. **Android Studio** (Ladybug+, SDK+NDK) → Open `jarvis/` → first build fetches llama.cpp (CMake) + sherpa-onnx AAR (JitPack).
2. **LLM:** `./scripts/get_models.sh` → pushes Qwen3-1.7B Q4 GGUF (~1.2 GB).
3. **Voice + wake:** `./scripts/get_voice_models.sh` → ASR (~45 MB) + Piper voice (~65 MB) + KWS (~38 MB).
4. Launch → pick model → tap 👂 to arm wake → *"Hey JARVIS, what's the weather in Mumbai?"*
5. Optional: ⚙ sets JARVIS as the system digital assistant (home-swipe summon).

Full instructions live in the README (inlined in §6 above).

## 8. Verification methodology (why this should compile close to first try)

- **llama.cpp JNI** written after fetching the live `include/llama.h` from master and confirming signatures (`llama_model_load_from_file`, `llama_init_from_model`, `llama_model_get_vocab`, `llama_batch_get_one`, `llama_decode`, sampler chain, `llama_vocab_is_eog`, `llama_n_batch`).
- **sherpa-onnx** Kotlin usage mirrored from upstream sources fetched the same day: `OnlineRecognizer.kt` (config field names `featConfig`/`modelConfig`, `getResult()`), `OnlineStream.kt` (`acceptWaveform(samples, sampleRate)`), `Tts.kt` (`OfflineTtsConfig`, `generateWithCallback`), `KeywordSpotter.kt`, `FeatureConfig.kt`.
- **Artifact coordinates** taken from sherpa's own `jitpack.yml` (`com.k2fsa.sherpa.onnx:sherpa-onnx` @ JitPack); KWS model names/files from the official docs page (`kws-models` release tag).
- **Assistant manifest pattern** cross-checked against AOSP voice-interaction docs (SERVICE_INTERFACE action, BIND_VOICE_INTERACTION permission, sessionService metadata).
- **Same-file edit discipline** enforced after Session 6's race: parallel edits only ever touch distinct files; grep verification sweeps after every batch.
- **Not yet done:** an actual Gradle build (no Android SDK in the authoring sandbox) — treat first sync as the final gate.

## 9. Known limitations (honest)

- Never compiled end-to-end yet (P0); expect minor first-build fixes.
- 1–4B models are mediocre at structured tool calls (lenient parser today, GBNF planned).
- `html.duckduckgo.com` is an unofficial endpoint — swap for SearXNG if it breaks.
- Bundled ASR is English-only; Hindi voice needs the offline-whisper pipeline (planned). Indic Piper TTS voices drop into `voice/tts/`.
- Always-on wake costs battery (light on KWS driver, heavy on ASR fallback); no boot auto-start yet (deliberate).
- No speak-over-it barge-in yet (tap-to-interrupt works; voice barge-in parked — needs AEC/energy gate).
- Chat templates hand-rolled for 4 model families.

## 10. Roadmap (priority order)

**P0 — make the first build work:** first-compile fixes · in-app model downloader (kill adb) · onboarding screen.
**P1 — core quality:** GBNF grammar-constrained tool calling (JNI extension) · citations + fetch-log UI · embedding-based memory (EmbeddingGemma-300M) · error UX · tests + CI (GitHub Actions APK).
**P2 — depth:** SMS/call/calendar/open-app/location-reminder tools · "what's on my screen?" (onHandleAssist) · daily briefing · Hindi/Hinglish voice · share-sheet target · boot auto-start + battery guard for wake · Arc-Reactor-HUD overlay UI (concept done, `jarvis-ui-concept.html`).
**P3 — performance & ship:** idle engine unload (~2 min) · adaptive model swap · ExecuTorch/QNN or OpenCL NPU path · biometric lock, encrypted memory, R8-verified release, Play Data Safety, trademark-safe store name.

## 11. License notes

Code: MIT. llama.cpp: MIT. sherpa-onnx: Apache-2.0 (the zh-en KWS model may carry its own terms — check its release page before redistribution). Models keep their own licenses: Qwen3 = Apache-2.0 (safest default brain); Gemma has usage conditions; Piper voices vary. Wikipedia content: CC BY-SA — attribute. "JARVIS" is a Marvel trademark for personal use; pick a store name before publishing.

---

## Appendix A — File manifest

| File | Lines |
|---|---|
""")

    for (rel, n) in manifest:
        w(f"| `{rel}` | {n} |")
    w(f"| **Total** | **{total_loc:,}** |")

    if missing:
        w("\n\n> ⚠ Files listed but not found at dossier generation time: " + ", ".join(f"`{m}`" for m in missing))

    w("""

---

## Appendix B — Design document (verbatim)

The original living design doc (`local-ai-assistant-idea.md`), as of this dossier:
""")

    if design:
        w(f"\n````markdown\n{design}\n````\n")

    w("\n---\n\n*End of dossier. Project root: `jarvis/` · Regenerate after every session: `./dossier.sh` · Build it, break it, bring back the stack traces.*\n")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))

    print(f"wrote {out_path}")
    print(f"files inlined: {len(manifest)}, missing: {missing}")
    print(f"total LOC tracked: {total_loc}")
    print(f"size: {os.path.getsize(out_path)/1024:.1f} KB, lines: {sum(1 for _ in open(out_path))}")

if __name__ == "__main__":
    main()
