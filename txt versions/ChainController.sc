// ChainController.sc
// v0.2
// MD 20250905: gate posts by 'verbose'; make status nil-safe.

ChainController : Object {
    classvar < version = "v0.2";
    var currentChain, nextChain, verbose = true;

    *new { |verbose = true|
        var instance;
        instance = super.new.init(verbose);
        ^instance
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
        if (verbose) {
            ("[ChainController] Current chain set to %".format(chainManager.getName)).postln;
        };
        ^this
    }

    setNext { |chainManager|
        nextChain = chainManager;
        if (verbose) {
            ("[ChainController] Next chain set to %".format(chainManager.getName)).postln;
        };
        ^this
    }

    switchNow {
        var hadNext, hadCurrent, newName;
        hadNext = nextChain.notNil;
        hadCurrent = currentChain.notNil;

        if (hadNext) {
            if (hadCurrent) { currentChain.stop };
            nextChain.play;
            currentChain = nextChain;
            nextChain = nil;
            newName = currentChain.getName;
            if (verbose) {
                ("[ChainController] Switched to chain %.".format(newName)).postln;
            };
        }{
            if (verbose) {
                "[ChainController] No next chain to switch to.".postln;
            };
        };
        ^this
    }

    status {
        var curName, nxtName;
        curName = if (currentChain.notNil) { currentChain.getName } { "None" };
        nxtName = if (nextChain.notNil) { nextChain.getName } { "None" };
        if (verbose) {
            ("[ChainController] Current: %, Next: %".format(curName, nxtName)).postln;
        };
        ^this
    }
}
