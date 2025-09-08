// OSCCommandRouter.sc
// v0.2.1
// MD 20250905: remove leading '_' from helper names; named OSCdef(s) + .free()

OSCCommandRouter : Object {
    classvar < version = "v0.2.1";
    var verbose = true;
    var controller;

    var defNames; // path -> Symbol
    var id;

    *new { |controller, verbose = true|
        var instance;
        instance = super.new.init(controller, verbose);
        ^instance
    }

    init { |ctrl, v|
        var hash;
        controller = ctrl;
        verbose = v;

        defNames = IdentityDictionary.new;
        hash = this.identityHash;
        id = hash;

        if (verbose) {
            ("[OSCCommandRouter] Initialized (% version, id:%)").format(version, id).postln;
        };

        this.prInstall("/chain/switchNow", { |msg, time, addr, recvPort|
            if (verbose) { "[OSC] /chain/switchNow".postln };
            controller.switchNow;
            nil
        });

        ^this
    }

    free {
        var syms;
        syms = defNames.values.asArray;
        syms.do { |sym|
            var d;
            d = OSCdef(sym);
            if (d.notNil) { d.free };
        };
        defNames.clear;
        if (verbose) { ("[OSCCommandRouter] Freed responders (id:%)".format(id)).postln };
        ^this
    }

    prInstall { |path, func|
        var p, defKey, action;
        p = path.asString;
        defKey = this.prDefNameFor(p);
        action = { |msg, time, addr, recvPort|
            var f;
            f = func;
            f.value(msg, time, addr, recvPort);
            nil
        };
        OSCdef(defKey, action, p);
        defNames[p] = defKey;
        if (verbose) { ("[OSCCommandRouter] OSCdef % for %".format(defKey, p)).postln };
        ^defKey
    }

    prDefNameFor { |path|
        var safe, str;
        safe = path.asString.collect { |ch|
            var code, rep;
            code = ch.ascii;
            if (code == $/.ascii) { rep = $_ } { rep = ch };
            rep
        }.join;
        str = "OSCCommandRouter_%_%_%".format(version, id, safe);
        ^str.asSymbol
    }
}
