// Filename: ChainOSCController.sc
// Version: v0.3.4
// Change notes:
// - v0.3.4: installRoute now ALWAYS (re)binds the OSCdef so hot reloads update handlers reliably.
//           This prevents stale handlers after class recompiles or test reruns.
// - v0.3.3: Added /chain/new, /chain/add, /chain/remove, /chain/setFrom; Symbol normalization; named OSCdefs; .free()
// - v0.3.2: Known-good base with minimal routes and payload normalization to Symbol.

ChainOSCController {
    classvar <defaultVerbose;
    var <>verbose;
    var <namePrefix;
    var <defNames;     // Dictionary: routeSym -> defName Sym (the OSCdef key)
    var <handlerMap;   // Dictionary: actionSym -> Function (optional)
    var <>controller;   // optional ChainController (for runtime wiring)

    *initClass {
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
        this.verbose = verbose;
        this.controller = controller;
        defNames = Dictionary.new;
        handlerMap = Dictionary.new;
        nm = name;
        if(nm.isNil) { nm = "osc" };
        namePrefix = ("ChainOSCController%" ++ this.identityHash.asString ++ "%" ++ nm.asString);
        if(handlers.notNil) { this.setHandlers(handlers) };
        this.installRoutes;
        ^this
    }

    setHandlers { arg handlers;
        var dict;
        dict = handlers;
        if(dict.isKindOf(Dictionary).not) {
            this.log("setHandlers: non-Dictionary provided, ignoring");
            ^this
        };
        handlerMap = dict.copy;
        ^this
    }

    defNameFor { arg routeSym;
        ^defNames[routeSym]
    }

    installedRoutes { ^defNames.keys }

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
        ^this
    }

    installRoutes {
        this.installRoute('/demo/ping');
        this.installRoute('/chain/setNext');
        this.installRoute('/chain/switchNow');
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
        handler = this.makeHandlerFor(routeSym);

        // Always (re)bind so changes take effect after recompile/rerun.
        OSCdef(defName, handler, routeStr);
        defNames[routeSym] = defName;

        this.log("installed/updated " ++ routeSym.asString ++ " -> " ++ defName.asString);
        ^this
    }

    makeDefName { arg routeSym;
        var base, rs, safe;
        base = namePrefix;
        rs = routeSym.asString;
        safe = rs.replace("/", "_");
        ^(base ++ "_" ++ safe).asSymbol
    }

    makeHandlerFor { arg routeSym;
        var fn;
        fn = { arg msg, time, addr, recvPort;
            var r;
            r = routeSym;
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
                this.log("handler: unknown route " ++ r.asString);
            }}}}}}};
        };
        ^fn
    }

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
        if(nameSym.isNil) { this.log("setNext: missing name"); ^this };
        this.log("/chain/setNext " ++ nameSym.asString);
        cb = handlerMap[\setNext];
        if(cb.notNil) { cb.(nameSym) } {
            if(controller.notNil and: { controller.respondsTo(\setNext) }) { controller.setNext(nameSym) };
        };
        ^this
    }

    onSwitchNow { arg msg, time, addr, recvPort;
        var cb;
        this.log("/chain/switchNow");
        cb = handlerMap[\switchNow];
        if(cb.notNil) { cb.() } {
            if(controller.notNil and: { controller.respondsTo(\switchNow) }) { controller.switchNow };
        };
        ^this
    }

    onNew { arg msg, time, addr, recvPort;
        var nameAny, nameSym, slots, cb;
        nameAny = msg.size > 0.if({ msg[0] }, { nil });
        nameSym = this.toSymbol(nameAny);
        slots = msg.size > 1.if({ msg[1].asInteger }, { nil });
        if(nameSym.isNil) { this.log("new: missing name"); ^this };
        this.log("/chain/new " ++ nameSym.asString ++ (slots.notNil.if({ " " ++ slots.asString }, { "" })));
        cb = handlerMap[\new];
        if(cb.notNil) { cb.(nameSym, slots) };
        ^this
    }

    onAdd { arg msg, time, addr, recvPort;
        var slotAny, procAny, slot, procSym, cb;
        slotAny = msg.size > 0.if({ msg[0] }, { nil });
        procAny = msg.size > 1.if({ msg[1] }, { nil });
        if(slotAny.isNil or: { procAny.isNil }) { this.log("add: requires <slot> <proc>"); ^this };
        slot = slotAny.asInteger; // 0-based
        procSym = this.toSymbol(procAny);
        this.log("/chain/add slot:" ++ slot.asString ++ " proc:" ++ procSym.asString);
        cb = handlerMap[\add];
        if(cb.notNil) { cb.(slot, procSym) };
        ^this
    }

    onRemove { arg msg, time, addr, recvPort;
        var slotAny, slot, cb;
        slotAny = msg.size > 0.if({ msg[0] }, { nil });
        if(slotAny.isNil) { this.log("remove: requires <slot>"); ^this };
        slot = slotAny.asInteger; // 0-based
        this.log("/chain/remove slot:" ++ slot.asString);
        cb = handlerMap[\remove];
        if(cb.notNil) { cb.(slot) };
        ^this
    }

    onSetFrom { arg msg, time, addr, recvPort;
        var startAny, start, rest, procSyms, cb;
        if(msg.size < 2) { this.log("setFrom: requires <start> <list...>"); ^this };
        startAny = msg[0];
        start = startAny.asInteger;
        rest = msg.copyRange(1, msg.size-1);
        procSyms = rest.collect({ arg it; this.toSymbol(it) });
        this.log("/chain/setFrom start:" ++ start.asString ++ " procs:" ++ procSyms.asString);
        cb = handlerMap[\setFrom];
        if(cb.notNil) { cb.(start, procSyms) };
        ^this
    }

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
