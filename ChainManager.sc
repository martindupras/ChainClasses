/*  ChainManager.sc  — v2.1
    Manages an Ndef-based audio-effect chain with N slots (default 8).

    Key improvements vs. v2:
      - setSlot() keeps slotSpec coherent (unknown filters -> \bypass).
      - free(): disables crossfades and hard-clears Ndef (quiet teardown).
      - Helpers for deterministic edits/teardown:
          setSlotNoFade(index, symbol)
          setChainSpecNoFade(arrayOfSymbols)
          withNoFade({ ... })
          withNoLatency({ ... })
          withNoLatencyNoFade({ ... })
          withBundleNow({ ... })
          withNoLatencyNoFadeBundled({ ... })
          freeImmediate()

    Style: var-first in every Function/closure; lowercase descriptive names; accessors spaced.
*/

ChainManager : Object {

    // ----- class state -----
    classvar < registry;      // IdentityDictionary[nameSymbol -> instance]
    classvar < nameCounter;   // Integer counter for auto-unique names

    // ----- instance state -----
    var < name;               // Symbol used as Ndef key
    var < numSlots;           // Integer >= 2
    var < slotSpec;           // Array of Symbols, e.g. [\testsignal, \bypass, ...]
    var < procDefs;           // IdentityDictionary[\symbol -> maker Function]
    var < isPlaying;          // Boolean
    var <> fadeTime = 0.1;    // Float; forwarded to Ndef(name).fadeTime

    *initClass {
        registry = IdentityDictionary.new;
        nameCounter = 0;
    }

    *uniqueName { |base = \chain|
        var candidate;
        candidate = base.asSymbol;
        while { registry.includesKey(candidate) } {
            nameCounter = nameCounter + 1;
            candidate = (base.asString ++ nameCounter.asString).asSymbol;
        };
        ^candidate   // -> a unique Symbol for chain/Ndef name
    }

    *new { |name = nil, numSlots = 8|
        var instance;
        instance = super.new.init(name, numSlots);
        ^instance    // -> new ChainManager instance
    }

    *allInstances {
        ^registry.copy   // -> copy of { name -> instance } for discovery/GUI/bulk ops
    }

    *freeAll {
        var keys, i;
        keys = registry.keys;
        i = 0;
        while { i < keys.size } {
            registry[keys[i]].free;
            i = i + 1;
        };
        ^this
    }

    // ----- init -----
    init { |nm = nil, nSlots = 8|
        var baseName, finalName;

        baseName = if (nm.isNil) { "chain" } { nm.asString };
        if (registry.isNil) { registry = IdentityDictionary.new };
        finalName = this.class.uniqueName(baseName.asSymbol);

        numSlots  = nSlots.max(2);
        name      = finalName.asSymbol;
        isPlaying = false;

        this.defineDefaultProcDefs;

        // Default: slot 0 is source; others bypass
        slotSpec = Array.fill(numSlots, { \bypass });
        slotSpec.put(0, \testsignal);

        this.buildChain;

        registry.put(name, this);
        ^this
    }

    // ----- processor definitions (in-class; can be externalized later) -----
    defineDefaultProcDefs {
        var hp, lp, trem, bypass, testsig;

        // High-pass (gentle)
        hp = {
            \filter -> { |in, freq = 300|
                var cut;
                cut = freq.clip(10, 20000);
                HPF.ar(in, cut)
            }
        };

        // Low-pass (gentle)
        lp = {
            \filter -> { |in, freq = 2000|
                var cut;
                cut = freq.clip(50, 20000);
                LPF.ar(in, cut)
            }
        };

        // Chopping tremolo
        trem = {
            \filter -> { |in, rate = 12, depth = 1.0, duty = 0.5|
                var chop, amount, dutyClamped;
                dutyClamped = duty.clip(0.05, 0.95);
                amount      = depth.clip(0, 1);
                chop        = LFPulse.kr(rate.max(0.1), 0, dutyClamped).lag(0.001);
                in * (chop * amount + (1 - amount))
            }
        };

        // Bypass
        bypass = { \filter -> { |in| in } };

        // Pulsed test signal (stereo), not obnoxious
        testsig = {
            {
                var trig, env, freqs, tone, pan, amp;
                trig  = Impulse.kr(2);
                env   = Decay2.kr(trig, 0.01, 0.15);
                freqs = [220, 330];
                tone  = SinOsc.ar(freqs, 0, 0.15).sum;
                amp   = env * 0.9;
                pan   = [-0.2, 0.2];
                [tone * amp, tone * amp] * (1 + pan)
            }
        };

        procDefs = IdentityDictionary.new;
        procDefs.put(\hp,         hp);
        procDefs.put(\lp,         lp);
        procDefs.put(\tremolo,    trem);
        procDefs.put(\bypass,     bypass);
        procDefs.put(\testsignal, testsig);
    }

    addProcessor { |keySymbol, makerFunc|
        var key;
        key = keySymbol.asSymbol;
        procDefs.put(key, makerFunc);
        ^this
    }

    // ----- build chain from slotSpec -----
    buildChain {
        var index, symbol, maker;

        // 1) fade time
        Ndef(name).fadeTime = fadeTime;

        // 2) slot 0: source
        symbol = slotSpec[0];
        maker  = procDefs.at(symbol);
        if (maker.isNil) {
            maker = procDefs[\testsignal];
            slotSpec.put(0, \testsignal);
        };
        Ndef(name)[0] = maker.value;

        // 3) slots 1..end: filters
        index = 1;
        while { index < numSlots } {
            symbol = slotSpec[index];
            maker  = procDefs.at(symbol);
            if (maker.isNil) { maker = procDefs[\bypass] };
            Ndef(name)[index] = maker.value;
            index = index + 1;
        };
    }

    // ----- transport -----
    play {
        var wasPlaying;
        wasPlaying = isPlaying;
        if (wasPlaying.not) {
            Ndef(name).play;
            isPlaying = true;
        };
        ^this
    }

    stop {
        var wasPlaying;
        wasPlaying = isPlaying;
        if (wasPlaying) {
            Ndef(name).stop;
            isPlaying = false;
        };
        ^this
    }

    // Quiet free: force no-fade and hard-clear (no stop/release)
    free {
        var removedInstance, previousFadeTime;

        previousFadeTime = Ndef(name).fadeTime;
        Ndef(name).fadeTime = 0.0;
        Ndef(name).clear;
        Ndef(name).fadeTime = previousFadeTime;

        removedInstance = registry.removeAt(name);
        isPlaying = false;
        ^removedInstance  // -> removed instance or nil
    }

    // Immediate, bundled free to avoid any delayed messages
    freeImmediate {
        var removedInstance, previousFadeTime, server;

        server = Ndef(name).server ? Server.default;
        previousFadeTime = Ndef(name).fadeTime;

        server.makeBundle(0.0, {
            Ndef(name).fadeTime = 0.0;
            Ndef(name).clear;
        });

        Ndef(name).fadeTime = previousFadeTime;

        removedInstance = registry.removeAt(name);
        isPlaying = false;
        ^removedInstance  // -> removed instance or nil
    }

    // ----- slot edits -----
    setSlot { |index, symbol|
        var slotIndex, sym, maker;

        slotIndex = index.asInteger.clip(0, numSlots - 1);
        sym       = symbol.asSymbol;

        slotSpec.put(slotIndex, sym);
        maker = procDefs.at(sym);

        if (maker.isNil) {
            if (slotIndex == 0) {
                maker = procDefs[\testsignal];
                slotSpec.put(0, \testsignal);
            } {
                maker = procDefs[\bypass];
                slotSpec.put(slotIndex, \bypass);  // keep spec truthful for filters
            };
        };

        if (slotIndex == 0) {
            Ndef(name)[0] = maker.value;     // source
        } {
            Ndef(name)[slotIndex] = maker.value;   // filter
        };

        ^this
    }

    setChainSpec { |arrayOfSymbols|
        var spec, symbol;

        spec = arrayOfSymbols.as(Array);

        if (spec.size < numSlots) {
            spec = spec ++ Array.fill(numSlots - spec.size, { \bypass });
        } {
            if (spec.size > numSlots) {
                spec = spec.copyRange(0, numSlots - 1);
            };
        };

        symbol = spec[0];
        if (procDefs.at(symbol).isNil or: { symbol != \testsignal }) {
            spec.put(0, \testsignal);
        };

        slotSpec = spec;
        this.buildChain;
        ^this
    }

    // ----- helpers: no-fade / no-latency / bundling -----
    setSlotNoFade { |index, symbol|
        var previousFadeTime;
        previousFadeTime = Ndef(name).fadeTime;
        Ndef(name).fadeTime = 0.0;
        this.setSlot(index, symbol);
        Ndef(name).fadeTime = previousFadeTime;
        ^this
    }

    setChainSpecNoFade { |arrayOfSymbols|
        var previousFadeTime;
        previousFadeTime = Ndef(name).fadeTime;
        Ndef(name).fadeTime = 0.0;
        this.setChainSpec(arrayOfSymbols);
        Ndef(name).fadeTime = previousFadeTime;
        ^this
    }

    withNoFade { |function|
        var previousFadeTime;
        previousFadeTime = Ndef(name).fadeTime;
        Ndef(name).fadeTime = 0.0;
        function.value;
        Ndef(name).fadeTime = previousFadeTime;
        ^this
    }

    withNoLatency { |function|
        var server, previousLatency;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        server.latency = 0.0;
        function.value;
        server.latency = previousLatency;
        ^this
    }

    withNoLatencyNoFade { |function|
        var server, previousLatency, previousFadeTime;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        previousFadeTime = Ndef(name).fadeTime;
        server.latency = 0.0;
        Ndef(name).fadeTime = 0.0;
        function.value;
        Ndef(name).fadeTime = previousFadeTime;
        server.latency = previousLatency;
        ^this
    }

    withBundleNow { |function|
        var server;
        server = Ndef(name).server ? Server.default;
        server.makeBundle(0.0, { function.value });
        ^this
    }

    withNoLatencyNoFadeBundled { |function|
        var server, previousLatency, previousFadeTime;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        previousFadeTime = Ndef(name).fadeTime;

        server.latency = 0.0;
        Ndef(name).fadeTime = 0.0;

        server.makeBundle(0.0, {
            function.value;
        });

        Ndef(name).fadeTime = previousFadeTime;
        server.latency = previousLatency;
        ^this
    }


	// Schedule a single server bundle at 'delta' seconds from now
// that: (a) disables fade/latency, (b) applies 'editFunction', and (c) clears immediately.
// This prevents any delayed /n_set or /n_free after clear.
editAndFreeBundled { |editFunction, delta = 0.05|
    var server, previousLatency, previousFadeTime;

    server = Ndef(name).server ? Server.default;
    previousLatency = server.latency;
    previousFadeTime = Ndef(name).fadeTime;

    // Set no-latency / no-fade for the *inside* of the bundle
    server.latency = 0.0;
    Ndef(name).fadeTime = 0.0;

    server.makeBundle(delta, {
        // Apply user edits (slot swaps, setChainSpec, etc.)
        editFunction.value;

        // Hard clear now, in the same timestamp
        Ndef(name).clear;
    });

    // Restore settings immediately
    Ndef(name).fadeTime = previousFadeTime;
    server.latency = previousLatency;

    // Update registry/local state now (the server will clear at 'delta')
    registry.removeAt(name);
    isPlaying = false;

    ^this
}


	// Schedules a release (stop) now, then clears after a small wait
// - waitSeconds: optional; defaults to (server.latency + 0.05).max(0.05)
endThenClear { |waitSeconds = nil|
    var server, previousLatency, previousFadeTime, waitTime;

    server = Ndef(name).server ? Server.default;
    previousLatency = server.latency;
    previousFadeTime = Ndef(name).fadeTime;

    // choose a safe delay so 'now' bundle is never 'late' and release precedes clear
    waitTime = if (waitSeconds.isNil) { (previousLatency + 0.05).max(0.05) } { waitSeconds.max(0.01) };

    // 1) schedule a 'now' bundle: no-fade release
    server.makeBundle(0.0, {
        Ndef(name).fadeTime = 0.0;  // no crossfade
        Ndef(name).stop;            // release gate (no fade)
    });

    // 2) schedule the clear in the near future (after release lands)
    server.makeBundle(waitTime, {
        Ndef(name).clear;           // hard clear (no /n_set after this)
    });

    // restore local settings immediately (server work is queued)
    Ndef(name).fadeTime = previousFadeTime;

    // update registry/state after the scheduled clear
    AppClock.sched(waitTime, {
        registry.removeAt(name);
        isPlaying = false;
        ^nil
    });

    ^this
}

// Perform edits and release in one 'now' bundle, then clear after a small wait.
// - editFunction: slot edits (setSlot, setChainSpec, etc.). Keep them server-light.
// - waitSeconds: optional; default same as above.
editEndThenClearBundled { |editFunction, waitSeconds = nil|
    var server, previousLatency, previousFadeTime, waitTime;

    server = Ndef(name).server ? Server.default;
    previousLatency = server.latency;
    previousFadeTime = Ndef(name).fadeTime;

    waitTime = if (waitSeconds.isNil) { (previousLatency + 0.05).max(0.05) } { waitSeconds.max(0.01) };

    // 1) 'now' bundle: disable fades, do edits, then release
    server.makeBundle(0.0, {
        Ndef(name).fadeTime = 0.0;
        editFunction.value;   // e.g. setSlot(2, \iDoNotExist), etc.
        Ndef(name).stop;      // schedule release (no fade)
    });

    // 2) future bundle: clear after release
    server.makeBundle(waitTime, {
        Ndef(name).clear;
    });

    // restore local fadeTime
    Ndef(name).fadeTime = previousFadeTime;

    // language-side cleanup after clear is sent
    AppClock.sched(waitTime, {
        registry.removeAt(name);
        isPlaying = false;
        ^nil
    });

    ^this
}

    // ----- params, status, help -----
    set { |...paramPairs|
        var pairs;
        pairs = paramPairs;
        if (pairs.size.odd) {
            ("ChainManager[%]: odd number of set() items ignored tail.").format(name).warn;
        };
        Ndef(name).set(*pairs);
        ^this
    }

    status {
        var info;
        info = "ChainManager[%] slots: %".format(name, slotSpec);
        info.postln;
        ^slotSpec.copy   // -> copy so callers can’t mutate internal state
    }

    help {
        var keys, lines;
        keys = procDefs.keys.asArray.sort;
        lines = [
            "ChainManager.help",
            "----------------------------------------",
            "Create:    c = ChainManager.new(\\myChain, 8);",
            "Play/Stop: c.play;  c.stop;",
            "Inspect:   c.status;  // prints and returns copy of slot spec",
            "Set slot:  c.setSlot(3, \\hp);  c.setSlot(4, \\lp);  c.setSlot(5, \\tremolo);",
            "Set chain: c.setChainSpec([\\testsignal, \\hp, \\tremolo, \\lp, \\bypass, \\bypass, \\bypass, \\bypass]);",
            "Params:    Ndef(c.getName).set(\\freq, 600, \\rate, 14, \\depth, 1.0);",
            "",
            "Helpers: setSlotNoFade, setChainSpecNoFade, withNoFade, withNoLatency,",
            "         withNoLatencyNoFade, withBundleNow, withNoLatencyNoFadeBundled, freeImmediate",
            "Available processors: " ++ keys.asString
        ];
        lines.do { |line| line.postln };
        ^this
    }

    // ----- simple accessors -----
    getName { ^name }
    getNumSlots { ^numSlots }
    getSpec { ^slotSpec.copy }
}
