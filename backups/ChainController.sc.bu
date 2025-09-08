// ChainController.sc
// v0.2  — MD 20250905
// - Adds accessors for currentChain / nextChain so GUI & other modules can read them
// - Safe logging; returns ^this for chaining

ChainController : Object {
    classvar < version = "v0.2";
    var <currentChain, <nextChain;   // <-- expose getters for GUI & others
    var verbose = true;

    *new { |verbose = true|
        ^super.new.init(verbose)
    }

    init { |v|
        verbose = v;
        if (verbose) {
            ("[ChainController] Initialized (% version)").format(version).postln;
        };
        ^this
    }

    setCurrent { |chainManager|
        currentChain = chainManager;
        if (verbose and: { chainManager.notNil }) {
            ("[ChainController] Current chain set to %".format(chainManager.getName)).postln;
        };
        ^this
    }

    setNext { |chainManager|
        nextChain = chainManager;
        if (verbose and: { chainManager.notNil }) {
            ("[ChainController] Next chain set to %".format(chainManager.getName)).postln;
        };
        ^this
    }

    switchNow {
        if (nextChain.notNil) {
            if (currentChain.notNil) {
                currentChain.stop;
            };
            nextChain.play;
            currentChain = nextChain;
            nextChain = nil;
            if (verbose and: { currentChain.notNil }) {
                ("[ChainController] Switched to chain %.".format(currentChain.getName)).postln;
            };
        } {
            "[ChainController] No next chain to switch to.".postln;
        };
        ^this
    }

    status {
        var cur = currentChain.tryPerform(\getName) ?? { "None" };
        var nxt = nextChain.tryPerform(\getName) ?? { "None" };
        ("[ChainController] Current: %, Next: %".format(cur, nxt)).postln;
        ^this
    }
}
