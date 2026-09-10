# JARVIS — User Manual

> *A local-first, voice-first AI assistant for Android. Say "Hey JARVIS", ask
> anything, and get a spoken answer — with the brain, ears and voice all on
> your phone.*
>
> **Built by Master Raj Thakur** (Administrator & Developer) ·
> [github.com/codexwith-raj-sudorm/Model-jarvis](https://github.com/codexwith-raj-sudorm/Model-jarvis)

---

## 1. What you're installing

JARVIS is a butler-style assistant. It listens for its name, hears your
question, and answers out loud. Three important things to know up front:

1. **The thinking happens on your phone.** A small local language model (a
   "brain") answers from what it knows; it is fast and private, but it is a
   1–4 billion parameter model — helpful, not frontier-grade.
2. **It may fetch facts from the internet** (weather, Wikipedia, news, web
   search) — only from free, key-less sources, and only the URL goes out.
   You can flip it fully **offline** with one tap, and every URL it has
   fetched this session is visible in the 🛰 log.
3. **Your voice is never uploaded.** Recognition, speech and answering all
   run on-device.

## 2. Installing the app

1. Grab the APK from the latest **Android build** in the project's
   [GitHub Actions tab](https://github.com/codexwith-raj-sudorm/Model-jarvis/actions):
   *Actions → latest "Android build" run → Artifacts → `jarvis-debug-apk` →
   download*. The zip contains the APK **and this manual**.
2. Tap the APK and allow *"Install unknown apps"* for your file manager when
   Android asks.
3. Launch **JARVIS**. Grant the permissions it requests:
   - **Microphone** — hearing you (essential).
   - **Notifications** (Android 13+) — the wake-word foreground service.
   - **Alarms & reminders** — only if you use the alarm tool.
   - **Flashlight** (camera) — only if you use the flashlight tool.

> First run with no brain shows a banner — *"I lack a brain, sir"* — with a
> button that opens the model lab. Follow it; it's the next section.

## 3. Giving JARVIS a brain (one time, 1–3 GB)

1. In the top bar, tap the model name (it says **no model** at first), then
   **⬇ download models…**. The **STARK LAB — SELECT PAYLOAD** dialog opens.
2. Pick a model matching your phone (each row shows size and a RAM tier):
   - **Leaner / Q4 models** — 4–6 GB RAM phones.
   - **Mid-size** — 8 GB+ flagships, noticeably better answers.
   - Downloads are **resumable** — a dropped connection won't restart them.
3. When the progress bar fills, tap **ACTIVATE**. The top-bar model name
   lights up — that's the "brain loaded" state.

> Wi-Fi is recommended. Models live in
> `Android/data/com.jarvis.assistant/files/models/` on your storage.

## 4. Getting offline voice (recommended, ~150 MB total)

Same dialog, two more sections:

- **VOICE MATRIX** — the butler's voice (TTS packs, e.g. `en_GB-alan`).
  Without one, JARVIS falls back to your system's text-to-speech.
- **EAR MATRIX** — speech-recognition packs (STT/"ears"). The default is
  English; an **Ears** pack (e.g. Bengali/English streaming) adds proper
  offline recognition for that language. The active pack shows on the
  telemetry line as `EAR:…`.

Without any voice packs JARVIS still works: it silently uses the phone's
system STT/TTS, and the wake word reuses whatever ASR is present.

## 5. Talking to JARVIS

Four ways to summon:

| Way | How |
|---|---|
| **In-app mic** | Open JARVIS and tap the **mic** button (bottom-right). |
| **Wake word** | Tap **👂** (top bar) to arm it, then say **"Hey JARVIS"** from anywhere. A short vibration confirms it heard you. Arm it again after reboot unless you enabled boot auto-start. |
| **System Assistant** | Settings → Apps → Default apps → Digital assistant → **JARVIS**, or tap ⚙ in the app. Then a home-button swipe / power-hold summons the voice overlay. |
| **Quick Settings tile** | Add the **JARVIS** tile to your notification shade and tap it. |

The status badge next to **J.A.R.V.I.S** tells you the state:
**ONLINE** (ready) · **LISTENING** (mic open) · **THINKING** (generating).

### The conversation loop

1. Speak your question (or type it in the *"Ask J.A.R.V.I.S…"* box).
2. JARVIS answers **out loud** and shows the text, with source chips
   (*"via weather, web_search"*) when it used the web.
3. With **🎧 HANDS-FREE** on, the mic re-opens automatically after it speaks —
   a continuous conversation. Tap the red **stop** to cut in at any time
   (barge-in).

### Things to try

- *"What's the weather in Mumbai?"*
- *"Latest news on the election?"*
- *"Who is CV Raman?"*
- *"Set an alarm for 6:30"*
- *"Start a 5 minute timer"* / *"Turn on the flashlight"*
- *"What day is it? What time?"*
- *"Remember that I'm vegetarian"* — stored in on-device memory
- Hinglish works too: *"aaj ka mausam kaisa hai?"*, *"latest khabar do"*

## 6. The controls

| Control | What it does |
|---|---|
| 🌐 **WEB LINK** / ✈ **OFFLINE** chip | On = JARVIS may fetch live facts (every fetch is logged). Off = *nothing* leaves the device. **On by default.** |
| 🎧 **HANDS-FREE** chip | Auto-reopen the mic after each spoken answer. |
| 👂 **WAKE** chip | Arm/disarm the "Hey JARVIS" wake word (battery-friendly by default — arming is deliberate). |
| **Model name** (top bar) | Dropdown: switch between installed brains, or **⬇ download models…** |
| 🛰 | The **fetch log** — every URL JARVIS retrieved this session. The visible half of the privacy contract. |
| ■ (red, while THINKING) | Stop generating. |
| ■ (while LISTENING) | Stop listening without sending. |
| 🎤 | Open the mic. |
| ➤ | Send the typed text. |
| Telemetry line under the chips | Live status: engine · STT engine · TTS engine · active Ears pack. |

## 7. Daily briefing

The first summon of the morning (or a *"good morning"*) triggers a spoken
briefing: today's date, your home-weather, and the top three headlines. It
degrades gracefully offline (skips whatever it can't get) and needs no
model download to run.

## 8. Memory

*"Remember that …"* / *"Forget that …"* store and delete **facts** in a
local SQLite database on your phone. JARVIS can also answer *"what do you
remember about …?"*. Chat history likewise stays on-device (`jarvis.db`).

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| *"I lack a brain, sir"* banner | Download a model (Section 3). |
| No answer / silence | Check the model name in the top bar is lit; tap it → ACTIVATE the model you want. |
| Wake word doesn't fire | Arm 👂; grant **notifications**; if flaky, it uses the heavier ASR fallback — arming consumes more battery (that's why it's off by default). |
| Voice sounds robotic / system voice | Install a **VOICE MATRIX** pack (Section 4). |
| Recognition misses you | Install an **EAR MATRIX** pack for your language (default is English). |
| Download stuck / failed | Tap **LOAD** again — downloads resume from where they stopped. |
| Mic permission denied | Settings → Apps → JARVIS → Permissions → Microphone → Allow. |
| Battery drain with wake armed | Expected for the ASR fallback driver; the KWS driver (with the wake model) is much lighter. Disarm 👂 when not needed. |
| JARVIS answers with stale facts | Local models only know their training data — that's why the web layer exists; make sure 🌐 is on. |
| Web answer seems off | Open 🛰 to see exactly which pages it read; the search endpoint is unofficial and can wobble. |

## 10. Privacy — the short version

- Networking is confined to one auditable file (`WebFetcher`), used only for
  weather / Wikipedia / news / search and downloads.
- Fetches contain **no personal data** — just the request URL.
- STT, LLM, TTS and wake word are fully on-device; **voice is never uploaded**.
- All memory lives in `jarvis.db` on your phone. Uninstalling removes it.

## 11. Requirements

| | |
|---|---|
| Android | 10+ (SDK 35 target) |
| RAM | 4 GB minimum (6 GB+ recommended; 8 GB+ for mid-size models) |
| Storage | ~5 GB free for the app + a lean model; ~8–10 GB for mid-size |
| Architecture | arm64-v8a, armeabi-v7a, x86_64 (debug build includes all) |

## 12. Credits

- **Master Raj Thakur** — Administrator & Developer, design, engineering and
  every commit. [GitHub](https://github.com/codexwith-raj-sudorm)
- Powered by open source: [llama.cpp](https://github.com/ggml-org/llama.cpp),
  [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [Piper
  TTS](https://github.com/rhasspy/piper), Qwen3 (Apache-2.0) and other GGUF
  models, Compose Multiplatform.

*Questions, bug reports and feature requests: open an issue on the
[GitHub repository](https://github.com/codexwith-raj-sudorm/Model-jarvis).*
