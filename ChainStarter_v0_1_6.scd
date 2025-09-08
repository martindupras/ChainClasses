// Filename: ChainStarter_v0_1_6.scd
// Version: v0.1.6
// Purpose: Boots audio, installs OSC routes, edits a "next" chain, switches live, and shows a status GUI.
// Notes:
// - Uses local ~nextChainRef (Option A) to track the ChainManager instance we edit as "next".
// - Slot indexing is 0-based (slot 0 = \testsignal). Names/procs are Symbols (normalized by ChainOSCController).
// - Requires: ChainManager v2.2, ChainController v0.2, ChainOSCController v0.3.8+, ChainStatusUI v0.1.2.
// - UI calls from OSC handlers are deferred to AppClock to satisfy Qt's threading rules.

(
// ---------------------------
// Config & re-run hygiene
// ---------------------------
var priorOsc;
priorOsc = ~osc;
if(priorOsc.notNil) { priorOsc.free; };

if(~starterVerbose.isNil) { ~starterVerbose = true; };

~post = { arg line;
    var s;
    s = line;
    if(~starterVerbose) { ("[ChainStarter] " ++ s.asString).postln; };
};

// Local state for "next" chain and lookup by name
~nextChainRef = nil;
~byName = IdentityDictionary.new;

// Array-based local OSC sender (no varargs syntax)
~send = { arg addr, argsArray;
    var n, args;
    n = NetAddr("127.0.0.1", NetAddr.langPort);
    args = argsArray;
    if(args.isNil) { args = Array.new; };
    args = args.as(Array);
    n.sendMsg(addr, *args);
    ~post.("sent " ++ addr.asString ++ " " ++ args.asString);
};

// Convenience helpers
~forceSwitchFromLocal = {
    var ch;
    ch = ~nextChainRef;
    if(ch.notNil) {
        ~controller.setNext(ch);
        ~controller.switchNow;
        if(~statusUI.notNil) { { ~statusUI.setCurrent(ch); ~statusUI.setNext(nil); }.defer; };
        ~nextChainRef = nil;
        ~controller.status;
    } {
        ~post.("forceSwitch: ~nextChainRef is nil; create or set a next chain first.");
    };
};

~playNdef = { arg nameSym;
    var nm;
    nm = nameSym.asSymbol;
    Ndef(nm).play;
    ~post.("Ndef(" ++ nm.asString ++ ").play issued (direct).");
};

// ---------------------------
// Boot audio (runtime demo)
// ---------------------------
~post.("Booting audio server...");
s.waitForBoot({
    var handlers, resolveByName, installTime;

    ~post.("Server booted. sampleRate:" ++ s.sampleRate.asString ++ " latency:" ++ s.latency.asString);

    // Optional: show available processors if ProcessorLibrary exists (safe via tryPerform)
    if(ProcessorLibrary.notNil) {
        ProcessorLibrary.tryPerform(\global);
        ProcessorLibrary.tryPerform(\describe);
    };

    // Resolve a ChainManager by name Symbol (local cache, then global registry snapshot)
    resolveByName = { arg nameSym;
        var inst, all;
        inst = ~byName[nameSym];
        if(inst.isNil) {
            all = ChainManager.allInstances;
            inst = all.at(nameSym);
        };
        ^inst;
    };  // <-- semicolon here matters

    // Runtime handlers used by ChainOSCController
    handlers = Dictionary[
        \new -> { arg nameSym, slotsInt;
            var chain, finalName, slots;
            slots = slotsInt;
            if(slots.isNil) { slots = 8; };
            chain = ChainManager.new(nameSym, slots);
            finalName = chain.getName;
            ~byName[finalName] = chain;
            ~nextChainRef = chain;
            ~post.("new: created " ++ finalName.asString ++ " slots:" ++ chain.getNumSlots.asString);
            chain.status;
            if(~statusUI.notNil) { { ~statusUI.setNext(chain) }.defer; };
        },
        \setNext -> { arg nameSym;
            var chain;
            chain = resolveByName.(nameSym);
            if(chain.isNil) {
                ~post.("setNext: no chain named " ++ nameSym.asString ++ " found");
                ^nil;
            };
            ~controller.setNext(chain);
            ~nextChainRef = chain;
            ~post.("setNext: next=" ++ chain.getName.asString);
            if(~statusUI.notNil) { { ~statusUI.setNext(chain) }.defer; };
        },
        \add -> { arg slotIndex, procSym;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("add: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil;
            };
            chain.setSlot(slotIndex, procSym);
            ~post.("add: slot:" ++ slotIndex.asString ++ " proc:" ++ procSym.asString ++ " on " ++ chain.getName.asString);
            chain.status;
            if(~statusUI.notNil) { { ~statusUI.setNext(chain) }.defer; };
        },
        \remove -> { arg slotIndex;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("remove: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil;
            };
            chain.setSlot(slotIndex, \bypass);
            ~post.("remove: slot:" ++ slotIndex.asString ++ " -> \\bypass on " ++ chain.getName.asString);
            chain.status;
            if(~statusUI.notNil) { { ~statusUI.setNext(chain) }.defer; };
        },
        \setFrom -> { arg startIndex, procSyms;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("setFrom: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil;
            };
            procSyms.do({ arg p, i;
                var idx;
                idx = startIndex + i;
                chain.setSlot(idx, p);
            });
            ~post.("setFrom: start:" ++ startIndex.asString ++ " procs:" ++ procSyms.asString ++ " on " ++ chain.getName.asString);
            chain.status;
            if(~statusUI.notNil) { { ~statusUI.setNext(chain) }.defer; };
        },
        \switchNow -> {
            var becomingCurrent;
            becomingCurrent = ~nextChainRef;
            ~controller.switchNow;
            if(becomingCurrent.notNil and: { ~statusUI.notNil }) {
                { ~statusUI.setCurrent(becomingCurrent) }.defer;
            };
            ~nextChainRef = nil;
            if(~statusUI.notNil) { { ~statusUI.setNext(nil) }.defer; };
            ~post.("switchNow: performed; nextChainRef cleared");
            ~controller.status;
        }
    ];

    // Create controller (verbose true so you can see status)
    ~controller = ChainController.new(true);
    ~controller.status;

    // Install OSC surface (verbose true)
    ~osc = ChainOSCController.new("starter", ~controller, handlers, true);
    ~post.("OSC routes installed: " ++ ~osc.installedRoutes.asString);

    // Bring up the status UI (create once)
    if(~statusUI.isNil) {
        ~statusUI = ChainStatusUI.new("Chain Status v6", 4);
    };

    // Diagnostics helper
    ~diagnose = {
        var routes, all, nextName;
        routes = ~osc.installedRoutes;
        all = ChainManager.allInstances;
        nextName = "None";
        if(~nextChainRef.notNil) { nextName = ~nextChainRef.getName; };
        ~post.("Routes: " ++ routes.asString);
        ChainRegistry.new(true).listAll;
        ~controller.status;
        ("[ChainStarter] Local nextChainRef: " ++ nextName.asString).postln;
        if(~nextChainRef.notNil) { ~nextChainRef.status; };
        ^this;
    };

    // Scripted demo (optional; adjust the /chain/setNext name to the actual printed name if suffixed)
    ~demo = {
        fork({
            var n;
            n = NetAddr("127.0.0.1", NetAddr.langPort);

            ~post.("Demo: creating \\Edit with 6 slots");
            ~send.("/chain/new", ["Edit", 6]);
            0.25.wait;

            ~post.("Demo: add \\tremolo at slot 1, \\hp at slot 2");
            ~send.("/chain/add", [1, "tremolo"]);
            0.15.wait;
            ~send.("/chain/add", [2, "hp"]);
            0.15.wait;

            ~post.("Demo: setNext to actual printed name (e.g., \\Edit or \\Edit1), then switch");
            ~send.("/chain/setNext", ["Edit"]);
            0.2.wait;
            ~send.("/chain/switchNow");
            0.3.wait;

            ~diagnose.();
        }, SystemClock);
    };

    // Cleanup helper
    ~cleanup = {
        if(~osc.notNil) { ~osc.free; ~osc = nil; };
        ChainRegistry.new(true).freeAll;
        ~byName = IdentityDictionary.new;
        ~nextChainRef = nil;
        if(~statusUI.notNil) { { ~statusUI.setCurrent(nil); ~statusUI.setNext(nil) }.defer; };
        ~post.("Cleanup complete.");
        ^this;
    };

    installTime = Date.localtime;
    ~post.("Starter ready at " ++ installTime.asString);
});
)
