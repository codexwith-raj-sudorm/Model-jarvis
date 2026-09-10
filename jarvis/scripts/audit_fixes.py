#!/usr/bin/env python3
"""JARVIS pre-push audit — asserts every historical fix is present.

The project has been bitten THREE times (Sessions 6, 15, 16) by silently
lost/clobbered edits producing chimera files. This script is the gate:
a canonical inventory of (file, must-contain pattern, description) for
every fix across sessions, plus syntax-level checks (nested-comment-aware
comment balance, brace balance, package/path match).

Run before every push:   python3 scripts/audit_fixes.py
Exit code 0 = safe to push.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")

PKG = os.path.join(SRC, "com/jarvis/assistant")
TESTPKG = os.path.join(ROOT, "app/src/test/java/com/jarvis/assistant")

def read(rel):
    """Main-source paths are relative to PKG; 'test/'-prefixed paths go to TESTPKG."""
    if rel.startswith("test/"):
        path = os.path.join(TESTPKG, rel[len("test/"):])
    else:
        path = os.path.join(PKG, rel)
    with open(path, encoding="utf-8") as f:
        return f.read()

# (file, pattern, description) — grows with every fix, forever.
FIXES = [
    # Session 15 — code review fixes
    ("llm/ModelManager.kt", "fun baseDir(context: Context): File",
     "external-first storage resolver exists"),
    ("llm/ModelManager.kt", 'File(baseDir(context), "models")',
     "modelsDir uses baseDir (external first)"),
    ("speech/SherpaSttEngine.kt", "ModelManager.baseDir",
     "STT model dir via baseDir"),
    ("speech/SherpaTtsEngine.kt", "ModelManager.baseDir",
     "TTS model dir via baseDir"),
    ("wake/WakeDrivers.kt", "ModelManager.baseDir",
     "wake model dir via baseDir"),
    ("wake/WakeDrivers.kt", "fun awaitStopped",
     "MicLoop bounded join helper exists"),
    ("wake/WakeDrivers.kt", "openMicWithGrace",
     "wake mic open retry exists"),
    ("speech/SherpaSttEngine.kt", "worker?.join(500)",
     "STT release joins worker"),
    ("speech/SherpaTtsEngine.kt", "awaitTermination(500",
     "TTS release waits for executor"),
    ("agent/Orchestrator.kt", "@Volatile var template",
     "template visible across threads"),
    ("chat/ChatViewModel.kt", "sendGuard.compareAndSet",
     "double-send CAS guard"),
    ("chat/ChatViewModel.kt", "scope.launch(Dispatchers.Default)",
     "agent turn off the main thread"),
    ("chat/ChatViewModel.kt", "fun switchModel",
     "warm model swap exists"),

    # Session 16 — first-compile fixes (nested-comment bug & friends)
    ("llm/ModelManager.kt", "comments NEST",
     "nested-comment warning note (history preserved)"),
    ("agent/WebIntents.kt", "fun has(vararg words: String): Boolean",
     "WebIntents has() as local function (not inferred lambda)"),
    ("speech/SherpaTtsEngine.kt", "WRITE_BLOCKING",
     "AudioTrack float write uses the 4-arg overload"),
    ("speech/SherpaSttEngine.kt", "worker = Thread.currentThread()",
     "STT worker thread tracked for join"),
    ("assistant/JarvisVoiceService.kt", r"// NOTE: there is deliberately no onShowSession override",
     "no phantom onShowSession override"),
    ("assistant/JarvisSessionService.kt", "startAssistantActivity",
     "session delegates UI to AssistantActivity (documented pattern)"),
    ("tile/JarvisTileService.kt", "SDK_INT >= 29) tile.subtitle",
     "tile subtitle API-29 guard"),

    # Phase C — butler features
    ("agent/Orchestrator.kt", "modeled on Tony Stark's AI butler",
     "C1 butler persona in system prompt"),
    ("speech/SherpaTtsEngine.kt", "SPEECH_RATE = 0.95f",
     "C2 measured butler delivery rate"),
    ("chat/Briefing.kt", "object Briefing",
     "C3 briefing builder exists"),
    ("chat/Briefing.kt", "fun shouldDeliver",
     "C3 first-summon-of-day policy"),
    ("chat/ChatViewModel.kt", "deliverBriefing(force = true)",
     "C3 good-morning intent intercept"),
    ("chat/ChatViewModel.kt", "if (!deliverBriefing(force = false)) startListening()",
     "C3 overlay briefing hook"),

    # Phase C4 — in-app model downloader
    ("web/WebFetcher.kt", "suspend fun download(",
     "C4 resumable download primitive in the only-networking class"),
    ("llm/ModelDownloader.kt", "class ModelDownloader",
     "C4 downloader state machine exists"),
    ("llm/ModelDownloader.kt", "Qwen3-1.7B-Q4_K_M.gguf",
     "C4 curated catalog present"),
    ("ui/ModelDownloadScreen.kt", "fun ModelDownloadDialog",
     "C4 download UI exists"),
    ("core/ServiceLocator.kt", "ModelDownloader(app, web, modelManager)",
     "C4 downloader wired"),
    ("ui/ChatScreen.kt", "ModelDownloadDialog(onDismiss",
     "C4 dialog reachable from model menu"),

    # Phase C5 — citations & fetch log
    ("agent/Orchestrator.kt", "toolsFired.add(call.name)",
     "C5 tool tracking in the agent loop"),
    ("agent/Orchestrator.kt", "source = toolsFired.takeIf",
     "C5 answer carries its tool citations"),
    ("ui/ChatScreen.kt", "via ${msg.source}",
     "C5 citation chips on bubbles"),
    ("ui/ChatScreen.kt", "FetchLogSheet",
     "C5 fetch-log sheet exists"),
    ("web/WebFetcher.kt", "fun accessSnapshot",
     "C5 fetch-log data source"),

    # GBNF grammar-constrained tool calling (P1)
    ("agent/Orchestrator.kt", "TOOL_CALL_GRAMMAR",
     "GBNF tool-call grammar defined"),
    ("agent/Orchestrator.kt", "grammar = TOOL_CALL_GRAMMAR",
     "grammar repair path wired"),
    ("llm/LlamaCppEngine.kt", "grammar: String?,",
     "engine passes grammar to JNI"),
    ("agent/Orchestrator.kt", "repairing tool call",
     "repair status surfaced"),

    # Unit tests (P1: tests + CI)
    ("test/agent/ToolCallParserTest.kt", "class ToolCallParserTest",
     "parser unit tests exist"),
    ("test/web/ParagraphRankerTest.kt", "class ParagraphRankerTest",
     "ranker unit tests exist"),
    ("test/speech/SpeechFormatterTest.kt", "class SpeechFormatterTest",
     "formatter unit tests exist"),
    ("test/agent/WebIntentsTest.kt", "class WebIntentsTest",
     "intent router unit tests exist"),
    ("test/tools/TimeParsingTest.kt", "class TimeParsingTest",
     "time parsing unit tests exist"),
    ("test/llm/ChatTemplateTest.kt", "class ChatTemplateTest",
     "template unit tests exist"),
    ("test/chat/BriefingTest.kt", "class BriefingTest",
     "briefing intent unit tests exist"),

    # Semantic memory (P1: embedding-based recall)
    ("memory/Embedder.kt", "interface Embedder",
     "embedder seam for future transformer upgrade"),
    ("memory/Embedder.kt", "class HashEmbedder(override val dims: Int = 512)",
     "feature-hashing embedder, stopword+stem pipeline"),
    ("memory/MemoryStore.kt", "ALTER TABLE facts ADD COLUMN embedding BLOB",
     "v2 schema migration preserves old facts"),
    ("memory/MemoryStore.kt", "cosine(qVec, vec) + 0.15f",
     "semantic search with lexical blend"),
    ("test/memory/EmbedderTest.kt", "class EmbedderTest",
     "embedding unit tests exist"),

    # Voice packs (P2: Hindi voices, in-app download + activation)
    ("llm/VoicePackManager.kt", "class VoicePackManager",
     "voice pack manager exists"),
    ("llm/VoicePackManager.kt", "extractTarBz2",
     "tar.bz2 extraction with zip-slip guard"),
    ("llm/VoicePackManager.kt", "vits-piper-hi_IN-rohan-medium-int8",
     "Hindi voice in catalog"),
    ("speech/SherpaTtsEngine.kt", "fun activeVoiceDir",
     "voice directory resolution (pref/legacy/pack)"),
    ("speech/SherpaTtsEngine.kt", "fun invalidate()",
     "live voice swap without restart"),
    ("agent/Orchestrator.kt", "replyLanguage() == \"hi\"",
     "Hindi voice biases reply language"),
    ("ui/ModelDownloadScreen.kt", "Voice packs",
     "voice pack section in downloader UI"),

    # Comms tools (P2: call, SMS, calendar — intent-based, zero permissions)
    ("tools/CallTool.kt", "class CallTool",
     "call tool (ACTION_DIAL)"),
    ("tools/SmsTool.kt", "class SmsTool",
     "sms tool (ACTION_SENDTO draft)"),
    ("tools/CalendarTool.kt", "class CalendarTool",
     "calendar tool (ACTION_INSERT event)"),
    ("tools/CalendarTool.kt", "resolveBegin",
     "calendar date math exists"),
    ("test/tools/CommsToolsTest.kt", "class CommsToolsTest",
     "comms tools unit tests exist"),

    # Screen context (P2: on-demand accessibility reading)
    ("accessibility/ScreenReaderService.kt", "class ScreenReaderService",
     "accessibility service exists"),
    ("accessibility/ScreenReaderService.kt", "fun snapshot()",
     "on-demand capture (events ignored)"),
    ("tools/ScreenTool.kt", "class ScreenTool",
     "screen tool registered"),

    # ASR packs (P2: Bengali ear, in-app)
    ("llm/VoicePackManager.kt", "asrCatalog",
     "ASR pack catalog exists"),
    ("llm/VoicePackManager.kt", "activateAsr",
     "ASR activation path"),
    ("llm/VoicePackManager.kt", "bn-vosk",
     "Bengali streaming ASR in catalog"),
    ("speech/SherpaSttEngine.kt", "fun activeAsrDir",
     "ASR dir resolution (pref/legacy/pack)"),
    # ---- Hybrid upgrade (Stark HUD x P2 backend) ------------------------------------
    # Hybrid upgrade — Stark palette (Theme.kt)
    ("ui/Theme.kt", "internal val StarkCyan = Color(0xFF00D9FF)",
     "Stark cyan is electric #00D9FF (not teal)"),
    ("ui/Theme.kt", "internal val StarkCyanGlow = Color(0xFF52FEFE)",
     "Stark glow #52FEFE present"),
    ("ui/Theme.kt", "internal val StarkCyanDeep = Color(0xFF073C4B)",
     "Stark coil fill #073C4B present"),
    ("ui/Theme.kt", "internal val StarkDeepSpace = Color(0xFF050A14)",
     "Stark deep space #050A14 present"),
    ("ui/Theme.kt", "internal val StarkPanel = Color(0xFF0F1E33)",
     "Stark panel #0F1E33 present"),
    ("ui/Theme.kt", "internal val StarkIce = Color(0xFFD6E4F0)",
     "Stark ice text #D6E4F0 present"),
    ("ui/Theme.kt", "internal val StarkDim = Color(0xFF7A8CA3)",
     "Stark muted text #7A8CA3 present"),
    ("ui/Theme.kt", "internal val StarkAlert = Color(0xFFFF3B30)",
     "Stark alert red #FF3B30 present"),
    ("ui/Theme.kt", "fun JarvisTheme(",
     "Stark theme composable present"),

    # Hybrid upgrade — HUD primitives
    ("ui/hud/HudPrimitives.kt", "fun HexGridOverlay(",
     "hex grid overlay primitive"),
    ("ui/hud/HudPrimitives.kt", "fun ScanlinesOverlay(",
     "scanlines overlay primitive"),
    ("ui/hud/HudPrimitives.kt", "fun CornerBrackets(",
     "corner bracket primitive"),
    ("ui/hud/HudPrimitives.kt", "fun CompassStrip(",
     "compass strip primitive"),
    ("ui/hud/HudPrimitives.kt", "fun Vignette(",
     "vignette primitive"),

    # Hybrid upgrade — Stark components
    ("ui/hud/StarkComponents.kt", "fun ToolChip(",
     "ToolChip component present"),
    ("ui/hud/StarkComponents.kt", "fun SourceChip(",
     "SourceChip component present"),
    ("ui/hud/StarkComponents.kt", "fun QuestionCard(",
     "QuestionCard component present"),
    ("ui/hud/StarkComponents.kt", "fun AnswerCard(",
     "AnswerCard component present"),
    ("ui/hud/StarkComponents.kt", "fun PartialTranscript(",
     "PartialTranscript component present"),
    ("ui/hud/StarkComponents.kt", "fun CoreWaveform(",
     "CoreWaveform component present"),

    # Hybrid upgrade — ears telemetry hooks (merge/hybrid-stark-hooks.py)
    ("ui/ChatScreen.kt", "HYBRID-HOOK: ears-telemetry (lab)",
     "hybrid hook: lab ears declaration"),
    ("ui/ChatScreen.kt", 'append("  ·  EAR:$earsLabel")',
     "hybrid hook: EAR segment in lab telemetry"),
    ("ui/ChatScreen.kt", "HYBRID-HOOK: ears-telemetry (helmet)",
     "hybrid hook: helmet ears readout"),

    # Hybrid upgrade — Ears section in the Stark downloader
    ("ui/ModelDownloadScreen.kt", "items(packs.asrCatalog)",
     "Ears section lists the ASR catalog"),
    ("ui/ModelDownloadScreen.kt", "packs.startAsr(entry)",
     "Ears download path wired"),
    ("ui/ModelDownloadScreen.kt", "packs.activateAsr(entry)",
     "Ears activation path wired"),
    ("ui/ModelDownloadScreen.kt", "packs.isAsrInstalled(entry)",
     "Ears installed check wired"),
    ("ui/ModelDownloadScreen.kt", "packs.isActiveAsr(entry)",
     "Ears active state wired"),
    ("ui/ModelDownloadScreen.kt", '"EAR MATRIX"',
     "Stark EAR MATRIX header present"),
    ("ui/ModelDownloadScreen.kt", "Ears — speech recognition",
     "Ears section literal retained"),
    ]

# Patterns that must NOT be present (regression tripwires)
BANNED = [
    ("agent/Orchestrator.kt", "throw\n", "bare 'throw' (Kotlin needs 'throw e')"),
    ("ui/ChatScreen.kt", "arcBorder", "dead arcBorder helper must stay dead"),
    ("ui/ChatScreen.kt", "borderCircle", "dead borderCircle helper must stay dead"),
    ("ui/Theme.kt", "isSystemInDarkTheme", "theme must not reference dark-theme check"),
    ("web/WebFetcher.kt", "isOnline", "dead isOnline() must stay deleted"),
    ("assistant/AssistantActivity.kt", "onBackPressed", "deprecated back-press override"),
    ("agent/WebIntents.kt", r"val has = \{ vararg", "broken inferred vararg lambda"),
]

def strip_kotlin(src):
    """Comment/string stripper with KOTLIN-NESTED block comments."""
    out = []
    i, n = 0, len(src)
    in_line = False
    while i < n:
        c = src[i]
        if in_line:
            if c == "\n":
                in_line = False
                out.append(c)
            i += 1
            continue
        if src.startswith("//", i):
            in_line = True
            i += 2
            continue
        if src.startswith("/*", i):
            depth = 1
            i += 2
            while i < n and depth > 0:
                if src.startswith("/*", i):
                    depth += 1; i += 2; continue
                if src.startswith("*/", i):
                    depth -= 1; i += 2; continue
                i += 1
            continue
        if src.startswith('"""', i):
            j = i + 3
            depth = 0
            while j < n:
                if src[j] == "$" and j + 1 < n and src[j + 1] == "{":
                    depth += 1; j += 2; continue
                if src[j] == "{" and depth > 0:
                    depth += 1
                if src[j] == "}" and depth > 0:
                    depth -= 1
                    if depth == 0:
                        j += 1; continue
                if depth == 0 and src.startswith('"""', j):
                    j += 3; break
                j += 1
            out.append('""'); i = j; continue
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2; continue
                if src[j] == '"':
                    j += 1; break
                if src[j] == "$" and j + 1 < n and src[j + 1] == "{":
                    depth = 1; j += 2
                    while j < n and depth > 0:
                        if src[j] == "{":
                            depth += 1
                        elif src[j] == "}":
                            depth -= 1
                        j += 1
                    continue
                j += 1
            out.append('""'); i = j; continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2; continue
                if src[j] == "'":
                    j += 1; break
                j += 1
            out.append("''"); i = j; continue
        out.append(c)
        i += 1
    return "".join(out)

def comment_depth(src):
    depth, i, n = 0, 0, len(src)
    in_line = False
    while i < n:
        if in_line:
            if src[i] == "\n":
                in_line = False
            i += 1; continue
        if src.startswith("//", i):
            in_line = True; i += 2; continue
        if src.startswith("/*", i):
            depth += 1; i += 2; continue
        if src.startswith("*/", i) and depth > 0:
            depth -= 1; i += 2; continue
        i += 1
    return depth

def main():
    problems = []

    # 1) fix inventory  (patterns are literal substrings unless "re:"-prefixed)
    for rel, pattern, desc in FIXES:
        if pattern.startswith("re:"):
            found = re.search(pattern[3:], read(rel)) is not None
        else:
            found = pattern in read(rel)
        if not found:
            problems.append(f"FIX LOST: {desc} ({rel}: {pattern!r})")

    # 2) banned patterns (same convention)
    for rel, pattern, desc in BANNED:
        if pattern.startswith("re:"):
            found = re.search(pattern[3:], read(rel)) is not None
        else:
            found = pattern in read(rel)
        if found:
            problems.append(f"REGRESSION: {desc} ({rel})")

    # 3) per-file syntax sanity
    for root, _dirs, files in os.walk(SRC):
        for fn in files:
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(root, fn)
            rel = os.path.relpath(p, SRC)
            src = open(p, encoding="utf-8").read()

            d = comment_depth(src)
            if d != 0:
                problems.append(f"SYNTAX: {rel}: comment depth {d} at EOF (nested /* unclosed)")

            s = strip_kotlin(src)
            for a, b in [("{", "}"), ("(", ")"), ("[", "]")]:
                if s.count(a) != s.count(b):
                    problems.append(f"SYNTAX: {rel}: {a}{b} imbalance {s.count(a)}/{s.count(b)}")

            m = re.search(r"^package\s+([\w.]+)", src, re.M)
            expected = os.path.dirname(rel).replace(os.sep, ".")
            if not m or m.group(1) != expected:
                problems.append(f"SYNTAX: {rel}: package != path")

            # Unit-assignment to Thread?: the classic `.start()` tail
            if re.search(r"=\s*Thread\(", s) and re.search(r"Thread\([^;]*\)\s*(\.\w+\s*){0,3}\.start\(\)", s, re.S):
                # heuristically flag `= Thread(...)...start()` patterns
                if re.search(r"=\s*Thread\([\s\S]{0,2000}?\}\s*,\s*\"[^\"]+\"\)\s*\.\s*apply\s*\{[^}]*\}\s*\.\s*start\(\)", s):
                    problems.append(f"SYNTAX: {rel}: Thread assigned the Unit result of .start()")

    if problems:
        print("AUDIT FAILED — do NOT push:")
        for x in problems:
            print("  ✗", x)
        sys.exit(1)
    print(f"AUDIT PASS — {len(FIXES)} fixes present, {len(BANNED)} tripwires clean, syntax OK")

if __name__ == "__main__":
    main()
