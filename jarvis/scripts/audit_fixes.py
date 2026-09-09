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

def read(rel):
    with open(os.path.join(PKG, rel), encoding="utf-8") as f:
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
]

# Patterns that must NOT be present (regression tripwires)
BANNED = [
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
