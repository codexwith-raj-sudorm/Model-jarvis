# Phase C — "Real JARVIS" upgrade plan

> Drafted while CI round 3 runs. Implementation is strictly sequential:
> one feature → audit → push → green → next feature. No mixing.

## C1. The Butler persona (the soul)
- `Orchestrator.buildSystemPrompt()`: replace the generic "concise, warm"
  persona with the butler persona:
  - Addresses the user as "sir" (occasionally "boss" for variety)
  - Dry British wit, understated, never sycophantic
  - Proactive closings ("Anything else, sir?")
  - Voice mode: crisp 1–3 sentences, no markdown, natural source attribution
    ("according to Wikipedia, sir")
- Keep the "reply in the language of the question" rule (Hinglish stays).

## C2. The Voice (movie-accurate)
- Default TTS model → British male Piper voice (en_GB-alan-medium or
  en_GB-norman-medium) in `get_voice_models.sh` (offer en_US-amy as alt).
- Speech rate 0.95 (slightly measured delivery).

## C3. Daily briefing ("Good morning, sir")
- New `BriefingTool` (web): weather (home city) + top 3 headlines + date.
- Wake service: on first wake-detection of the day (or "good morning jarvis"
  intent), pre-empt the mic with the briefing read-out, then open the mic.
- Simple, private: a SharedPreferences "last briefing date" — no new storage.

## C4. In-app model downloader (P0 roadmap)
- `ModelDownloadScreen` (Compose): list curated GGUFs (name/size/RAM tier),
  download with progress via OkHttp (WebFetcher gains a
  `download(url, dest, onProgress)` method — still the only network class),
  resume support, Wi-Fi-only check, then set active + warm-swap.
- Menu in the top bar (⬇ chip when no model present).

## C5. Citations / fetch-log UI (privacy contract, P1 roadmap)
- `WebFetcher.accessSnapshot()` already exists — surface it: an info chip in
  the top bar opens a bottom sheet listing the last fetches (host, time,
  cache-hit), plus per-answer source chips on assistant bubbles (the
  `source` field on ChatMessage already exists).

## C6. Continuity & onboarding
- First-run: one-screen "grant mic → download voice pack → pick model"
  flow (reuses C4 UI).
- Assistant bubbles show which tools fired (⚙ chips from toolStatus history).

## Order
C1 → C2 (both tiny, persona-defining) → C3 (signature feature) → C4 (P0)
→ C5 → C6. Each = separate commit, audit-gated.

## Discipline (unchanged)
- `python3 scripts/audit_fixes.py` before every push (add new fixes to the
  inventory as they land).
- No parallel edits to the same file. Verify each edit with grep.
- APIs verified against upstream sources, never memory.
- CHANGELOG entry + `./dossier.sh` after every feature session.
