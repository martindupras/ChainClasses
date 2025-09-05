(
// test_transitions2.scd — focused CTM checks (tempo-aware wait)
// Requires: ChainTransitionManager v0.1c, MDMockController

var ctmClass, ctl, ctm, did1, did2, clk, beatsAhead, nowBeat, nextWhole, targetBeat, waitSec;

ctmClass = \ChainTransitionManager.asClass;
if(ctmClass.isNil) {
    "ChainTransitionManager not compiled".warn; ^this;
};

ctl = MDMockController.new;
ctm = ctmClass.new(ctl, true);

// --- switchIn (delay) ---
ctl.didSwitch = false;
ctm.switchIn(0.02);

Routine({
    0.06.wait;  // enough for the 0.02s delay
    did1 = ctl.didSwitch;
    ("switchIn -> didSwitch: " ++ did1).postln;

    // --- switchOnBeat (future downbeat) ---
    // Use a fresh clock so we know we're at beat ~0.0
    ctl.didSwitch = false;
    clk = TempoClock(2);  // 2 beats/sec -> 0.5 s/beat
    beatsAhead = 1;

    // Replicate CTM's scheduling math to compute *our* wait time:
    nowBeat   = clk.beats;
    nextWhole = nowBeat.ceil;
    if(nextWhole == nowBeat) { nextWhole = nowBeat + 1 };  // never schedule "now"
    targetBeat = nextWhole + (beatsAhead - 1).max(0);

    // How long (seconds) until targetBeat from *right now*?
    waitSec = ((targetBeat - clk.beats) * clk.beatDur) + 0.03; // +small margin

    ctm.switchOnBeat(clk, beatsAhead);

    waitSec.postln;  // optional: see the computed wait seconds
    waitSec.wait;    // wait long enough for the scheduled switch

    did2 = ctl.didSwitch;
    ("switchOnBeat -> didSwitch: " ++ did2).postln;
}).play(AppClock);
)
