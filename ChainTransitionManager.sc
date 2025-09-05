// ChainTransitionManager.sc
// v0.1c — ensure switchOnBeat schedules into the future (no immediate trigger at exact downbeat)
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



/*// ChainTransitionManager.sc
// v0.1b — MD 20250905
// - Fix: use TempoClock.sched with beats instead of SystemClock.schedAbs + nextBeat
// - Fix: remove caret returns from scheduled functions (no OutOfContextReturn)
// - Log: use numeric rounding instead of printf-like specifiers

ChainTransitionManager : Object {
    classvar < version = "v0.1b";
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

    // Schedule a switch after 'seconds' on SystemClock
    switchIn { |seconds|
        var delay = seconds.max(0.01);
        ("[ChainTransitionManager] Scheduled switch in % seconds."
            .format(delay.round(0.01))).postln;

        SystemClock.sched(delay, {
            controller.switchNow;
            nil; // do not ^return from inside a scheduled Function
        });

        ^this
    }

    // Schedule on a TempoClock at the next whole beat plus (beatsAhead-1)
    switchOnBeat { |beatClock, beatsAhead = 1|
        var baseBeat, targetBeat;
        // next whole beat
        baseBeat = beatClock.beats.ceil;
        // allow ahead-of-time scheduling
        targetBeat = baseBeat + (beatsAhead - 1).max(0);

        ("[ChainTransitionManager] Scheduled switch on beat % (tempoClock)."
            .format(targetBeat)).postln;

        beatClock.sched(targetBeat, {
            controller.switchNow;
            nil; // do not ^return inside scheduled Function
        });

        ^this
    }
}*/


/*// ChainTransitionManager.sc  (v0.1 → v0.1a)
ChainTransitionManager : Object {
    classvar < version = "v0.1a";
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
        now = SystemClock.seconds;
        ("[ChainTransitionManager] Ready at %.3f".format(now)).postln;
        ^this
    }

    switchIn { |seconds|
        var delay;
        delay = seconds.max(0.01);
        ("[ChainTransitionManager] Scheduled switch in %.2f seconds.".format(delay)).postln;
        SystemClock.sched(delay, {
            controller.switchNow;
            nil;   // <- do NOT use ^nil here
        });
        ^this
    }

    switchOnBeat { |beatClock, beatsAhead = 1|
        var beatTime;
        beatTime = beatClock.nextBeat + (beatsAhead - 1) * beatClock.beatDur;
        ("[ChainTransitionManager] Scheduled switch on beat at %.3f".format(beatTime)).postln;
        SystemClock.schedAbs(beatTime, {
            controller.switchNow;
            nil;   // <- do NOT use ^nil here
        });
        ^this
    }
}*/


// // ChainTransitionManager.sc
// // v0.1
// // MD 20250905:1315
//
// ChainTransitionManager : Object {
// 	classvar < version = "v0.1";
// 	var verbose = true;
// 	var controller;
//
// 	*new { |controller, verbose = true|
// 		var instance;
// 		instance = super.new.init(controller, verbose);
// 		^instance
// 	}
//
// 	init { |ctrl, v|
// 		var now;
// 		controller = ctrl;
// 		verbose = v;
// 		if (verbose) {
// 			("[ChainTransitionManager] Initialized (% version)").format(version).postln;
// 		};
// 		now = SystemClock.seconds;
// 		("[ChainTransitionManager] Ready at %.3f".format(now)).postln;
// 		^this
// 	}
//
// 	switchIn { |seconds|
// 		var delay;
// 		delay = seconds.max(0.01);
// 		("[ChainTransitionManager] Scheduled switch in %.2f seconds.".format(delay)).postln;
// 		SystemClock.sched(delay, {
// 			controller.switchNow;
// 			^nil;
// 		});
// 		^this
// 	}
//
// 	switchOnBeat { |beatClock, beatsAhead = 1|
// 		var beatTime;
// 		beatTime = beatClock.nextBeat + (beatsAhead - 1) * beatClock.beatDur;
// 		("[ChainTransitionManager] Scheduled switch on beat at %.3f".format(beatTime)).postln;
// 		SystemClock.schedAbs(beatTime, {
// 			controller.switchNow;
// 			^nil;
// 		});
// 		^this
// 	}
// }
