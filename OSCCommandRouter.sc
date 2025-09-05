// OSCCommandRouter.sc
// v0.1
// MD 20250905:1300

OSCCommandRouter : Object {
    classvar < version = "v0.1";
    var verbose = true;
    var controller;

    *new { |controller, verbose = true|
        var instance;
        instance = super.new.init(controller, verbose);
        ^instance
    }

    init { |ctrl, v|
        var responder;
        controller = ctrl;
        verbose = v;
        if (verbose) {
            ("[OSCCommandRouter] Initialized (% version)").format(version).postln;
        };

        // Example: listen for /chain/switchNow
        responder = OSCFunc({ |msg, time, addr, recvPort|
            if (verbose) {
                ("[OSCCommandRouter] Received: %".format(msg)).postln;
            };
            controller.switchNow;
        }, '/chain/switchNow');

        ^this
    }
}
