# v6 Chain System for Live Guitar Processing (SuperCollider)

**Date:** 2025‑09‑05

## Overview
This repository contains a SuperCollider (sclang) system for live performance that builds and switches audio‑effect chains using Ndefs. It targets a hexaphonic (6‑channel) guitar pickup in the long run, with OSC‑only performer control and a simple on‑stage display of the current and next chains.

The v6 system focuses on:
- Dynamic chains: slot‑based chains built from a small ProcessorLibrary (e.g., \hp, \lp, 	remolo, ypass, 	estsignal)
- Robust control: a central ChainController with ChainTransitionManager for beat‑ or delay‑aligned switching
- Remote control (OSC): receiving /chain/new, /chain/setNext, /chain/switchNow, etc.
- Simple GUI: shows Current and Next chain (performer does not touch the computer)
- Structure tests: language‑side harnesses to validate the API/behavior (no audio required)

Note: Structure tests are intended to validate class contracts and state transitions, not sound quality. Audio can be tested separately once the server is booted and your graph is connected to real inputs/outputs.

## Architecture
### Components
- **ChainManager**: Builds and manages an Ndef chain of N slots (default 8). Slot 0 is always a source (	estsignal by default), slots 1..N‑1 are filters (e.g., \hp, \lp, 	remolo, ypass). Provides helpers for no‑fade / no‑latency edits and bundled operations.
- **ChainController**: The runtime controller that owns currentChain and nextChain, implements switchNow and setNext, and cooperates with GUI/OSC/transition scheduling.
- **ChainTransitionManager**: Schedules controller.switchNow either after a delay (switchIn(seconds)) or on a tempo clock beat (switchOnBeat(clock, beatsAhead)), with future‑safe beat scheduling.
- **ChainOSCController**: Registers OSC routes (/chain/new, /chain/setNext, /chain/switchNow) and forwards to the ChainController.
- **OSCCommandRouter**: A minimal router that listens to /chain/switchNow and calls the supplied controller. Useful for smoke tests and simple setups.
- **ChainGUIManager**: A compact GUI that displays Current Chain and Next Chain by reading from a controller with accessors (currentChain / nextChain).
- **ChainRegistry**: A thin façade over ChainManager.allInstances for listing, describing, and freeing all chains. Returns sorted keys for deterministic logs/UI.
- **ChainSignalsManager**: Describes I/O shape and mixdown policy: \mono, \stereo, \sixout. Intended to keep I/O expectations clear (hex‑in → FOH stereo or six‑out).
- **ProcessorLibrary**: A small, extensible library of processor descriptors / makers used by chains to populate slots.

### Typical runtime flow
1. A ChainController holds currentChain and nextChain.
2. Performer’s device sends OSC to ChainOSCController (or minimal OSCCommandRouter).
3. ChainTransitionManager schedules clean switching (switchIn or switchOnBeat).
4. ChainGUIManager reflects current/next names from the controller.
5. ChainSignalsManager determines output shape (e.g., stereo FOH).

## What’s implemented (now)
### Chain building & editing (ChainManager)
- Slot‑based Ndef chains (slot 0 is always a \source)
- Automatic coercion: unknown source → 	estsignal, unknown filter → ypass
- Helpers: setSlotNoFade, setChainSpecNoFade, withNoLatency, withBundleNow, freeImmediate, etc.

### Control & switching
- ChainController v0.2 with accessors for GUI & modules, plus switchNow, setNext, status
- ChainTransitionManager v0.1c:
  - switchIn(seconds) uses SystemClock.sched
  - switchOnBeat(clock, beatsAhead) uses TempoClock.beats.ceil logic and always schedules in the future

### OSC control
- ChainOSCController v0.1 listens for /chain/new, /chain/setNext, /chain/switchNow
- OSCCommandRouter v0.1 listens for /chain/switchNow (minimal smoke‑test router)

### GUI
- ChainGUIManager v0.1 displays Current and Next by reading controller accessors

### I/O policy
- ChainSignalsManager v0.1 exposes desired shape (\mono | \stereo | \sixout) and reports channel counts

### Testing
- A full structure harness (tests/test_structures_v0.3.1.scd) covering all modules
- Focused transition tests (tests/test_transitions2.scd, optional edgebeats)
- Sanity checker (tests/test_sanity_versions.scd)
- Mock classes for language‑side testing without audio (MDMockController, MDMockRegistry)

## Files
### Runtime classes
- ChainManager.sc — Ndef chain manager (v2.2 banner)
- ChainController.sc — Recommended: v0.2 (add <currentChain, <nextChain accessors)
- ChainTransitionManager.sc — v0.1c (future‑safe beat scheduling)
- ChainOSCController.sc — v0.1 (OSC: /chain/new, /chain/setNext, /chain/switchNow)
- OSCCommandRouter.sc — v0.1 (OSC: /chain/switchNow, minimal)
- ChainGUIManager.sc — v0.1 (Current/Next display via controller accessors)
- ChainRegistry.sc — v0.1 (lists/describe/free all chains)
- ChainSignalsManager.sc — v0.1 (I/O shape: mono/stereo/sixout)
- ProcessorLibrary.sc — v0.1 (basic processors: \hp, \lp, 	remolo, ypass, 	estsignal)

### Test utilities (classes)
- MDMockController.sc — minimal controller for tests (<didSwitch flag, switchNow, setNext)
- MDMockRegistry.sc — minimal registry for tests (addChain, addCount, lastChain)

### Test harnesses (scripts)
- tests/test_structures_v0.3.1.scd — Main structure harness (no audio, optional OSC reset)
- tests/test_transitions2.scd — Transition checks with tempo‑aware wait
- tests/test_transitions_edgebeats.scd — (optional) downbeat edge scheduling check
- tests/test_sanity_versions.scd — Verify class presence and version banners
- tests/test_runner_all.scd — (optional) run all tests in sequence

Tip: Keep backups (e.g., .zip, older .sc variants) outside the Extensions path so the class loader only sees the active .sc files you intend to compile.

## Setup & Running
1. Place the *.sc class files somewhere in your SuperCollider Extensions path (e.g., ~/Library/Application Support/SuperCollider/Extensions/MDclasses/ChainClasses/ on macOS).
2. Recompile Class Library after any change to class files.
3. Open and run:
   - tests/test_sanity_versions.scd
   - tests/test_structures_v0.3.1.scd (main harness)
   - tests/test_transitions2.scd if you want the focused timing check
4. The main harness can optionally clear OSC responders at start/end (see top toggles).

The structure tests do not require the audio server. If you want to exercise play/stop, boot the server and either enable the guarded section or run an audio‑specific test.

## Coding conventions (v6)
- sclang only; no quarks required
- var declarations at the top of every Function/block
- Lowercase names for ~env vars (e.g., ~failcount)
- No C‑style ternary (?:) or stray ! suffixes
- Do not call Event slots like methods; use Functions or classes for assertions/mocks
- Use accessors (e.g., var <name / var <>name) when other modules need reads/writes
- OSC lifecycle: prefer named OSCdef + per‑instance free when hot‑reloading

## Known gaps & next steps
- Convert OSCCommandRouter (and optionally ChainOSCController) to named OSCdefs with free() for safe hot‑reloads.
- Normalize ProcessorLibrary descriptors to a uniform shape with ole, \label, \defaults for easier GUI/automation.
- Add a small GlobalConfig (verbosity 0–4, displayVersion v6) and route prints/UI through it.
- (Optional) Have ChainController call gui.updateDisplay automatically in setNext/switchNow (opt‑in).
- Define default mixdown policy in ChainSignalsManager (e.g., hex → stereo for FOH).
- Add a simple status/ping OSC route and a health indicator in the GUI.
