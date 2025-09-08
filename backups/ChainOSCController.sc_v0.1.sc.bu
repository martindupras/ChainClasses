// ChainOSCController.sc
// v0.1
// MD 20250905:1355

ChainOSCController {
    classvar < version = "v0.1";
    var verbose = true;
    var controller, registry;

    *new { |controller, registry, verbose = true|
        var instance;
        instance = super.new.init(controller, registry, verbose);
        ^instance
    }

    init { |ctrl, reg, v|
        verbose = v;
        controller = ctrl;
        registry = reg;

        if (verbose) {
            ("[ChainOSCController] Initialized (% version)").format(version).postln;
        };

        this.setupListeners;
        ^this
    }

    setupListeners {
        // Example: /chain/switchNow
        OSCFunc({ |msg|
            if (verbose) { ("[OSC] /chain/switchNow received: %".format(msg)).postln };
            controller.switchNow;
        }, '/chain/switchNow');

        // Example: /chain/setNext myChain
        OSCFunc({ |msg|
            var name, chain;
            name = msg[1].asSymbol;
            chain = ChainManager.allInstances.at(name);
            if (chain.notNil) {
                controller.setNext(chain);
            } {
                ("[OSC] Chain not found: %".format(name)).warn;
            };
        }, '/chain/setNext');

        // Example: /chain/new myChain 8
        OSCFunc({ |msg|
            var name, slots, chain;
            name = msg[1].asSymbol;
            slots = msg[2].asInteger;
            chain = ChainManager.new(name, slots, verbose);
            registry.addChain(chain);
        }, '/chain/new');
    }
}
