#!/usr/bin/env python3
"""
hybrid-stark-hooks — hybrid-upgrade step 3 (rebuilt v3).

The Stark HUD session (01a0889a) was written against the pre-P2 backend, so
after merging origin/main the HUD still had no idea the machine grew a pair of
ears: main's P2 "ASR language packs" work added `VoicePackManager.asrCatalog`
(English + Bengali streaming zipformers) and the SherpaSttEngine resolution
chain, but nothing in the Stark UI surfaced it.

This script stitches the two halves together with three idempotent,
needle-anchored inserts into ChatScreen.kt:

  1. StarkLabConsole  — resolve the active ASR ("ears") pack into `earsLabel`
  2. StarkLabConsole  — append `EAR:<label>` to the lab telemetry line
  3. HelmetHUD        — an `EAR  <LABEL>` readout in the helmet telemetry stack

Hooks 1 and 2 are deliberately separate inserts into the same function, and
hook 3 declares its own `earsLabel` — `StarkLabConsole` (L116) and `HelmetHUD`
(L555) are *different* composables, so there is no redeclaration clash.

Every hook is guarded by a `HYBRID-HOOK:` marker, so re-running is a no-op.

Usage:  python3 merge/hybrid-stark-hooks.py <repo-root>
"""

import io
import os
import sys

CHATSCREEN = os.path.join(
    "jarvis", "app", "src", "main", "java",
    "com", "jarvis", "assistant", "ui", "ChatScreen.kt",
)

# Shared lines — resolves the active ears pack out of the merged backend.
# NB: plain .replace, not .format — the Kotlin body is full of literal braces.
EARS_DECL = [
    "// HYBRID-HOOK: ears-telemetry (@WHERE@) — active ASR pack from the merged P2 language packs",
    "val earsLabel = ServiceLocator.voicePacks.asrCatalog",
    "    .firstOrNull { ServiceLocator.voicePacks.isActiveAsr(it) }?.langLabel ?: \"default\"",
]


def ears_decl(where):
    return [ln.replace("@WHERE@", where) for ln in EARS_DECL]

HOOKS = [
    {
        "name": "StarkLabConsole — ears declaration",
        "file": CHATSCREEN,
        # The lab telemetry block; declaration must land before the Text() below.
        "needle": "// telemetry line — MCU alt / rng style crud",
        "marker": "// HYBRID-HOOK: ears-telemetry (lab)",
        "lines": ears_decl("lab"),
    },
    {
        "name": "StarkLabConsole — EAR segment in telemetry",
        "file": CHATSCREEN,
        # Inside buildString { ... }; sits between TTS and the RNG flourish.
        "needle": "append(\"  ·  TTS:",
        "marker": "// HYBRID-HOOK: ears-telemetry (lab — readout)",
        "lines": [
            "// HYBRID-HOOK: ears-telemetry (lab — readout)",
            "append(\"  ·  EAR:$earsLabel\")",
        ],
    },
    {
        "name": "HelmetHUD — EAR readout",
        "file": CHATSCREEN,
        # Helmet telemetry stack, just above the ALT/MACH/RNG/PWR line.
        "needle": "// secondary telemetry — alt / rng mock like film",
        "marker": "// HYBRID-HOOK: ears-telemetry (helmet)",
        "lines": ears_decl("helmet") + [
            "Text(",
            "    \"EAR  ${earsLabel.uppercase()}\",",
            "    fontFamily = FontFamily.Monospace,",
            "    fontSize = 8.5.sp,",
            "    letterSpacing = 1.sp,",
            "    color = StarkDim.copy(alpha = 0.65f),",
            "    modifier = Modifier.padding(top = 2.dp),",
            ")",
        ],
    },
]


def indent_of(line):
    return line[: len(line) - len(line.lstrip())]


def apply_hook(root, hook):
    """Return 1 if an insert was made, 0 if already present."""
    path = os.path.join(root, hook["file"])
    if not os.path.isfile(path):
        sys.exit("hybrid-stark-hooks: missing target file: %s" % path)

    with io.open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")

    text = "\n".join(lines)
    if hook["marker"] in text:
        print("  skip   %-46s (already applied)" % hook["name"])
        return 0

    hits = [i for i, ln in enumerate(lines) if hook["needle"] in ln]
    if len(hits) != 1:
        sys.exit(
            "hybrid-stark-hooks: needle %r matched %d line(s) in %s — refusing "
            "to guess" % (hook["needle"], len(hits), hook["file"])
        )

    at = hits[0] + 1
    pad = indent_of(lines[hits[0]])
    block = [pad + ln if ln.strip() else ln for ln in hook["lines"]]
    lines[at:at] = block

    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))

    print("  insert %-46s -> %s:%d" % (hook["name"], hook["file"], at + 1))
    return 1


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    print("hybrid-stark-hooks: root=%s" % root)
    applied = sum(apply_hook(root, h) for h in HOOKS)
    print("%d insert(s) applied" % applied)


if __name__ == "__main__":
    main()
