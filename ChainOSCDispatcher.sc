// Filename: ChainOSCDispatcher.sc
// Version: v0.4
// Change notes:
// - v0.3.8: Refactor variable names to be descriptive; add instance/class `help` methods; add concise header.
// - v0.3.7: Correct OSC payload indexing (msg[1..]); toSymbol uses isKindOf(Symbol).
// - v0.3.6: Fix boolean precedence in size checks; keep read-only accessors; direct ivar assigns.
// - v0.3.5: Setter-free internal assigns; keep public accessors read-only (<).
// - v0.3.4: Always (re)bind OSCdef in installRoute to avoid stale handlers on recompile/rerun.
// - v0.3.3: Added edit routes; named OSCdefs; handler Functions; free(); Symbol normalization.
//
// Summary (≤30 lines):
// ChainOSCDispatcher is the SINGLE OSC entry point for a live-performance chain system.
// It installs named OSCdef routes (per instance), normalizes payloads, and forwards actions
// to either provided handler Functions or an optional ChainController instance.
// Key routes (payloads start at msg[1], since msg[0] is the address):
//   /demo/ping
//   /chain/setNext <name>
//   /chain/switchNow
//   /chain/new <name> [<slots>]
//   /chain/add <slot> <proc>       // 0-based slots; slot 0 = source
//   /chain/remove <slot>           // 0-based
//   /chain/setFrom <start> <list>  // 0-based; applies procs at start+i
// Notes:
//   - Names/procs are normalized to Symbols (\Name, \tremolo).
//   - Uses Dictionary+Symbol keys; named OSCdef per route; .free removes only installed defs.
//   - Tests can pass handler Functions; runtime can pass a real ChainController.
// See .help for usage and examples.

ChainOSCDispatcher {
    classvar <defaultVerbose;
    var <verbose;
    var <namePrefix;
    var <defNames;     // Dictionary: routeSymbol -> defNameSymbol (the OSCdef key)
    var <handlerMap;   // Dictionary: actionSymbol -> Function
    var <controller;   // optional ChainController

    *initClass {
        defaultVerbose = false;
    }

    *new { arg name, controller = nil, handlers = nil, verbose;
        var chosenVerbose;
        chosenVerbose = verbose;
        if(chosenVerbose.isNil) { chosenVerbose = defaultVerbose };
        ^super.new.init(name, controller, handlers, chosenVerbose)
    }

    // args renamed to avoid ivar shadowing; assign ivars directly (no setters)
    init { arg nameArg, controllerArg, handlersArg, verboseArg;
        var nameString;
        verbose = if(verboseArg.isNil) { defaultVerbose } { verboseArg };
        controller = controllerArg;
        defNames = Dictionary.new;
        handlerMap = Dictionary.new;

        nameString = nameArg;
        if(nameString.isNil) { nameString = "osc" };
        namePrefix = ("ChainOSCDispatcher%" ++ this.identityHash.asString ++ "%" ++ nameString.asString);

        if(handlersArg.notNil) { this.setHandlers(handlersArg) };

        this.installRoutes;
        ^this
    }

    // --- Public API ---

    setHandlers { arg handlers;
        var handlersDict;
        handlersDict = handlers;
        if(handlersDict.isKindOf(Dictionary).not) {
            this.log("setHandlers: non-Dictionary provided, ignoring");
            ^this
        };
        handlerMap = handlersDict.copy;
        ^this
    }

    defNameFor { arg routeSymbol;
        ^defNames[routeSymbol]
    }

    installedRoutes {
        ^defNames.keys
    }

    free {
        var removedDefNames;
        removedDefNames = List.new;
        // Dictionary.do yields values (defNameSymbol) then keys (routeSymbol)
        defNames.do { arg defNameSymbol, routeSymbol;
            var oscdefObject;
            oscdefObject = OSCdef(defNameSymbol);
            if(oscdefObject.notNil) {
                oscdefObject.free;
                removedDefNames.add(defNameSymbol);
            }
        };
        if(removedDefNames.size > 0) {
            this.log("free: removed % OSCdef(s)".format(removedDefNames.size));
        } {
            this.log("free: nothing to remove");
        };
        ^this
    }

    // Useful quick reference printed to post window
    help {
        var banner;
        banner = "ChainOSCDispatcher routes (payload indexing starts at msg[1]):\n"
        ++ "  /demo/ping\n"
        ++ "  /chain/setNext <name>\n"
        ++ "  /chain/switchNow\n"
        ++ "  /chain/new <name> [<slots>]\n"
        ++ "  /chain/add <slot> <proc>\n"
        ++ "  /chain/remove <slot>\n"
        ++ "  /chain/setFrom <start> <list...>\n\n"
        ++ "Notes:\n"
        ++ "  - 0-based slot indexing (slot 0 = source)\n"
        ++ "  - Names/procs normalized to Symbols (\"B\" -> \\B, \"tremolo\" -> \\tremolo)\n"
        ++ "  - Named OSCdefs per instance; .free only removes those\n\n"
        ++ "Example (no audio):\n"
        ++ "  h = Dictionary[ \\setNext -> { |name| (\"setNext:\" + name).postln } ];\n"
        ++ "  c = ChainOSCDispatcher.new(\"demo\", nil, h, true);\n"
        ++ "  n = NetAddr(\"127.0.0.1\", NetAddr.langPort);\n"
        ++ "  n.sendMsg(\"/chain/setNext\", \"B\");  // -> \\B\n"
        ++ "  c.free;";
        banner.postln;
        ^this
    }

    *help {
        var instance;
        instance = ChainOSCDispatcher.new("help", nil, nil, true);
        instance.help;
        instance.free;
        ^"See post window for usage."
    }

    // --- Private: route install ---

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

    installRoute { arg routeString;
        var routeSymbol, defNameSymbol, handlerFunction;
        routeSymbol = routeString.asSymbol;
        defNameSymbol = this.makeDefName(routeSymbol);
        handlerFunction = this.makeHandlerFor(routeSymbol);

        // Always (re)bind so changes take effect after recompile/rerun.
        OSCdef(defNameSymbol, handlerFunction, routeString);
        defNames[routeSymbol] = defNameSymbol;

        this.log("installed/updated " ++ routeSymbol.asString ++ " -> " ++ defNameSymbol.asString);
        ^this
    }

    makeDefName { arg routeSymbol;
        var base, routeAsString, safeRoute;
        base = namePrefix;
        routeAsString = routeSymbol.asString;
        safeRoute = routeAsString.replace("/", "_");
        ^(base ++ "_" ++ safeRoute).asSymbol
    }

    // Build per-route OSCdef function
    makeHandlerFor { arg routeSymbol;
        var handlerFunc;
        handlerFunc = { arg msg, time, addr, recvPort;
            var routeSymbolLocal;
            routeSymbolLocal = routeSymbol;
            if(routeSymbolLocal == '/demo/ping'.asSymbol) {
                this.onPing(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/setNext'.asSymbol) {
                this.onSetNext(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/switchNow'.asSymbol) {
                this.onSwitchNow(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/new'.asSymbol) {
                this.onNew(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/add'.asSymbol) {
                this.onAdd(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/remove'.asSymbol) {
                this.onRemove(msg, time, addr, recvPort);
            } {
            if(routeSymbolLocal == '/chain/setFrom'.asSymbol) {
                this.onSetFrom(msg, time, addr, recvPort);
            } {
                this.log("handler: unknown route " ++ routeSymbolLocal.asString);
            }}}}}}};
        };
        ^handlerFunc
    }

    // --- Route handlers (var-first) ---

    onPing { arg msg, time, addr, recvPort;
        var callback;
        // msg[0] is address; payload starts at msg[1]
        this.log("/demo/ping " ++ msg.asString);
        callback = handlerMap[\ping];
        if(callback.notNil) { callback.(msg, time, addr, recvPort) };
    }

	onSetNext { arg msg, time, addr, recvPort;
    var nameAny, nameSymbol, callback, chain, all;
    nameAny = (msg.size > 1).if({ msg[1] }, { nil });
    nameSymbol = this.toSymbol(nameAny);
    if(nameSymbol.isNil) { this.log("setNext: missing name"); ^this };

    this.log("/chain/setNext " ++ nameSymbol.asString);

    // Prefer injected handler if provided
    callback = handlerMap[\setNext];
    if(callback.notNil) { callback.(nameSymbol); ^this };

    // Fallback: resolve by name and set on controller
    if(controller.notNil and: { controller.respondsTo(\setNext) }) {
        all = ChainManager.allInstances;
        chain = all.at(nameSymbol);
        if(chain.isNil) {
            this.log("setNext: no chain named " ++ nameSymbol.asString ++ " found");
            ^this;
        };
        controller.setNext(chain);
    };
    ^this
}

/*    onSetNext { arg msg, time, addr, recvPort;
        var nameAny, nameSymbol, callback;
        nameAny = (msg.size > 1).if({ msg[1] }, { nil });
        nameSymbol = this.toSymbol(nameAny);
        if(nameSymbol.isNil) { this.log("setNext: missing name"); ^this };
        this.log("/chain/setNext " ++ nameSymbol.asString);
        callback = handlerMap[\setNext];
        if(callback.notNil) { callback.(nameSymbol) } {
            if(controller.notNil and: { controller.respondsTo(\setNext) }) { controller.setNext(nameSymbol) };
        };
        ^this
    }*/

    onSwitchNow { arg msg, time, addr, recvPort;
        var callback;
        this.log("/chain/switchNow");
        callback = handlerMap[\switchNow];
        if(callback.notNil) { callback.() } {
            if(controller.notNil and: { controller.respondsTo(\switchNow) }) { controller.switchNow };
        };
        ^this
    }

    onNew { arg msg, time, addr, recvPort;
        var nameAny, nameSymbol, slots, callback;
        nameAny = (msg.size > 1).if({ msg[1] }, { nil });
        nameSymbol = this.toSymbol(nameAny);
        slots = (msg.size > 2).if({ msg[2].asInteger }, { nil });
        if(nameSymbol.isNil) { this.log("new: missing name"); ^this };
        this.log("/chain/new " ++ nameSymbol.asString ++ (slots.notNil.if({ " " ++ slots.asString }, { "" })));
        callback = handlerMap[\new];
        if(callback.notNil) { callback.(nameSymbol, slots) };
        ^this
    }

    onAdd { arg msg, time, addr, recvPort;
        var slotAny, procAny, slotIndex, procSymbol, callback;
        slotAny = (msg.size > 1).if({ msg[1] }, { nil });
        procAny = (msg.size > 2).if({ msg[2] }, { nil });
        if(slotAny.isNil or: { procAny.isNil }) { this.log("add: requires <slot> <proc>"); ^this };
        slotIndex = slotAny.asInteger; // 0-based
        procSymbol = this.toSymbol(procAny);
        this.log("/chain/add slot:" ++ slotIndex.asString ++ " proc:" ++ procSymbol.asString);
        callback = handlerMap[\add];
        if(callback.notNil) { callback.(slotIndex, procSymbol) };
        ^this
    }

    onRemove { arg msg, time, addr, recvPort;
        var slotAny, slotIndex, callback;
        slotAny = (msg.size > 1).if({ msg[1] }, { nil });
        if(slotAny.isNil) { this.log("remove: requires <slot>"); ^this };
        slotIndex = slotAny.asInteger; // 0-based
        this.log("/chain/remove slot:" ++ slotIndex.asString);
        callback = handlerMap[\remove];
        if(callback.notNil) { callback.(slotIndex) };
        ^this
    }

    onSetFrom { arg msg, time, addr, recvPort;
        var startAny, startIndex, restArgs, procSymbols, callback;
        // Need at least address + start + one proc => size >= 3
        if(msg.size < 3) { this.log("setFrom: requires <start> <list...>"); ^this };
        startAny = msg[1];
        startIndex = startAny.asInteger;
        restArgs = msg.copyRange(2, msg.size - 1);
        procSymbols = restArgs.collect({ arg item; this.toSymbol(item) });
        this.log("/chain/setFrom start:" ++ startIndex.asString ++ " procs:" ++ procSymbols.asString);
        callback = handlerMap[\setFrom];
        if(callback.notNil) { callback.(startIndex, procSymbols) };
        ^this
    }

    // --- Utilities ---

    toSymbol { arg x;
        var symbolValue;
        if(x.isNil) { ^nil };
        if(x.isKindOf(Symbol)) { ^x };
        symbolValue = x.asString.asSymbol;
        ^symbolValue
    }

    log { arg line;
        var lineString;
        if(verbose.not) { ^this };
        lineString = "[ChainOSCDispatcher] " ++ line.asString;
        lineString.postln;
        ^this
    }
}
