// Filename: tests/ChainStarterTest_v0_2.scd
// Description: Step-by-step OSC driving with visible state after each action.
// Notes: Uses the ChainStarter helpers (~send, ~diagnose). Run AFTER ChainStarter is loaded.

(
fork({
    var nameA, nameB;

    // Start clean
    "~cleanup (if needed)".postln;
    if(~cleanup.notNil) { ~cleanup.() };
    0.2.wait;

    // A: New chain and edits
    "~new A".postln;
    ~send.("/chain/new", ["A", 6]);
    0.2.wait;
    "~add tremolo to A@1".postln;
    ~send.("/chain/add", [1, "tremolo"]);
    0.15.wait;
    "~add hp to A@2".postln;
    ~send.("/chain/add", [2, "hp"]);
    0.15.wait;
    ~diagnose.();
    0.1.wait;

    // Intentional wrong setNext (to show robustness when we switch)
    "~setNext WRONG name 'AX'".postln;
    ~send.("/chain/setNext", ["AX"]);
    0.15.wait;

    "~switchNow (auto-promote local next if available)".postln;
    ~send.("/chain/switchNow");
    0.4.wait;
    ~diagnose.();
    0.1.wait;

    // B: Build another chain with setFrom bulk edits
    "~new B".postln;
    ~send.("/chain/new", ["B", 6]);
    0.2.wait;
    "~setFrom start=1 procs [lp,tremolo]".postln;
    ~send.("/chain/setFrom", [1, "lp", "tremolo"]);
    0.2.wait;
    ~diagnose.();

    "~switch to B".postln;
    ~send.("/chain/switchNow");
    0.4.wait;
    ~diagnose.();

    "~remove B@2 (-> bypass)".postln;
    ~send.("/chain/remove", [2]);
    0.2.wait;
    ~diagnose.();

    "~test complete".postln;
}, SystemClock);
)
