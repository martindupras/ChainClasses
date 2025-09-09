// ChainTransitionManager.sc
// MD 20250909-0811

// v0.2 — ensure switchOnBeat schedules into the future (no immediate trigger at exact downbeat)
// Also uses numeric rounding in logs (sclang doesn't support printf %.2f/%.3f)

ChainTransitionManager : Object {
    classvar < version = "v0.1c";
    var verbose = true;
    var controller;

    *new { |controller, verbose = true|
        ^super.new.init(controller, verbose)
    }

    init { |ctrl, v|
        var now;
        controller = ctrl;
        verbose = v;
        if (verbose) {
            ("[ChainTransitionManager] Initialized (% version)").format(version).postln;
        };
        now = SystemClock.seconds.round(0.001);
        ("[ChainTransitionManager] Ready at %".format(now)).postln;
        ^this
    }

    // Schedule after 'seconds' on SystemClock
    switchIn { |seconds|
        var delay = seconds.max(0.01);
        ("[ChainTransitionManager] Scheduled switch in % seconds."
            .format(delay.round(0.01))).postln;

        SystemClock.sched(delay, {
            controller.switchNow;
            nil;  // never ^return from inside scheduled functions
        });

        ^this
    }

    // Schedule on a TempoClock at the next whole beat, unless we're exactly on it:
    // In that case, advance to the following beat. Then add (beatsAhead-1).
    switchOnBeat { |beatClock, beatsAhead = 1|
        var nowBeat, nextWhole, targetBeat, ahead;
        nowBeat   = beatClock.beats;          // current absolute beat (Float)
        nextWhole = nowBeat.ceil;             // next integer beat OR same if already integer
        if (nextWhole == nowBeat) {           // exactly on a downbeat -> force future
            nextWhole = nowBeat + 1;
        };
        ahead     = (beatsAhead - 1).max(0);
        targetBeat = nextWhole + ahead;

        ("[ChainTransitionManager] Scheduled switch on beat % (tempoClock)."
            .format(targetBeat)).postln;

        beatClock.sched(targetBeat, {
            controller.switchNow;
            nil;
        });

        ^this
    }
}