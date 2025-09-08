// ChainOSCController.sc
// v0.2.1
// MD 20250905: remove leading '_' from helper method names to avoid PRIMITIVENAME parse errors.
// - Uses named OSCdef(s) per instance, adds edit routes, and provides .free().

ChainOSCController {
    classvar < version = "v0.2.1";
    var verbose = true;
    var controller, registry;

    // internal: per-instance OSCdef keys and targeting state
    var defNames;         // IdentityDictionary path(String) -> Symbol (OSCdef key)
    var id;               // per-instance id for OSCdef name suffix
    var lastTargetName;   // Symbol or nil; set by /chain/setNext or /chain/new

    *new { |controller, registry, verbose = true|
        var instance;
        instance = super.new.init(controller, registry, verbose);
        ^instance
    }

    init { |ctrl, reg, v|
        var hash;
        verbose    = v;
        controller = ctrl;
        registry   = reg;

        defNames = IdentityDictionary.new;
        hash = this.identityHash;
        id = hash;

        if (verbose) {
            ("[ChainOSCController] Initialized (% version, id:%)")
            .format(version, id).postln;
        };

        this.setupListeners;
        ^this
    }

    // ---------- OSC setup ----------

    setupListeners {
        // /chain/new <name> [<slots>]
        this.prInstall("/chain/new", { |msg, time, addr, recvPort|
            var name, slots, chain;
            name  = msg[1].asSymbol;
            slots = msg[2];
            if (slots.isNil) { slots = 8 } { slots = slots.asInteger.max(2) };
            chain = ChainManager.new(name, slots);
            lastTargetName = name;  // convenience default target
            if (verbose) { ("[OSC] /chain/new % slots:%".format(name, slots)).postln };
            nil
        });

        // /chain/setNext <name>
        this.prInstall("/chain/setNext", { |msg, time, addr, recvPort|
            var name, dict, chain;
            name = msg[1].asSymbol;
            dict = ChainManager.allInstances;
            chain = dict.at(name);
            if (chain.notNil) {
                controller.setNext(chain);
                lastTargetName = name;
                if (verbose) { ("[OSC] /chain/setNext %".format(name)).postln };
            }{
                ("[OSC] /chain/setNext: chain not found: %".format(name)).warn;
            };
            nil
        });

        // /chain/switchNow
        this.prInstall("/chain/switchNow", { |msg, time, addr, recvPort|
            if (verbose) { "[OSC] /chain/switchNow".postln };
            controller.switchNow;
            nil
        });

        // /chain/add [<name>] <slot> <processor>
        this.prInstall("/chain/add", { |msg, time, addr, recvPort|
            var cName, slot, proc, dict, chain, hasNameFirst;
            hasNameFirst = msg.size >= 4 and: { msg[1].isString or: { msg[1].isSymbol } };
            if (hasNameFirst) {
                cName = msg[1].asSymbol;
                slot  = msg[2].asInteger;
                proc  = msg[3].asSymbol;
            }{
                cName = lastTargetName;
                slot  = msg[1].asInteger;
                proc  = msg[2].asSymbol;
            };
            if (cName.isNil) {
                "[OSC] /chain/add: no target chain set (use /chain/setNext <name> or include name)".warn;
            }{
                dict  = ChainManager.allInstances;
                chain = dict.at(cName);
                if (chain.notNil) {
                    chain.setSlot(slot, proc);
                    if (verbose) { ("[OSC] /chain/add %[%] := %".format(cName, slot, proc)).postln };
                }{
                    ("[OSC] /chain/add: chain not found: %".format(cName)).warn;
                };
            };
            nil
        });

        // /chain/remove [<name>] <slot>
        this.prInstall("/chain/remove", { |msg, time, addr, recvPort|
            var cName, slot, dict, chain, hasNameFirst;
            hasNameFirst = msg.size >= 3 and: { msg[1].isString or: { msg[1].isSymbol } };
            if (hasNameFirst) {
                cName = msg[1].asSymbol;
                slot  = msg[2].asInteger;
            }{
                cName = lastTargetName;
                slot  = msg[1].asInteger;
            };
            if (cName.isNil) {
                "[OSC] /chain/remove: no target chain set".warn;
            }{
                dict  = ChainManager.allInstances;
                chain = dict.at(cName);
                if (chain.notNil) {
                    chain.setSlot(slot, \bypass);
                    if (verbose) { ("[OSC] /chain/remove %[%]".format(cName, slot)).postln };
                }{
                    ("[OSC] /chain/remove: chain not found: %".format(cName)).warn;
                };
            };
            nil
        });

        // /chain/setFrom [<name>] <startSlot> <p1> <p2> ...
        this.prInstall("/chain/setFrom", { |msg, time, addr, recvPort|
            var cName, start, names, dict, chain, maxIndex, hasNameFirst;
            hasNameFirst = msg.size >= 3 and: { msg[1].isString or: { msg[1].isSymbol } };
            if (hasNameFirst) {
                cName = msg[1].asSymbol;
                start = msg[2].asInteger;
                names = msg.copyRange(3, msg.size - 1).collect(_.asSymbol);
            }{
                cName = lastTargetName;
                start = msg[1].asInteger;
                names = msg.copyRange(2, msg.size - 1).collect(_.asSymbol);
            };
            if (cName.isNil) {
                "[OSC] /chain/setFrom: no target chain set".warn;
            }{
                dict  = ChainManager.allInstances;
                chain = dict.at(cName);
                if (chain.notNil) {
                    maxIndex = chain.getNumSlots - 1;
                    names.do { |sym, i|
                        var idx;
                        idx = start + i;
                        if (idx <= maxIndex) {
                            chain.setSlot(idx, sym);
                        }{
                            ("[OSC] /chain/setFrom: index % out of range; ignored".format(idx)).postln;
                        };
                    };
                    if (verbose) { ("[OSC] /chain/setFrom % from % := %".format(cName, start, names)).postln };
                }{
                    ("[OSC] /chain/setFrom: chain not found: %".format(cName)).warn;
                };
            };
            nil
        });

        // Optional: /chain/status — prints all chains
        this.prInstall("/chain/status", { |msg, time, addr, recvPort|
            var dict;
            dict = ChainManager.allInstances;
            if (verbose) {
                ("[OSC] /chain/status (chains: %)"
                    .format(dict.keys.asArray.sort)
                ).postln
            };
            dict.keysValuesDo { |key, ch| ch.status };
            nil
        });

        ^this
    }

    // ---------- lifecycle ----------
    free {
        var syms;
        syms = defNames.values.asArray;
        syms.do { |sym|
            var d;
            d = OSCdef(sym);
            if (d.notNil) { d.free };
        };
        defNames.clear;
        if (verbose) { ("[ChainOSCController] Freed responders (id:%)".format(id)).postln };
        ^this
    }

    // ---------- helpers ----------

    prInstall { |path, func|
        var p, defKey, action;
        p = path.asString;
        defKey = this.prMakeDefName(p);
        action = { |msg, time, addr, recvPort|
            var f;
            f = func;
            f.value(msg, time, addr, recvPort);
            nil
        };
        OSCdef(defKey, action, p);
        defNames[p] = defKey;
        if (verbose) { ("[ChainOSCController] OSCdef % for %".format(defKey, p)).postln };
        ^defKey
    }

    prMakeDefName { |path|
        var safe, str;
        safe = path.asString.collect { |ch|
            var code, rep;
            code = ch.ascii;
            if (code == $/.ascii) { rep = $_ } { rep = ch };
            rep
        }.join;
        str = "ChainOSCController_%_%_%".format(version, id, safe);
        ^str.asSymbol
    }
}
