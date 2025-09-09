// Filename: ChainOSCDispatcherDG.sc
// Version: v0.4.1-DG1
// Purpose: Delegate-driven OSC dispatcher (explicit app protocol).
// The dispatcher parses & normalizes payloads then calls delegate methods.
// Protocol (delegate implements):
//   onPing(msg, time, addr, recvPort)
//   onSetNext(nameSym)
//   onSwitchNow()
//   onNew(nameSym, slots)
//   onAdd(slotIndex, procSym)
//   onRemove(slotIndex)
//   onSetFrom(startIndex, procList)
//
// Style: var-first; conservative syntax; no '??' operator.

ChainOSCDispatcherDG : Object {
    classvar <defaultVerbose;
    var <verbose;
    var <namePrefix;
    var <defNames;  // Dictionary[routeSymbol -> OSCdef key]
    var <delegate;

    *initClass { defaultVerbose = false }

    *new { arg name, delegate, verbose;
        ^super.new.init(name, delegate, verbose)
    }

    init { arg nameArg, delegateArg, verboseArg;
        var nameString;
        verbose  = if(verboseArg.isNil) { defaultVerbose } { verboseArg };
        delegate = delegateArg;
        defNames = Dictionary.new;

        nameString = nameArg;
        if(nameString.isNil) { nameString = "osc" };
        namePrefix = ("ChainOSCDispatcherDG%" ++ this.identityHash.asString ++ "%" ++ nameString.asString);

        this.installRoutes;
        ^this
    }

    installRoutes {
        this.install("/demo/ping",       \onPing);
        this.install("/chain/setNext",   \onSetNext);
        this.install("/chain/switchNow", \onSwitchNow);
        this.install("/chain/new",       \onNew);
        this.install("/chain/add",       \onAdd);
        this.install("/chain/remove",    \onRemove);
        this.install("/chain/setFrom",   \onSetFrom);
        ^this
    }

    install { arg routeString, methodSymbol;
        var defName, routeSymbol, fn;
        routeSymbol = routeString.asSymbol;
        defName     = this.makeDefName(routeSymbol);

        fn = { arg msg, time, addr, recvPort;
            var args;
            // FIX: pass time/addr/recvPort through to normalization
            args = this.normalizeArgsFor(methodSymbol, msg, time, addr, recvPort);
            if(delegate.notNil and: { delegate.respondsTo(methodSymbol) }) {
                delegate.performList(methodSymbol, args);
            } {
                this.log(("delegate missing % for route %").format(methodSymbol, routeString));
            };
            ^this
        };

        OSCdef(defName, fn, routeString);
        defNames[routeSymbol] = defName;
        this.log("installed/updated " ++ routeString ++ " -> " ++ defName.asString);
        ^this
    }

    // FIX: accept time/addr/recvPort as parameters
    normalizeArgsFor { arg methodSymbol, msg, time, addr, recvPort;
        var size, nameSym, slotIndex, procSym, startIndex, rest, syms;
        size = msg.size;

        if(methodSymbol == \onPing) {
            ^[msg, time, addr, recvPort]  // delegate can inspect full OSC envelope
        };

        if(methodSymbol == \onSetNext) {
            nameSym = if(size > 1) { this.toSymbol(msg[1]) } { nil };
            ^[nameSym]
        };

        if(methodSymbol == \onSwitchNow) { ^[] };

        if(methodSymbol == \onNew) {
            nameSym = if(size > 1) { this.toSymbol(msg[1]) } { nil };
            ^[nameSym, if(size > 2) { msg[2].asInteger } { nil }]
        };

        if(methodSymbol == \onAdd) {
            slotIndex = if(size > 1) { msg[1].asInteger } { nil };
            procSym   = if(size > 2) { this.toSymbol(msg[2]) } { nil };
            ^[slotIndex, procSym]
        };

        if(methodSymbol == \onRemove) {
            slotIndex = if(size > 1) { msg[1].asInteger } { nil };
            ^[slotIndex]
        };

        if(methodSymbol == \onSetFrom) {
            startIndex = if(size > 1) { msg[1].asInteger } { nil };
            rest = if(size > 2) { msg.copyRange(2, size - 1) } { [] };
            syms = rest.collect({ arg item; this.toSymbol(item) });
            ^[startIndex, syms]
        };

        ^[]
    }

    makeDefName { arg routeSymbol;
        ^(namePrefix ++ "_" ++ routeSymbol.asString.replace("/", "_")).asSymbol
    }

    defNameFor { arg routeSymbol; ^defNames[routeSymbol] }
    installedRoutes { ^defNames.keys }

    free {
        var removed;
        removed = List.new;
        defNames.do { arg defName, routeSymbol;
            var d;
            d = OSCdef(defName);
            if(d.notNil) { d.free; removed.add(defName) };
        };
        if(removed.size > 0) {
            this.log("free: removed % OSCdef(s)".format(removed.size));
        } {
            this.log("free: nothing to remove");
        };
        defNames.clear;
        ^this
    }

    *help {
        var i;
        i = ChainOSCDispatcherDG.new("help", nil, true);
        i.help; i.free;
        ^"See post window"
    }

    help {
        var banner;
        banner = "ChainOSCDispatcherDG routes -> delegate methods (msg[1] indexing):\n"
        ++ " /demo/ping                       -> onPing(msg, time, addr, recvPort)\n"
        ++ " /chain/setNext <name>            -> onSetNext(nameSym)\n"
        ++ " /chain/switchNow                 -> onSwitchNow()\n"
        ++ " /chain/new <name> [slots]        -> onNew(nameSym, slots)\n"
        ++ " /chain/add <slot> <proc>         -> onAdd(slotIndex, procSym)\n"
        ++ " /chain/remove <slot>             -> onRemove(slotIndex)\n"
        ++ " /chain/setFrom <start> <list...> -> onSetFrom(startIndex, procList)\n"
        ++ "Notes: 0-based slot indexing (slot 0 = source)\n";
        banner.postln;
        ^this
    }

    toSymbol { arg x;
        if(x.isNil) { ^nil };
        if(x.isKindOf(Symbol)) { ^x };
        ^x.asString.asSymbol
    }

    log { arg line;
        if(verbose) { ("[ChainOSCDispatcherDG] " ++ line.asString).postln };
        ^this
    }
}
