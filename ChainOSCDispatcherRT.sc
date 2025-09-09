// Filename: ChainOSCDispatcherRT.sc
// Purpose: Data-driven OSC dispatcher (route table + generic handler)
// Notes:
//  - Handler dictionary is optional per action.
//  - Built-in fallbacks only for actions with universal behaviour: \setNext, \switchNow.
//  - No functional coupling to UI; keep policy in handlers or controller.

// Style: var-first; conservative syntax; no '??' operator.

ChainOSCDispatcherRT : Object {
    classvar <defaultVerbose;
    var <verbose;
    var <namePrefix;
    var <defNames;     // Dictionary[routeSymbol -> OSCdef key]
    var <handlerMap;   // Dictionary[actionSymbol -> Function]
    var <controller;   // optional ChainController
    var <routes;       // IdentityDictionary[routeSymbol -> (action: \sym, args: [...], fallback: Bool)]

    *initClass {
        defaultVerbose = false;
    }

    *new { arg name, controller = nil, handlers = nil, verbose;
        ^super.new.init(name, controller, handlers, verbose)
    }

    init { arg nameArg, controllerArg, handlersArg, verboseArg;
        var nameString;
        verbose     = if(verboseArg.isNil) { defaultVerbose } { verboseArg };
        controller  = controllerArg;
        defNames    = Dictionary.new;
        handlerMap  = Dictionary.new;

        nameString = nameArg;
        if(nameString.isNil) { nameString = "osc" };
        namePrefix = ("ChainOSCDispatcherRT%" ++ this.identityHash.asString ++ "%" ++ nameString.asString);

        this.buildRouteTable;
        if(handlersArg.notNil) { this.setHandlers(handlersArg) };
        this.installRoutes;
        ^this
    }

    buildRouteTable {
        var r;
        r = IdentityDictionary.new;
        r['/demo/ping'.asSymbol]      = (action: \ping,      args: [\msg],               fallback: false);
        r['/chain/setNext'.asSymbol]  = (action: \setNext,   args: [\name],              fallback: true);
        r['/chain/switchNow'.asSymbol]= (action: \switchNow, args: [],                   fallback: true);
        r['/chain/new'.asSymbol]      = (action: \new,       args: [\name, \slots],      fallback: false);
        r['/chain/add'.asSymbol]      = (action: \add,       args: [\slot, \proc],       fallback: false);
        r['/chain/remove'.asSymbol]   = (action: \remove,    args: [\slot],              fallback: false);
        r['/chain/setFrom'.asSymbol]  = (action: \setFrom,   args: [\start, \list],      fallback: false);
        routes = r;
        ^this
    }

    setHandlers { arg handlers;
        var d;
        d = handlers;
        if(d.isKindOf(Dictionary).not) {
            this.log("setHandlers: non-Dictionary provided, ignoring");
            ^this
        };
        handlerMap = d.copy;
        ^this
    }

    installRoutes {
        routes.keysValuesDo { arg routeSymbol, meta;
            this.installRoute(routeSymbol, meta)
        };
        ^this
    }

    installRoute { arg routeSymbol, meta;
        var defName, routeString, fn;
        defName = this.makeDefName(routeSymbol);
        routeString = routeSymbol.asString;
        fn = this.makeGenericHandler(routeSymbol, meta);
        OSCdef(defName, fn, routeString);
        defNames[routeSymbol] = defName;
        this.log("installed/updated " ++ routeString ++ " -> " ++ defName.asString);
        ^this
    }

    makeDefName { arg routeSymbol;
        ^(namePrefix ++ "_" ++ routeSymbol.asString.replace("/", "_")).asSymbol
    }

    makeGenericHandler { arg routeSymbol, meta;
        var action, argsSpec;
        action   = meta[\action];
        argsSpec = meta[\args];

        ^{ arg msg, time, addr, recvPort;
            var parsedArgs, cb, hasFallback;
            parsedArgs  = this.parseArgs(argsSpec, msg);
            cb = handlerMap[action];

            if(cb.notNil) {
                cb.valueArray(parsedArgs);
                ^this
            };

            hasFallback = (meta[\fallback] == true);
            if(hasFallback) {
                this.applyFallback(routeSymbol, action, parsedArgs);
            } {
                this.log("no fallback for " ++ routeSymbol.asString);
            };
            ^this
        }
    }

    parseArgs { arg argsSpec, msg;
        var out, size;
        out  = Array.new;
        size = msg.size;

        argsSpec.do { arg key;
            var v;
            if(key == \msg) { out = [msg]; ^out };

            if(key == \name) {
                v = if(size > 1) { this.toSymbol(msg[1]) } { nil };
                out = out.add(v);
            };

            if(key == \slots) {
                v = if(size > 2) { msg[2].asInteger } { nil };
                out = out.add(v);
            };

            if(key == \slot) {
                v = if(size > 1) { msg[1].asInteger } { nil };
                out = out.add(v);
            };

            if(key == \proc) {
                v = if(size > 2) { this.toSymbol(msg[2]) } { nil };
                out = out.add(v);
            };

            if(key == \start) {
                v = if(size > 1) { msg[1].asInteger } { nil };
                out = out.add(v);
            };

            if(key == \list) {
                var rest, syms;
                rest = if(size > 2) { msg.copyRange(2, size - 1) } { [] };
                syms = rest.collect({ arg item; this.toSymbol(item) });
                out = out.add(syms);
            };
        };
        ^out
    }

    applyFallback { arg routeSymbol, action, parsedArgs;
        // Minimal, generic fallbacks only where behaviour is universal.

        if(action == \setNext) {
            var nameSym, all, ch;
            if(parsedArgs.size < 1) {
                this.log("setNext: missing name"); ^this
            };
            nameSym = parsedArgs[0];
            all = ChainManager.allInstances;
            ch  = all.at(nameSym);
            if(ch.isNil) {
                this.log("setNext: no chain named " ++ nameSym.asString);
                ^this
            };
            if(controller.notNil and: { controller.respondsTo(\setNext) }) {
                controller.setNext(ch);
            };
            ^this
        };

        if(action == \switchNow) {
            if(controller.notNil and: { controller.respondsTo(\switchNow) }) {
                controller.switchNow;
            };
            ^this
        };

        // No other routes have a universal policy.
        ^this
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
        i = ChainOSCDispatcherRT.new("help", nil, nil, true);
        i.help; i.free;
        ^"See post window"
    }

    help {
        var banner;
        banner = "ChainOSCDispatcherRT routes (payload indexing starts at msg[1]):\n"
        ++ " /demo/ping\n"
        ++ " /chain/setNext <name>\n"
        ++ " /chain/switchNow\n"
        ++ " /chain/new <name> [<slots>]\n"
        ++ " /chain/add <slot> <proc>\n"
        ++ " /chain/remove <slot>\n"
        ++ " /chain/setFrom <start> <list...>\n\n"
        ++ "Notes:\n"
        ++ " - 0-based slot indexing (slot 0 = source)\n";
        banner.postln;
        ^this
    }

    toSymbol { arg x;
        if(x.isNil) { ^nil };
        if(x.isKindOf(Symbol)) { ^x };
        ^x.asString.asSymbol
    }

    log { arg line;
        if(verbose) { ("[ChainOSCDispatcherRT] " ++ line.asString).postln };
        ^this
    }
}
