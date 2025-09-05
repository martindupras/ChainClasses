// tests_ChainManagerv2.scd
(
// ChainManager v2 — test plan
// Paste & evaluate this whole block (Cmd/Ctrl+Enter).

s.waitForBoot {
    var assert, header, footer, sep;
    var regBefore, regAfter, keys, cm1, cm2, cm1Name, cm2Name;
    var testProcessorsDir;  // optional: set to your processors folder if you have one
    var lfoName;
	var spec1, spec2, spec3, spec4, spec5, spec6;

    // ---------- Helper: simple assertion ----------
    assert = { |cond, msg|
        if (cond) { ("[OK]   " ++ msg).postln } { ("[FAIL] " ++ msg).warn };
    };

    sep    = { "────────────────────────────────────────────────".postln };
    header = { |title| sep.value; title.postln; sep.value };
    footer = { sep.value };

    // ---------- 0) Clean start ----------
    header.value("ChainManager v2 — Test Plan");

    // If you have old instances around, free them
    if (ChainManager.respondsTo(\allInstances)) {
        regBefore = ChainManager.allInstances;
        if (regBefore.notNil and: { regBefore.size > 0 }) {
            "Freeing pre-existing ChainManager instances…".postln;
            ChainManager.freeAll;
        };
    };

    // ---------- 1) Create first chain (8 slots) ----------
    "Creating cm1 (name=\\myChain, 8 slots) and playing…".postln;
    cm1 = ChainManager.new(\myChain, 8);
    cm1.play;

    // Check registry has one entry
    regAfter = ChainManager.allInstances;
    assert.(regAfter.size == 1, "Registry holds exactly one chain after cm1 creation");
    cm1Name = cm1.getName;
    assert.(regAfter.includesKey(cm1Name), "Registry contains key == cm1.getName");

    // ---------- 2) Verify default slots, then edit a few ----------
    spec1;
    spec1 = cm1.status;  // posts and returns a copy
    assert.(spec1[0] == \testsignal, "Slot 0 is \\testsignal by default");
    assert.(spec1[1] == \bypass, "Slot 1 defaults to \\bypass");

    "Assigning processors to cm1: slot 3->hp, 4->lp, 5->tremolo…".postln;
    cm1.setSlot(3, \hp).setSlot(4, \lp).setSlot(5, \tremolo);

    spec2 = cm1.status;
    assert.(spec2[3] == \hp and: { spec2[4] == \lp } and: { spec2[5] == \tremolo },
        "Slots 3..5 updated to \\hp, \\lp, \\tremolo");

    // ---------- 3) Parameter control (generic set) ----------
    "Tweaking params (\\freq, \\rate, \\depth)…".postln;
    Ndef(cm1.getName).set(\freq, 600, \rate, 14, \depth, 1.0);
    // We cannot easily assert audibility, but no error should be posted.

    // ---------- 4) Chain spec padding / trimming ----------
    "Testing setChainSpec padding…".postln;
    cm1.setChainSpec([\testsignal, \hp, \tremolo]); // shorter than 8; should pad with \bypass
     spec3 = cm1.getSpec;
    assert.(spec3.size == 8, "setChainSpec pads to the chain length (8)");
    assert.(spec3[0] == \testsignal, "Slot 0 remains a valid source after padding");

    "Testing setChainSpec trimming…".postln;
    cm1.setChainSpec([\testsignal, \hp, \lp, \tremolo, \bypass, \hp, \lp, \tremolo, \hp, \lp]); // > 8; trims
     spec4 = cm1.getSpec;
    assert.(spec4.size == 8, "setChainSpec trims to the chain length (8)");

    // ---------- 5) Unknown symbol fallback ----------
    "Testing unknown symbol fallback (slot 0)…".postln;
    cm1.setSlot(0, \iDoNotExist);
     spec5 = cm1.getSpec;
    assert.(spec5[0] == \testsignal, "Unknown symbol at slot 0 falls back to \\testsignal");

    "Testing unknown symbol fallback (filter slot)…".postln;
    cm1.setSlot(2, \iDoNotExist);
     spec6 = cm1.getSpec;
    assert.(spec6[2] == \bypass, "Unknown symbol at filter slot falls back to \\bypass");

    // ---------- 6) Second chain to test unique naming & independence ----------
    "Creating cm2 (auto-unique name) and playing…".postln;
    cm2 = ChainManager.new(nil, 8).play;
    cm2Name = cm2.getName;
    assert.(cm2Name != cm1Name, "Auto-unique name differs from cm1 name");
    regAfter = ChainManager.allInstances;
    assert.(regAfter.size == 2, "Registry now has two chains (cm1, cm2)");

    // Give cm2 a quick different flavor
    cm2.setSlot(1, \tremolo);
    Ndef(cm2.getName).set(\rate, 9, \depth, 1.0);

    // ---------- 7) Live modulation test (LFO on tremolo rate of cm1) ----------
    "Mapping an LFO to cm1 tremolo rate…".postln;
    lfoName = (cm1.getName.asString ++ "_rateLFO").asSymbol;
    Ndef(lfoName, { LinLin.kr(SinOsc.kr(0.15), -1, 1, 4, 24) }); // rate sweeps 4..24 Hz
    // Map only if a \tremolo is present somewhere; we’ll just set the control regardless:
    // (If the active slot doesn’t expose \\rate, this mapping is harmless.)
    Ndef(cm1.getName).map(\rate, Ndef(lfoName));

    // Unmap in a moment (let it run for 4 seconds)
    AppClock.sched(4.0, {
        "Unmapping LFO from cm1 rate.".postln;
        Ndef(cm1.getName).unmap(\rate);
        Ndef(lfoName).free;
        nil
    });

    // ---------- 8) Fade-time swap test ----------
    "Testing fadeTime by swapping a slot…".postln;
    cm1.fadeTime = 0.25;  // smoother swap
    Ndef(cm1.getName).fadeTime = cm1.fadeTime; // ensure proxy reflects it
    cm1.setSlot(3, \bypass);  // swap something audible (hp -> bypass)

    // ---------- 9) Help + Status ----------
    "Calling .help…".postln;
    cm1.help;  // should print a guide with available processors and return this

    // ---------- 10) Optional: Load external processors (skips if not present) ----------
    // Set this to a real folder if you have descriptor/maker .scd files:
    // e.g., "/Users/you/Projects/Processors/filters"
    testProcessorsDir = nil;
    if (testProcessorsDir.notNil) {
        "Attempting to load processors from: ".post; testProcessorsDir.postln;
        cm1.loadProcessorsFromDir(testProcessorsDir);
        // Try using one of the newly loaded keys if you know them:
        // cm1.setSlot(2, \\myNewFilter);
    } {
        "(Skipping external processor load; testProcessorsDir is nil)".postln;
    };

    // ---------- 11) Stop & free; ensure registry empties ----------
    AppClock.sched(6.0, {
        "Stopping both chains…".postln;
        cm1.stop; cm2.stop;

        "Freeing both chains…".postln;
        var r1 = cm1.free;  // returns removed instance or nil
        var r2 = cm2.free;
        assert.(r1.notNil and: { r2.notNil }, "free() returned non-nil for removed instances");

        regAfter = ChainManager.allInstances;
        assert.(regAfter.size == 0, "Registry empty after freeing all chains");

        footer.value;
        nil
    });
};
)
