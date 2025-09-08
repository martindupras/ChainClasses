// Filename: ChainOSCController.sc
// Version: v0.3.3
// Change notes:
// - v0.3.3: Confirms /demo/ping, /chain/setNext, /chain/switchNow. Adds /chain/new, /chain/add, /chain/remove, /chain/setFrom.
//            Uses named OSCdef per route with per-instance def names; stores them for safe free(). Dictionary + Symbol keys.
//            Test-friendly via optional handler Functions (no Server required). Verbose-gated logs.
//            OSC slot indexing is 0-based by default (slot 0 = source), matching ChainManager semantics.
// - v0.3.2: Known-good base with minimal routes and payload normalization to Symbol.

ChainOSCController {
    classvar <defaultVerbose;
    var <>verbose;
    var <namePrefix;
    var <defNames;     // Dictionary: routeSym -> defName Sym (the OSCdef key)
    var <handlerMap;   // Dictionary: actionSym -> Function (optional)
    var <>controller;   // optional ChainController (for runtime wiring)

    *initClass {
        // Keep class-level default conservative
        defaultVerbose = false;
    }

    *new { arg name, controller = nil, handlers = nil, verbose;
        var v;
        v = verbose;
        if(v.isNil) { v = defaultVerbose };
        ^super.new.init(name, controller, handlers, v)
    }

    init { arg name, controller, handlers, verbose;
        var nm;
        verbose = verbose; // assign ivar
        this.verbose = verbose;

        this.controller = controller;
        defNames = Dictionary.new;
        handlerMap = Dictionary.new;

        // Prefix for def names; identityHash is valid here (method context).
        nm = name;
        if(nm.isNil) { nm = "osc" };
        namePrefix = ("ChainOSCController%" ++ this.identityHash.asString ++ "%" ++ nm.asString);

        if(handlers.notNil) {
            this.setHandlers(handlers);
        };

        this.installRoutes;
        ^this
    }

    // --- Public API ---

    setHandlers { arg handlers;
        var dict;
        dict = handlers;
        if(dict.isKindOf(Dictionary).not) {
            this.log("setHandlers: non-Dictionary provided, ignoring");
            ^this
        };
        handlerMap = dict.copy; // shallow copy
        ^this
    }

    defNameFor { arg routeSym;
        var r;
        r = routeSym;
        if(r.notNil and: { defNames.notNil }) {
            ^defNames[r]
        } {
            ^nil
        }
    }

    installedRoutes {
        ^defNames.keys
    }

    free {
        var removed;
        removed = List.new;
        defNames.do { arg defSym, routeSym;
            var od;
            od = OSCdef(defSym);
            if(od.notNil) {
                od.free;
                removed.add(defSym);
            }
        };
        if(removed.size > 0) {
            this.log("free: removed % OSCdef(s)".format(removed.size));
        } {
            this.log("free: nothing to remove");
        };
        // Do not clear defNames so tests can check after free; they can see OSCdef(defSym) is nil.
        // Keep handlerMap intact; user may inspect state post-free.
        ^this
    }

    // --- Private: route install ---

    installRoutes {
        // Route keys stored as Symbol
        this.installRoute('/demo/ping');
        this.installRoute('/chain/setNext');
        this.installRoute('/chain/switchNow');

        // Edit routes (opt-in for tests/clients)
        this.installRoute('/chain/new');
        this.installRoute('/chain/add');
        this.installRoute('/chain/remove');
        this.installRoute('/chain/setFrom');

        ^this
    }

    installRoute { arg routeStr;
        var routeSym, defName, handler;
        routeSym = routeStr.asSymbol;
        defName = this.makeDefName(routeSym);

        // Avoid double-install in hot reload
        if(OSCdef(defName).notNil) {
            this.log("installRoute: reusing existing OSCdef " ++ defName.asString);
            defNames[routeSym] = defName;
            ^this
        };

        handler = this.makeHandlerFor(routeSym);

        OSCdef(defName, handler, routeSym.asString);
        defNames[routeSym] = defName;

        this.log("installed " ++ routeSym.asString ++ " -> " ++ defName.asString);
        ^this
    }

    makeDefName { arg routeSym;
        var base, rs, safe;
        base = namePrefix;
        rs = routeSym.asString;
        // replace '/' with '_' for a safe Symbol name
        safe = rs.replace("/", "_");
        ^(base ++ "_" ++ safe).asSymbol
    }

    // Build per-route OSCdef function
    makeHandlerFor { arg routeSym;
        var fn;
        fn = { arg msg, time, addr, recvPort;
            var r;
            r = routeSym;
            // Ensure var-first; then route match
            if(r == '/demo/ping'.asSymbol) {
                this.onPing(msg, time, addr, recvPort);
            } {
            if(r == '/chain/setNext'.asSymbol) {
                this.onSetNext(msg, time, addr, recvPort);
            } {
            if(r == '/chain/switchNow'.asSymbol) {
                this.onSwitchNow(msg, time, addr, recvPort);
            } {
            if(r == '/chain/new'.asSymbol) {
                this.onNew(msg, time, addr, recvPort);
            } {
            if(r == '/chain/add'.asSymbol) {
                this.onAdd(msg, time, addr, recvPort);
            } {
            if(r == '/chain/remove'.asSymbol) {
                this.onRemove(msg, time, addr, recvPort);
            } {
            if(r == '/chain/setFrom'.asSymbol) {
                this.onSetFrom(msg, time, addr, recvPort);
            } {
                // Should never happen
                this.log("handler: unknown route " ++ r.asString);
            }}}}}}};
        };
        ^fn
    }

    // --- Route handlers (each var-first) ---

    onPing { arg msg, time, addr, recvPort;
        var cb;
        this.log("/demo/ping " ++ msg.asString);
        cb = handlerMap[\ping];
        if(cb.notNil) { cb.(msg, time, addr, recvPort) };
    }

    onSetNext { arg msg, time, addr, recvPort;
        var nameAny, nameSym, cb;
        nameAny = msg.size > 0.if({ msg[0] }, { nil });
        nameSym = this.toSymbol(nameAny);
        if(nameSym.isNil) {
            this.log("setNext: missing name");
            ^this
        };
        this.log("/chain/setNext " ++ nameSym.asString);

        cb = handlerMap[\setNext];
        if(cb.notNil) {
            cb.(nameSym);
        } {
            if(controller.notNil and: { controller.respondsTo(\setNext) }) {
                controller.setNext(nameSym);
            } {
                // no-op in tests
            }
        };
        ^this
    }

    onSwitchNow { arg msg, time, addr, recvPort;
        var cb;
        this.log("/chain/switchNow");
        cb = handlerMap[\switchNow];
        if(cb.notNil) {
            cb.();
        } {
            if(controller.notNil and: { controller.respondsTo(\switchNow) }) {
                controller.switchNow;
            } {
                // no-op in tests
            }
        };
        ^this
    }

    onNew { arg msg, time, addr, recvPort;
        var nameAny, nameSym, slots, cb;
        nameAny = msg.size > 0.if({ msg[0] }, { nil });
        nameSym = this.toSymbol(nameAny);
        slots = msg.size > 1.if({ msg[1].asInteger }, { nil });

        if(nameSym.isNil) {
            this.log("new: missing name");
            ^this
        };

        this.log("/chain/new " ++ nameSym.asString ++ (slots.notNil.if({ " " ++ slots.asString }, { "" })));

        cb = handlerMap[\new];
        if(cb.notNil) {
            cb.(nameSym, slots);
        } {
            // Runtime wiring could be: ChainManager.new(nameSym, slots)
            // Not invoked here to keep tests audio-free.
        };
        ^this
    }

    onAdd { arg msg, time, addr, recvPort;
        var slotAny, procAny, slot, procSym, cb;
        slotAny = msg.size > 0.if({ msg[0] }, { nil });
        procAny = msg.size > 1.if({ msg[1] }, { nil });

        if(slotAny.isNil or: { procAny.isNil }) {
            this.log("add: requires <slot> <proc>");
            ^this
        };

        slot = slotAny.asInteger; // 0-based slot index (slot 0 = source)
        procSym = this.toSymbol(procAny);

        this.log("/chain/add slot:" ++ slot.asString ++ " proc:" ++ procSym.asString);

        cb = handlerMap[\add];
        if(cb.notNil) {
            cb.(slot, procSym);
        } {
            // Runtime wiring: controller.nextChain.setSlot(slot, procSym)
        };
        ^this
    }

    onRemove { arg msg, time, addr, recvPort;
        var slotAny, slot, cb;
        slotAny = msg.size > 0.if({ msg[0] }, { nil });

        if(slotAny.isNil) {
            this.log("remove: requires <slot>");
            ^this
        };

        slot = slotAny.asInteger; // 0-based
        this.log("/chain/remove slot:" ++ slot.asString);

        cb = handlerMap[\remove];
        if(cb.notNil) {
            cb.(slot);
        } {
            // Runtime wiring: controller.nextChain.setSlot(slot, \bypass)
        };
        ^this
    }

    onSetFrom { arg msg, time, addr, recvPort;
        var startAny, start, rest, procSyms, cb;
        if(msg.size < 2) {
            this.log("setFrom: requires <start> <list...>");
            ^this
        };

        startAny = msg[0];
        start = startAny.asInteger; // 0-based
        rest = msg.copyRange(1, msg.size-1);
        procSyms = rest.collect({ arg it; this.toSymbol(it) });

        this.log("/chain/setFrom start:" ++ start.asString ++ " procs:" ++ procSyms.asString);

        cb = handlerMap[\setFrom];
        if(cb.notNil) {
            cb.(start, procSyms);
        } {
            // Runtime wiring: procSyms.do {|p, i| controller.nextChain.setSlot(start + i, p) }
        };
        ^this
    }

    // --- Utilities ---

    toSymbol { arg x;
        var res;
        if(x.isNil) { ^nil };
        if(x.isSymbol) { ^x };
        res = x.asString.asSymbol;
        ^res
    }

    log { arg s;
        var str;
        if(verbose.not) { ^this };
        str = "[ChainOSCController] " ++ s.asString;
        str.postln;
        ^this
    }
}
