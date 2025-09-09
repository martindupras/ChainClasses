# Chain System (v6) — SuperCollider Classes

A compact, live‑performance–oriented chain system for SuperCollider built around:

- **ChainManager**: builds and runs audio effect chains (Ndef-based).
- **ChainController**: holds `current` and `next` chains and switches them.
- **ChainOSCDispatcher**: the single OSC entry point that **dispatches** incoming OSC to handlers or to the controller.
- **ChainStatusUI**: a read‑only Qt GUI showing **Current** and **Next** chains with per‑slot colours.
- **ChainSwitchScheduler**: schedules controller switches (in seconds or on beat).
- **ProcessorLibrary**: global registry of processors (filters and sources).
- **ChainRegistry**: small utilities to list/inspect/free all chains.
- **ChainManagerDevTools** (optional): advanced teardown helpers for deterministic edits and frees during development.

> These classes are written with **clarity and live stability** in mind: conservative SC syntax, named OSCdefs, UI updates on `AppClock`, and clean separation of concerns.

---

## Table of Contents

1. [Concepts](#concepts)
2. [Class Overview](#class-overview)
3. [Installation & Folder Layout](#installation--folder-layout)
4. [Quick Start](#quick-start)
5. [OSC Surface](#osc-surface)
6. [UI Behaviour](#ui-behaviour)
7. [Extending with Processors](#extending-with-processors)
8. [Scheduling Switches](#scheduling-switches)
9. [Registry Helpers](#registry-helpers)
10. [Dev Tools (Optional)](#dev-tools-optional)
11. [Troubleshooting](#troubleshooting)
12. [Version & Naming Notes](#version--naming-notes)

---

## Concepts

- **Chain** = an `Ndef` with **slots**:
  - **Slot 0** is the **source** (e.g. `\testsignal`, or your input).
  - Slots **1..N-1** are **processors** (filters/effects).
- The **spec** (what you see in the UI) is an `Array<Symbol>`, e.g.

  ```
  [\testsignal, \tremolo, \hp, \bypass, ...]
  ```

- A **processor** is defined by a maker function that returns either:
  - a **UGen function** (for source slot), or
  - a **Dictionary** with `\filter -> { |in, ...| ... }` (for effect slots).
- **Controller** manages `current` and `next` chains; **Dispatcher** converts incoming OSC into actions.

---

## Class Overview

### `ChainManager` (v2.3.1)
- Creates a unique chain/Ndef name (`\chain`, `\chain1`, …).
- Populates slot 0 = `\testsignal` by default; others = `\bypass`.
- Adopts processors from **ProcessorLibrary.global** if present; otherwise uses in‑class fallbacks.
- Key methods:
  - `new(\name, numSlots=8)`
  - `play`, `stop`, `free`, `freeImmediate`
  - `setSlot(index, \procSym)` — sets source or filter at `index`
  - `setChainSpec(arrayOfSymbols)` — replaces the entire spec (clamped to size)
  - `setSlotNoFade`, `setChainSpecNoFade`, `withNoFade`, `withNoLatency`, `withNoLatencyNoFade`, `withBundleNow`
  - `addProcessor(\key, makerFunc)` — extend per chain
  - Accessors: `getName`, `getNumSlots`, `getSpec`
  - Class utilities: `ChainManager.allInstances`, `ChainManager.freeAll`

### `ChainController` (v0.2)
- Holds `currentChain` and `nextChain`.
- `setNext(chain)`, `setCurrent(chain)`, `switchNow()`, `status()`.
- On `switchNow()`:
  - stops old `current` (if any),
  - plays `next`,
  - promotes `next` → `current`.

### `ChainOSCDispatcher` (v0.4.1)
- Installs **named** `OSCdef`s for these routes (payload starts at `msg[1]`):
  - `/demo/ping`
  - `/chain/setNext <name>`
  - `/chain/switchNow`
  - `/chain/new <name> [<slots>]`
  - `/chain/add <slot> <proc>`
  - `/chain/remove <slot>`
  - `/chain/setFrom <start> <list...>`
- Can be constructed with a **handlers** `Dictionary` for app‑specific actions (e.g., updating UI).
- **Robust fallback**: if no handler is given for `/chain/setNext`, it resolves the named chain via `ChainManager.allInstances` and calls `controller.setNext(chain)`.

### `ChainStatusUI` (v0.3.2)
- Read‑only GUI with **Current** and **Next** panels.
- Colours: source (blue), current active (green), current bypass (light grey), next/queued (orange).
- Adaptive slot count; safe repainting (re‑entrancy guard).
- API: `setCurrent(chainOrNil)`, `setNext(chainOrNil)`, `front()`, `free()`.
- Creates a polling routine to refresh periodically.

### `ChainSwitchScheduler`
- Schedules controller switches either:
  - `switchIn(seconds)` — SystemClock delay.
  - `switchOnBeat(tempoClock, beatsAhead=1)` — at next (or next+ahead) whole beat (never “now”).
- Expects a `ChainController` at construction.

### `ProcessorLibrary` (v0.1)
- `classvar < global` instance with built‑in processors: `\testsignal`, `\bypass`, `\hp`, `\lp`, `\tremolo`.
- Methods: `add(\key, func)`, `get(\key)`, `list()`, `describe()`.

### `ChainRegistry`
- `listAll()` — prints sorted chain names.
- `describeAll()` — calls `status()` on each chain.
- `freeAll()` — frees them all.

### `ChainManagerDevTools` (extension, optional)
- Advanced, deterministic dev helpers:
  - `editAndFreeBundled { |f, delta=0.05| ... }`
  - `endThenClear { |waitSeconds=nil| ... }`
  - `editEndThenClearBundled { |f, waitSeconds=nil| ... }`
- Not required for normal live use; keep in a separate file.

---

## Installation & Folder Layout

Place each class in its own `.sc` file (filenames must match class names) inside your SC Extensions path, e.g.:

```
~/Library/Application Support/SuperCollider/Extensions/MDclasses/ChainClasses/
  ChainManager.sc
  ChainController.sc
  ChainOSCDispatcher.sc
  ChainStatusUI.sc
  ChainSwitchScheduler.sc
  ChainRegistry.sc
  ProcessorLibrary.sc
  DevTools/
    ChainManagerDevTools.sc
```

Then **recompile the class library** (Cmd/Ctrl‑Shift‑L).

---

## Quick Start

1. **Boot server** and create the UI:

```supercollider
s.waitForBoot({
  ~ui = ChainStatusUI.new("Chain Status v6", 4);  // read-only status
});
```

2. **Create a chain and play**:

```supercollider
c = ChainManager.new(\Demo, 6);
c.setSlot(0, \testsignal);   // source
c.setSlot(1, \tremolo);
c.setSlot(2, \hp);
c.play;

~ui.setCurrent(c);
```

3. **Switching with a controller**:

```supercollider
~ctl = ChainController.new(true);     // verbose
~ctl.setNext(c);
~ctl.switchNow;                       // c becomes current
~ctl.status;
```

---

## OSC Surface

Create a dispatcher with handlers (recommended):

```supercollider
~ctl = ~ctl ? ChainController.new(true);
~ui  = ~ui  ? ChainStatusUI.new("Chain Status v6", 4);

// App handlers the dispatcher will call
~handlers = Dictionary[
  \new     -> { |nameSym, slots|  var ch = ChainManager.new(nameSym, slots ? 8); ~ui.setNext(ch) },
  \setNext -> { |nameSym|
    var ch = ChainManager.allInstances.at(nameSym);
    if(ch.notNil) { ~ctl.setNext(ch); ~ui.setNext(ch) } { ("No chain "+nameSym).warn };
  },
  \add     -> { |slot, proc|  var ch = ~ctl.tryPerform(\nextChain) ? nil; if(ch.notNil) { ch.setSlot(slot, proc); ~ui.setNext(ch) } },
  \remove  -> { |slot|         var ch = ~ctl.tryPerform(\nextChain) ? nil; if(ch.notNil) { ch.setSlot(slot, \bypass); ~ui.setNext(ch) } },
  \setFrom -> { |start, list|  var ch = ~ctl.tryPerform(\nextChain) ? nil; if(ch.notNil) { list.do{|p,i| ch.setSlot(start+i, p)}; ~ui.setNext(ch) } },
  \switchNow -> { ~ctl.switchNow; ~ui.setCurrent(~ctl.tryPerform(\currentChain)); ~ui.setNext(nil) }
];

~osc = ChainOSCDispatcher.new("main", ~ctl, ~handlers, true);
```

Send messages from SC or another device:

```supercollider
n = NetAddr("127.0.0.1", NetAddr.langPort);
n.sendMsg("/chain/new", "Edit", 6);
n.sendMsg("/chain/add", 1, "tremolo");
n.sendMsg("/chain/add", 2, "hp");
n.sendMsg("/chain/switchNow");
```

---

## UI Behaviour

- The UI paints **Current** (green/grey/blue) and **Next** (orange/grey/blue).
- It adapts to the larger of `current.getNumSlots` or `next.getNumSlots`.
- GUI updates are automatically deferred to `AppClock`.

---

## Extending with Processors

Add a custom source or filter:

```supercollider
// Source example
c.addProcessor(\sawpad, {
  {
    var base=110, det=[1,1.005,0.995], freqs=det*base, w=SinOsc.kr(0.08).range(0.1,0.9);
    var sig = Mix(VarSaw.ar(freqs, 0, w)) * 0.1 ! 2;
    Limiter.ar(sig, 0.95)
  }
});

// Filter example (uses \filter -> { |in, ...| ... })
c.addProcessor(\chorus, {
  \filter -> { |in, rate=0.2, depth=0.008, mix=0.4|
    var max=0.03, m=SinOsc.kr(rate).range(0,depth);
    var d = DelayC.ar(in, max, (m+0.010).clip(0, max));
    XFade2.ar(in, d, (mix.clip(0,1)*2-1))
  }
});
```

Then apply:

```supercollider
c.setSlot(0, \sawpad);
c.setSlot(1, \chorus);
```

---

## Scheduling Switches

```supercollider
~ctl = ~ctl ? ChainController.new(true);
~sch = ChainSwitchScheduler.new(~ctl, true);

// After 4 seconds:
~sch.switchIn(4);

// On the next whole beat (plus optional beatsAhead-1):
t = TempoClock.default;
~sch.switchOnBeat(t, 1);   // next downbeat
```

---

## Registry Helpers

```supercollider
ChainRegistry.new.listAll;      // prints chain names
ChainRegistry.new.describeAll;  // prints each chain's slot spec
ChainRegistry.new.freeAll;      // frees them all
```

---

## Dev Tools (Optional)

If you added `DevTools/ChainManagerDevTools.sc`, you also have:

```supercollider
c.editAndFreeBundled({ /* edits here */ }, 0.05);
c.endThenClear(0.2);
c.editEndThenClearBundled({ /* edits here */ }, 0.2);
```

These are useful for deterministic server scheduling during heavy edits/teardown. They’re **not required** for live use.

---

## Troubleshooting

- **UI shows dashes or wrong count**  
  Ensure you’re updating the **same** UI instance you see on screen:
  
  ```supercollider
  ~ui.currentLabels[0].string_("TEST");  // should change immediately
  ```
  
  If not, close all windows and recreate one UI instance.

- **No sound**  
  Did you `s.boot` and `c.play`? Check `s.meter`. Slot 0 must be a source (e.g., `\testsignal`).

- **OSC not taking effect**  
  Verify the dispatcher is created (`~osc`) and you’re sending to `NetAddr.langPort`. Use the handlers dictionary or rely on the dispatcher’s name‑resolution fallback for `/chain/setNext`.

---

## Version & Naming Notes

- You renamed the scheduler class to `ChainSwitchScheduler` for clarity. If any old scripts still reference `ChainTransitionManager`, create a small deprecated shim file that subclasses `ChainSwitchScheduler` and warns on use, then remove it when migration is complete.
- Default `verbose`:
  - `ChainOSCDispatcher`: **false** by default (`defaultVerbose = false`).
  - `ChainManager`, `ChainController`, `ProcessorLibrary`: default **true** (prints helpful logs).

Happy patching!
