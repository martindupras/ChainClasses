// ChainStarter_v0_1_2.scd == WORKING ==
// Version: v0.1.2
// Purpose: One-file proof-of-concept starter. Boots audio, installs OSC routes,
//          and wires commands to create/edit a "next" chain and switch it live.
// Notes:
// - Uses local ~nextChainRef (Option A) to track which ChainManager instance is "next".
// - Slot indexing is 0-based (slot 0 = \testsignal). Names/procs are Symbols.
// - Requires your classes on the classpath: ChainManager v2.2, ChainController v0.2, ChainOSCController v0.3.8+
//
// Quick usage (after evaluation):
//   ~send.("/chain/new", ["Edit", 6]);
//   ~send.("/chain/add", [1, "tremolo"]);
//   ~send.("/chain/setNext", ["Edit"]);
//   ~send.("/chain/switchNow");            // no args -> omit second param or pass #[]
//   // Helpers:
//   ~diagnose.(); ~demo.(); ~cleanup.();

(
// ---------------------------
// Config & re-run hygiene
// ---------------------------
var priorOsc, priorPostFn;
priorOsc = ~osc;
if(priorOsc.notNil) { priorOsc.free };  // remove only our previous responders

// Toggleable verbosity for this starter script (not the classes)
~starterVerbose = ~starterVerbose ?? { true };

// Consistent logging helper
~post = { arg line;
    var s;
    s = line;
    if(~starterVerbose) { ("[ChainStarter] " ++ s.asString).postln };
};

// Local state for "next" chain and lookup by name
~nextChainRef = nil;  // ChainManager instance we are editing as "next"
~byName = IdentityDictionary.new; // Symbol -> ChainManager instance (from /chain/new)

// Array-based local OSC sender (no varargs syntax)
~send = { arg addr, argsArray = #[];  // pass args as Array, e.g., ["Edit", 6]
    var n, args;
    n = NetAddr("127.0.0.1", NetAddr.langPort);
    args = argsArray.as(Array);
    n.sendMsg(addr, *args);
    ~post.("sent " ++ addr.asString ++ " " ++ args.asString);
};

// ---------------------------
// Boot audio (runtime demo)
// ---------------------------
~post.("Booting audio server...");
s.waitForBoot({
    // var-first inside this Function
    var handlers, resolveByName, installTime;

    ~post.("Server booted. sampleRate:" ++ s.sampleRate.asString ++ " latency:" ++ s.latency.asString);

    // Optional: inspect available processors if your ProcessorLibrary is present
    // Safe even if ProcessorLibrary is not loaded (tryPerform returns nil).
    ProcessorLibrary.tryPerform(\global).tryPerform(\describe);

    // ---------------------------
    // Create controller and OSC surface with runtime handlers
    // ---------------------------

    // Resolve a ChainManager instance by name Symbol (first by local cache, then class registry)
    resolveByName = { arg nameSym;
        var inst, all;
        inst = ~byName[nameSym];
        if(inst.isNil) {
            all = ChainManager.allInstances;       // snapshot IdentityDictionary
            inst = all.at(nameSym);
        };
        ^inst
    };

    // Runtime handlers that ChainOSCController will call
    handlers = Dictionary[
        \new -> { arg nameSym, slotsInt;
            var chain, finalName, slots;
            slots = slotsInt ?? { 8 };
            chain = ChainManager.new(nameSym, slots);   // self-registers
            finalName = chain.getName;
            ~byName[finalName] = chain;
            ~nextChainRef = chain;  // make newly created chain the current "next" target by default
            ~post.("new: created " ++ finalName.asString ++ " slots:" ++ chain.getNumSlots.asString);
            chain.status;  // prints spec
        },

        \setNext -> { arg nameSym;
            var chain;
            chain = resolveByName.(nameSym);
            if(chain.isNil) {
                ~post.("setNext: no chain named " ++ nameSym.asString ++ " found");
                ^nil
            };
            ~controller.setNext(chain);
            ~nextChainRef = chain;  // keep local pointer consistent with controller
            ~post.("setNext: next=" ++ chain.getName.asString);
        },

        \add -> { arg slotIndex, procSym;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("add: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil
            };
            chain.setSlot(slotIndex, procSym);
            ~post.("add: slot:" ++ slotIndex.asString ++ " proc:" ++ procSym.asString ++ " on " ++ chain.getName.asString);
            chain.status;
        },

        \remove -> { arg slotIndex;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("remove: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil
            };
            chain.setSlot(slotIndex, \bypass);
            ~post.("remove: slot:" ++ slotIndex.asString ++ " -> \\bypass on " ++ chain.getName.asString);
            chain.status;
        },

        \setFrom -> { arg startIndex, procSyms;
            var chain;
            chain = ~nextChainRef;
            if(chain.isNil) {
                ~post.("setFrom: no nextChainRef set yet; call /chain/new or /chain/setNext first");
                ^nil
            };
            procSyms.do({ arg p, i;
                var idx;
                idx = startIndex + i;
                chain.setSlot(idx, p);
            });
            ~post.("setFrom: start:" ++ startIndex.asString ++ " procs:" ++ procSyms.asString ++ " on " ++ chain.getName.asString);
            chain.status;
        },

        \switchNow -> {
            // Promote next -> current and play it; controller will stop current if needed
            ~controller.switchNow;
            // After a switch, controller.nextChain becomes nil; clear our local pointer too
            ~nextChainRef = nil;
            ~post.("switchNow: performed; nextChainRef cleared");
            ~controller.status;
        }
    ];

    // Create controller (verbose true so you can see status)
    ~controller = ChainController.new(true);
    ~controller.status;

    // Install OSC surface (verbose true for readable logs)
    ~osc = ChainOSCController.new("starter", ~controller, handlers, true);
    ~post.("OSC routes installed: " ++ ~osc.installedRoutes.asString);

    // ---------------------------
    // Diagnostics helpers
    // ---------------------------
    ~diagnose = {
        var routes, all, nextName;
        routes = ~osc.installedRoutes;
        all = ChainManager.allInstances;
        nextName = if(~nextChainRef.notNil) { ~nextChainRef.getName } { "None" };
        ~post.("Routes: " ++ routes.asString);
        ChainRegistry.new(true).listAll;  // prints sorted names
        ~controller.status;
        ("[ChainStarter] Local nextChainRef: " ++ nextName.asString).postln;
        if(~nextChainRef.notNil) { ~nextChainRef.status };
        ^this
    };

    // ---------------------------
    // Scripted demo (optional)
    // ---------------------------
    ~demo = {
        fork({
            // var-first
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

            ~post.("Demo: setNext \\Edit, then switchNow");
            ~send.("/chain/setNext", ["Edit"]);
            0.2.wait;
            ~send.("/chain/switchNow");
            0.3.wait;

            ~diagnose.();
        }, SystemClock);
    };

    // ---------------------------
    // Cleanup helper
    // ---------------------------
    ~cleanup = {
        // Remove only our OSC responders and free all chain instances
        if(~osc.notNil) { ~osc.free; ~osc = nil };
        ChainRegistry.new(true).freeAll;
        ~byName = IdentityDictionary.new;
        ~nextChainRef = nil;
        ~post.("Cleanup complete.");
        ^this
    };

    installTime = Date.localtime;
    ~post.("Starter ready at " ++ installTime.asString);
});
)
