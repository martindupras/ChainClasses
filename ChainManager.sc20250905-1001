/*  ChainManager.sc (v2) - MD 20250905-1001
    Manages an Ndef-based audio-effect chain with N slots (default 8).
    - Slot 0: source (pulsed test signal, stereo)
    - Slots 1..(n-1): filters/processors (default: \bypass)
    - Output: last slot goes to main bus via Ndef.play (normal behavior)

    ─────────────────────────────────────────────────────────────────────────────
    OVERVIEW OF IMPORTANT VARIABLES (quick map)
    ─────────────────────────────────────────────────────────────────────────────
    classvar < registry
        IdentityDictionary mapping a unique chain name Symbol -> ChainManager instance.
        Why? Allows discovering, enumerating, and managing multiple chains at runtime.

    classvar < nameCounter
        Integer used to generate unique names when user does not provide one.
        Each time a collision is found, this counter increments to suffix the base.

    var < name
        Symbol used as the Ndef key for this chain (e.g., \myChain).
        This is the stable identifier you use with Ndef(name).

    var < numSlots
        Integer number of slots in this chain (>= 2). Slot 0 is the source.

    var < slotSpec
        Array of Symbols describing each slot’s role, e.g.
        [ \testsignal, \bypass, \lp, \hp, \tremolo, \bypass, ... ].
        Changing an entry and calling setSlot (or setChainSpec) updates the Ndef chain.

    var < procDefs
        IdentityDictionary mapping a processor Symbol -> a maker Function.
        The maker Function returns a NodeProxy-compatible function for a slot:
           - Source: { ... }                      // no 'in' arg
           - Filter: \filter -> { |in| ... }      // has 'in' arg
        (See bottom of the file for a strategy to keep these in external files.)

    var < isPlaying
        Boolean flag mirroring whether Ndef(name) is currently playing.

    var <> fadeTime
        Float forwarded to Ndef(name).fadeTime. Controls crossfade time when replacing slots.

    ─────────────────────────────────────────────────────────────────────────────
    NOTES ON EXTENSIBILITY (summarised; see inline TODO stubs further down)
    ─────────────────────────────────────────────────────────────────────────────
    - External processor packs:
      Store processor descriptors as small .scd files organized by category
      (e.g., ".../Processors/filters/*.scd"). Each file returns an IdentityDictionary
      of { \symbol -> descriptor }, where a descriptor can include channel counts.

      Suggested descriptor keys:
        (
          kind: \source | \filter,
          ins:  Int (e.g., 1, 2, 6),
          outs: Int (e.g., 1, 2, 6),
          make: { ... } // the same maker function shape as today
        )
      The chain can later adapt linkages if ins/outs differ using a small adapter
      policy (mix-down, upmix/duplication, or channel-select).
*/

ChainManager : Object {

    // ─────────────────────────────────────────────────────────────────────────
    // CLASS STATE
    // ─────────────────────────────────────────────────────────────────────────
    classvar < registry;      // Q: What does 'registry' keep? Why?
                              // A: Keeps every ChainManager instance, keyed by 'name' Symbol.
                              //    It lets you enumerate, find, stop, or free multiple chains at runtime
                              //    without manually tracking variables.

    classvar < nameCounter;   // Q: NameCounter counts names of what?
                              // A: Counts how many *auto-generated* chain names we’ve created,
                              //    so we can append a unique integer suffix when needed.

    // ─────────────────────────────────────────────────────────────────────────
    // INSTANCE STATE
    // ─────────────────────────────────────────────────────────────────────────
    var < name;               // Ndef key / chain name Symbol (unique per instance)
    var < numSlots;           // number of slots (>= 2)
    var < slotSpec;           // Symbols describing per-slot processors
    var < procDefs;           // processor registry: \symbol -> maker Function
    var < isPlaying;          // whether Ndef(name) is currently playing
    var <> fadeTime = 0.1;    // Ndef crossfade time for slot replacement

    // ─────────────────────────────────────────────────────────────────────────
    // CLASS LIFECYCLE & DISCOVERY
    // ─────────────────────────────────────────────────────────────────────────
    *initClass {
        registry = IdentityDictionary.new;
        nameCounter = 0;
    }

    *uniqueName { |base=\chain|
        var candidate;
        candidate = base.asSymbol;
        // ensure unique among existing chain names in 'registry'
        while { registry.includesKey(candidate) } {
            nameCounter = nameCounter + 1;
            candidate = (base.asString ++ nameCounter.asString).asSymbol;
        };
        ^candidate  // returns a unique Symbol safe to use as the chain/Ndef name
    }

    *new { |name=nil, numSlots=8|
        var instance;
        instance = super.new.init(name, numSlots);
        ^instance  // returns the new ChainManager instance
    }

    *allInstances {
        // Q: What does allInstances do? Why do we need it?
        // A: Returns a shallow copy of the { name -> ChainManager } registry.
        //    Useful for diagnostics, bulk stop/free, or listing chains in a GUI.
        ^registry.copy
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

    // ─────────────────────────────────────────────────────────────────────────
    // INSTANCE INIT
    // ─────────────────────────────────────────────────────────────────────────
    init { |nm=nil, nSlots=8|
        var baseName, finalName;

        baseName = if (nm.isNil) { "chain" } { nm.asString };
        // Defensive: if class vars were cleared, ensure we can run
        if (registry.isNil) { registry = IdentityDictionary.new };

        finalName = this.class.uniqueName(baseName.asSymbol);

        numSlots  = nSlots.max(2);  // at least 2 (source + one filter)
        name      = finalName.asSymbol;
        isPlaying = false;

        this.defineDefaultProcDefs;

        // Default chain: slot 0 is the source; all other slots are \bypass
        slotSpec = Array.fill(numSlots, { \bypass });
        slotSpec.put(0, \testsignal);

        // Build Ndef slots from slotSpec
        this.buildChain;

        // Track this instance globally for discovery & management
        registry.put(name, this);
        ^this
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROCESSOR DEFINITIONS (current in-class storage)
    // See “External Processor Strategy” below for a way to load these from files.
    // ─────────────────────────────────────────────────────────────────────────
    defineDefaultProcDefs {
        var hp, lp, trem, bypass, testsig;

        // High-pass (gentle)
        hp = {
            \filter -> { |in, freq=300|
                var cut;
                cut = freq.clip(10, 20000);
                HPF.ar(in, cut)
            }
        };

        // Low-pass (gentle)
        lp = {
            \filter -> { |in, freq=2000|
                var cut;
                cut = freq.clip(50, 20000);
                LPF.ar(in, cut)
            }
        };

        // Fast, very audible "chopping" tremolo
        trem = {
            \filter -> { |in, rate=12, depth=1.0, duty=0.5|
                var chop, amount, dutyClamped;
                dutyClamped = duty.clip(0.05, 0.95);
                amount      = depth.clip(0, 1);
                chop        = LFPulse.kr(rate.max(0.1), 0, dutyClamped).lag(0.001);  // crisp, not clicky
                in * (chop * amount + (1 - amount))
            }
        };

        // Bypass (passthrough)
        bypass = {
            \filter -> { |in| in }
        };

        // Pulsed test signal (stereo), not obnoxious
        testsig = {
            {
                var trig, env, freqs, tone, pan, amp;
                trig  = Impulse.kr(2);                // 2 pulses per second
                env   = Decay2.kr(trig, 0.01, 0.15);  // short 'ping'
                freqs = [220, 330];
                tone  = SinOsc.ar(freqs, 0, 0.15).sum;
                amp   = env * 0.9;
                pan   = [-0.2, 0.2];
                [tone * amp, tone * amp] * (1 + pan)  // subtle L/R emphasis
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

    // ─────────────────────────────────────────────────────────────────────────
    // BUILD THE NDEF CHAIN FROM slotSpec
    // ─────────────────────────────────────────────────────────────────────────
    buildChain {
        var index, symbol, maker;

        // Q: How does buildChain work?
        // A: 1) Set Ndef(name).fadeTime.
        //    2) Resolve slot 0's symbol (e.g., \testsignal) to a maker function and
        //       assign it directly as a source function (no 'in' arg).
        //    3) For every subsequent slot i >= 1, resolve its symbol to a maker function
        //       that returns a filter form (\filter -> { |in| ... }) and assign it to Ndef(name)[i].
        //    4) If a symbol cannot be found in procDefs, fall back to \testsignal (slot 0)
        //       or \bypass (filters) so the chain remains valid.

        // 1) proxy fade time
        Ndef(name).fadeTime = fadeTime;

        // 2) slot 0 (source)
        symbol = slotSpec[0];
        maker  = procDefs.at(symbol);
        if (maker.isNil) {
            // Fallback to a known-good source if symbol is unknown
            maker = procDefs[\testsignal];
            slotSpec.put(0, \testsignal);
        };
        Ndef(name)[0] = maker.value;

        // 3) slots 1..(numSlots-1) — filters (passthrough by default)
        index = 1;
        while { index < numSlots } {
            symbol = slotSpec[index];
            maker  = procDefs.at(symbol);
            if (maker.isNil) { maker = procDefs[\bypass] };
            Ndef(name)[index] = maker.value;  // \filter -> { |in| ... }
            index = index + 1;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────
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

    free {
        var removed;
        removed = registry.removeAt(name);  // removed is the façade instance removed, or nil if absent
        Ndef(name).clear;                   // clear slots; standard behavior, no server syncs
        isPlaying = false;
        ^removed  // returns the removed instance (or nil) so callers can confirm disposal
    }

    setSlot { |index, symbol|
        var slotIndex, sym, maker;

        slotIndex = index.asInteger.clip(0, numSlots - 1);
        sym       = symbol.asSymbol;

        slotSpec.put(slotIndex, sym);
        maker = procDefs.at(sym);

        if (maker.isNil) {
            // Unknown symbol: slot 0 -> source fallback; filters -> bypass
            if (slotIndex == 0) {
                maker = procDefs[\testsignal];
                slotSpec.put(0, \testsignal);
            } {
                maker = procDefs[\bypass];
            };
        };

        if (slotIndex == 0) {
            Ndef(name)[0] = maker.value;      // source
        } {
            Ndef(name)[slotIndex] = maker.value;  // filter
        };

        ^this
    }

    setChainSpec { |arrayOfSymbols|
        var spec, symbol;

        spec = arrayOfSymbols.as(Array);

        // Fit to numSlots (pad with \bypass, or trim excess)
        if (spec.size < numSlots) {
            spec = spec ++ Array.fill(numSlots - spec.size, { \bypass });
        } {
            if (spec.size > numSlots) {
                spec = spec.copyRange(0, numSlots - 1);
            };
        };

        // Force slot 0 to be a valid source
        symbol = spec[0];
        if (procDefs.at(symbol).isNil or: { symbol != \testsignal }) {
            spec.put(0, \testsignal);
        };

        slotSpec = spec;
        this.buildChain;
        ^this
    }

    // Generic parameter setter (live controls forwarded to current slots)
    set { |...paramPairs|
        var pairs;
        pairs = paramPairs;
        if (pairs.size.odd) {
            "ChainManager[%]: odd number of set() items ignored tail.".format(name).warn;
        };
        Ndef(name).set(*pairs);
        ^this
    }

    // Introspection: posts and returns a copy of the current slotSpec
    status {
        var info;
        info = "ChainManager[%] slots: %".format(name, slotSpec);
        info.postln;
        ^slotSpec.copy  // returns a copy so callers don't mutate internal state
    }

    // Help: posts usage summary and returns this for chaining
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
            "Free:      c.free;   // removes from registry and clears Ndef",
            "",
            "Available processors: " ++ keys.asString
        ];

        lines.do { |line| line.postln };
        ^this
    }

    // Accessors
    getName { ^name }             // returns the Symbol used for the Ndef key
    getNumSlots { ^numSlots }     // returns the number of slots in this chain
    getSpec { ^slotSpec.copy }    // returns a copy of slotSpec (protects internal state)

    // ─────────────────────────────────────────────────────────────────────────
    // EXTERNAL PROCESSOR STRATEGY (Design sketch + loader stub)
    // ─────────────────────────────────────────────────────────────────────────
    /*
        GOAL:
          - Keep processor definitions in separate files, organized by category.
          - Allow processors with different I/O sizes (e.g., 6-in/6-out, 6-in/2-out, 1-in/1-out).
          - Load at runtime without recompiling the class library.

        LAYOUT SUGGESTION:
          <ProjectRoot>/Processors/
             filters/
               hp.scd
               lp.scd
               tremolo.scd
             dynamics/
               compressor.scd
             sources/
               testsignal.scd
             spatial/
               upmix6to2.scd
               fold2to1.scd
             ...

        FILE CONTRACT (hp.scd example):
          // The file returns an IdentityDictionary whose values are *descriptors*:
          (
              \hp: (
                   category: \filters,
                   kind: \filter,            // or \source
                   ins: 2,                   // expected input channels
                   outs: 2,                  // output channels
                   make: {                   // maker: returns slot function
                       \filter -> { |in, freq=300|
                           HPF.ar(in, freq.clip(10, 20000))
                       }
                   }
              )
          )

        DESCRIPTOR FORMAT:
          (
            category: \filters | \sources | \spatial | ...
            kind: \source | \filter
            ins: Int
            outs: Int
            make: Function               // same maker shape we use today
          )

        CHAIN ADAPTER POLICY (future work):
          When a filter descriptor outs != next filter ins, adapt automatically:
            - Downmix (e.g., Mix for N->1), upmix (duplication/pan), or select channels.
            - Provide policy hooks: \downmixStrategy, \upmixStrategy, etc.
          For now, we keep today’s “natural expansion” (typical 2-in/2-out), and we accept
          multi-channel processors later by adding a small adapter stage in buildChain.

        LOADING MECHANICS:
          - Use thisProcess.interpreter.executeFile(path) to evaluate .scd and get its return value.
          - Validate the result is an IdentityDictionary of descriptors, then merge into procDefs.
          - Namespacing: we could prefix symbols with categories (optional) or keep flat.
    */

    loadProcessorsFromDir { |dirPath|
        var folder, files, i, path, result, mergedCount;

        folder = PathName(dirPath);
        if (folder.notNil and: { folder.isFolder }) {
            files = folder.files.select { |pn| pn.fileName.endsWith(".scd") };
            i = 0; mergedCount = 0;

            while { i < files.size } {
                path = files[i].fullPath;
                // Evaluate the .scd; the file should return an IdentityDictionary of descriptors
                result = thisProcess.interpreter.executeFile(path);

                // Accept either:
                //   A) descriptor dicts: \name -> (kind:..., ins:..., outs:..., make: { ... })
                //   B) legacy maker functions: \name -> ( { \filter -> { |in| ... } } )
                if (result.isKindOf(IdentityDictionary)) {
                    result.keysValuesDo { |key, val|
                        if (val.isKindOf(Function)) {
                            // legacy: store as-is
                            procDefs.put(key.asSymbol, val);
                            mergedCount = mergedCount + 1;
                        } {
                            if (val.isKindOf(Dictionary) and: { val[\make].isKindOf(Function) }) {
                                // descriptor form: store the maker for now (compatible),
                                // and keep the descriptor on the side if you want:
                                procDefs.put(key.asSymbol, val[\make]);

                                // TODO (future): keep a parallel descriptor table to track ins/outs
                                // e.g., ~procMeta = IdentityDictionary[ \hp -> (ins:2, outs:2, ...) ]
                                // and teach buildChain to insert channel adapters between slots.
                                mergedCount = mergedCount + 1;
                            };
                        };
                    };
                };

                i = i + 1;
            };

            ("Loaded/merged % processor(s) from: ".format(mergedCount) ++ dirPath).postln;
        } {
            ("No such folder: " ++ dirPath).warn;
        };

        ^this
    }
}
