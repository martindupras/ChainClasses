# ChainOSCController_readme.md
**Version:** v0.3.8
**Filename:** `ChainOSCController.sc`
**Scope:** SuperCollider v6 chain system — OSC-driven live-performance controller (test-friendly, audio-free)

---

## Overview

`ChainOSCController` is the **single OSC entry point** for a live-performance audio chain system built around **Ndefs**.
It installs **named `OSCdef` responders** (one per route, per instance), **normalizes payloads**, and **forwards** actions to either:

- **Test-time handler `Function`s** (no audio required), or
- A **runtime `ChainController`** instance (for actual chain switching / editing).

The class is designed for **robust testing**: you can fully exercise it **without booting a server**, and cleanly free only the responders it installed—ideal for hot-reload and smoke tests.

---

## Key Features

- **Minimal OSC surface** (current/next switching + chain editing).
- **Named `OSCdef` per route**, per instance; unique names include the instance `identityHash`.
- **Symbol normalization** for names and processors (e.g., `"B"` → `\B`, `"tremolo"` → `\tremolo`).
- **0-based slot indexing** in the OSC layer (**slot 0 = source**), consistent with ChainManager semantics.
- **Audio-free by default**: handlers let you test without a running server.
- **Verbose-gated logs** with consistent `[ChainOSCController]` prefix.
- **Safe `.free()`** removes only the OSCdefs created by this instance (no global nuking).

---

## Routes

> **Important:** In SuperCollider `OSCdef` handlers, **`msg[0]` is the address**. The **first payload** is **`msg[1]`**.

### Current (implemented)

- `/demo/ping`
  Sanity/logging only.

- `/chain/setNext <name>`
  Normalize `name` to `Symbol` and forward to handler or `controller.setNext(\Name)`.

- `/chain/switchNow`
  Forward to handler or `controller.switchNow()`.

- `/chain/new <name> [<slots>]`
  Normalize `name` to `Symbol`, optional `slots` as `Integer`. For testing, use a handler; in runtime, typically `ChainManager.new(name, slots)` (self-registers).

- `/chain/add <slot> <proc>`
  `slot`: `Integer` (0-based). `proc`: normalized to `Symbol`. For runtime, usually `nextChain.setSlot(slot, \proc)`.

- `/chain/remove <slot>`
  `slot`: `Integer` (0-based). For runtime, usually `nextChain.setSlot(slot, \bypass)`.

- `/chain/setFrom <start> <list...>`
  `start`: `Integer` (0-based). `list`: each normalized to `Symbol`. For runtime, iterate and call `setSlot(start+i, \proc)`.

### Planned (example extension ideas)
- Route flag to **enable/disable edit routes** at construction time.
- Optional **1-based** performer indexing (with internal remap).

---

## Construction & Lifecycle

```supercollider
// Test-time, audio-free usage
(
var handlers, osc, n;
handlers = Dictionary[
    \setNext  -> { arg name; ("setNext -> " ++ name).postln },
    \switchNow -> { "switchNow".postln },
    \new -> { arg name, slots; ("new -> " ++ name ++ " slots:" ++ slots).postln },
    \add -> { arg slot, proc; ("add -> slot:" ++ slot ++ " proc:" ++ proc).postln },
    \remove -> { arg slot; ("remove -> slot:" ++ slot).postln },
    \setFrom -> { arg start, procs; ("setFrom -> start:" ++ start ++ " procs:" ++ procs).postln }
];

osc = ChainOSCController.new("surfaceA", nil, handlers, true);
n = NetAddr("127.0.0.1", NetAddr.langPort);

// send a few
n.sendMsg("/chain/setNext", "B");      // -> \B
n.sendMsg("/chain/switchNow");
n.sendMsg("/chain/new", "Edit", 6);
n.sendMsg("/chain/add", 2, "tremolo");
n.sendMsg("/chain/remove", 3);
n.sendMsg("/chain/setFrom", 1, "hp", "lp", "tremolo");

// cleanup
osc.free;
)
