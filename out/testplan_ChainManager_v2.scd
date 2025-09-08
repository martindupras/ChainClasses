// testplan_ChainManager_v2.scd

(
// ChainManager v2 — test plan (var-first in every Function, descriptive lowercase variable names)

s.waitForBoot {
    // -------------------- VARS FIRST --------------------
    var assertFunction, headerFunction, footerFunction, separatorFunction;
    var registryBefore, registryAfter, primaryChain, secondaryChain, primaryChainName, secondaryChainName;
    var lfoNdefName, externalProcessorsDir;
    var specInitial, specAfterAssign, specPadded, specTrimmed, specFallbackSource, specFallbackFilter;

    // ---------- Helper: simple assertion ----------
    assertFunction = { |condition, message|
        var isOk;
        isOk = condition;
        if (isOk) { ("[OK]   " ++ message).postln } { ("[FAIL] " ++ message).warn };
    };

    separatorFunction = { "────────────────────────────────────────────────".postln };
    headerFunction = { |title|
        var localTitle;
        localTitle = title;
        separatorFunction.value; localTitle.postln; separatorFunction.value;
    };
    footerFunction = { separatorFunction.value };

    // ---------- 0) Clean start ----------
    headerFunction.value("ChainManager v2 — Test Plan");

    // If you have old instances around, free them
    if (ChainManager.respondsTo(\allInstances)) {
        registryBefore = ChainManager.allInstances;
        if (registryBefore.notNil and: { registryBefore.size > 0 }) {
            "Freeing pre-existing ChainManager instances…".postln;
            ChainManager.freeAll;
        };
    };

    // ---------- 1) Create first chain (8 slots) ----------
    "Creating primaryChain (8 slots) and playing…".postln;
    primaryChain = ChainManager.new(\myChain, 8);
    primaryChain.play;

    // Check registry has one entry
    registryAfter = ChainManager.allInstances;
    assertFunction.(registryAfter.size == 1, "Registry holds exactly one chain after primaryChain creation");
    primaryChainName = primaryChain.getName;
    assertFunction.(registryAfter.includesKey(primaryChainName), "Registry contains key == primaryChain.getName");

    // ---------- 2) Verify default slots, then edit a few ----------
    specInitial = primaryChain.status;  // posts and returns a copy
    assertFunction.(specInitial[0] == \testsignal, "Slot 0 is \\testsignal by default");
    assertFunction.(specInitial[1] == \bypass, "Slot 1 defaults to \\bypass");

    "Assigning processors to primaryChain: slot 3->hp, 4->lp, 5->tremolo…".postln;
    primaryChain.setSlot(3, \hp).setSlot(4, \lp).setSlot(5, \tremolo);

    specAfterAssign = primaryChain.status;
    assertFunction.(
        specAfterAssign[3] == \hp and: { specAfterAssign[4] == \lp } and: { specAfterAssign[5] == \tremolo },
        "Slots 3..5 updated to \\hp, \\lp, \\tremolo"
    );

    // ---------- 3) Parameter control (generic set) ----------
    "Tweaking params (\\freq, \\rate, \\depth)…".postln;
    Ndef(primaryChain.getName).set(\freq, 600, \rate, 14, \depth, 1.0);
    // (No assert — we’re just verifying no errors are posted.)

    // ---------- 4) Chain spec padding / trimming ----------
    "Testing setChainSpec padding…".postln;
    primaryChain.setChainSpec([\testsignal, \hp, \tremolo]); // shorter than 8; should pad with \bypass
    specPadded = primaryChain.getSpec;
    assertFunction.(specPadded.size == 8, "setChainSpec pads to the chain length (8)");
    assertFunction.(specPadded[0] == \testsignal, "Slot 0 remains a valid source after padding");

    "Testing setChainSpec trimming…".postln;
    primaryChain.setChainSpec([\testsignal, \hp, \lp, \tremolo, \bypass, \hp, \lp, \tremolo, \hp, \lp]); // > 8; trims
    specTrimmed = primaryChain.getSpec;
    assertFunction.(specTrimmed.size == 8, "setChainSpec trims to the chain length (8)");

    // ---------- 5) Unknown symbol fallback ----------
    "Testing unknown symbol fallback (slot 0)…".postln;
    primaryChain.setSlot(0, \iDoNotExist);
    specFallbackSource = primaryChain.getSpec;
    assertFunction.(specFallbackSource[0] == \testsignal, "Unknown symbol at slot 0 falls back to \\testsignal");

    "Testing unknown symbol fallback (filter slot)…".postln;
    primaryChain.setSlot(2, \iDoNotExist);
    specFallbackFilter = primaryChain.getSpec;
    assertFunction.(specFallbackFilter[2] == \bypass, "Unknown symbol at filter slot falls back to \\bypass");

    // ---------- 6) Second chain to test unique naming & independence ----------
    "Creating secondaryChain (auto-unique name) and playing…".postln;
    secondaryChain = ChainManager.new(nil, 8).play;
    secondaryChainName = secondaryChain.getName;
    assertFunction.(secondaryChainName != primaryChainName, "Auto-unique name differs from primaryChain name");
    registryAfter = ChainManager.allInstances;
    assertFunction.(registryAfter.size == 2, "Registry now has two chains (primaryChain, secondaryChain)");

    // Give secondaryChain a quick different flavor
    secondaryChain.setSlot(1, \tremolo);
    Ndef(secondaryChain.getName).set(\rate, 9, \depth, 1.0);

    // ---------- 7) Live modulation test (LFO on tremolo rate of primaryChain) ----------
    "Mapping an LFO to primaryChain tremolo rate…".postln;
    lfoNdefName = (primaryChain.getName.asString ++ "_rateLFO").asSymbol;
    Ndef(lfoNdefName, { LinLin.kr(SinOsc.kr(0.15), -1, 1, 4, 24) }); // rate sweeps 4..24 Hz
    Ndef(primaryChain.getName).map(\rate, Ndef(lfoNdefName));

    // Unmap in a moment (let it run for ~4 seconds)
    AppClock.sched(4.0, {
        // ----- VARS FIRST (closure) -----
        var messageText;
        messageText = "Unmapping LFO from primaryChain rate.";
        messageText.postln;
        Ndef(primaryChain.getName).unmap(\rate);
        Ndef(lfoNdefName).free;
        ^nil
    });

    // ---------- 8) Fade-time swap test ----------
    "Testing fadeTime by swapping a slot…".postln;
    primaryChain.fadeTime = 0.25;                            // smoother swap
    Ndef(primaryChain.getName).fadeTime = primaryChain.fadeTime;      // ensure proxy reflects it
    primaryChain.setSlot(3, \bypass);                        // swap something audible (hp -> bypass)

    // ---------- 9) Help + Status ----------
    "Calling .help…".postln;
    primaryChain.help;  // should print a guide with available processors and return this

    // ---------- 10) Optional: Load external processors (skips if not present) ----------
    externalProcessorsDir = nil; // e.g., "/Users/you/Projects/Processors/filters"
    if (externalProcessorsDir.notNil) {
        "Attempting to load processors from: ".post; externalProcessorsDir.postln;
        primaryChain.loadProcessorsFromDir(externalProcessorsDir);
        // Try using one of the newly loaded keys here if you have any:
        // primaryChain.setSlot(2, \\myNewFilter);
    } {
        "(Skipping external processor load; externalProcessorsDir is nil)".postln;
    };

    // ---------- 11) Stop & free; ensure registry empties ----------
    AppClock.sched(6.0, {
        // ----- VARS FIRST (closure) -----
        var removedPrimary, removedSecondary, registryEnd;

        "Stopping both chains…".postln;
        primaryChain.stop; secondaryChain.stop;

        "Freeing both chains…".postln;
        removedPrimary = primaryChain.free;   // returns removed instance or nil
        removedSecondary = secondaryChain.free;
        assertFunction.(removedPrimary.notNil and: { removedSecondary.notNil }, "free() returned non-nil for removed instances");

        registryEnd = ChainManager.allInstances;
        assertFunction.(registryEnd.size == 0, "Registry empty after freeing all chains");

        footerFunction.value;
        ^nil
    });
};
)

