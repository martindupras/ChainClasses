// Filename: tests/test_chain_osc_controller_smoke_v0_3_4.scd
// Description: Smoke test for ChainOSCController v0.3.4 (install routes, send OSC, verify callbacks, free responders)

(
fork({
    // var-first
    var verbose = true;
    var n = NetAddr("127.0.0.1", NetAddr.langPort);
    var controller, defsBeforeFree;
    var gotPing = false, gotSetNext = false, gotSwitch = false;
    var lastSetNextName = nil;

    // Handlers (test-friendly, no audio side effects)
    var handlers = Dictionary[
        \ping -> { arg msg, time, addr, recvPort; gotPing = true; if(verbose) { "[TEST] onPing".postln } },
        \setNext -> { arg nameSym; gotSetNext = true; lastSetNextName = nameSym; if(verbose) { "[TEST] onSetNext %".format(nameSym).postln } },
        \switchNow -> { gotSwitch = true; if(verbose) { "[TEST] onSwitchNow".postln } }
    ];

    // Instantiate
    controller = ChainOSCController.new("smoke", nil, handlers, true);

    // Verify named OSCdefs installed
    [
        '/demo/ping',
        '/chain/setNext',
        '/chain/switchNow'
    ].do { arg addrStr;
        var defSym;
        defSym = controller.defNameFor(addrStr.asSymbol);
        if(defSym.isNil or: { OSCdef(defSym).isNil }) {
            ("[TEST][FAIL] OSCdef missing for " ++ addrStr).postln;
        } {
            ("[TEST][OK] OSCdef installed for " ++ addrStr ++ " -> " ++ defSym.asString).postln;
        }
    };

    // Keep def names for post-free checks
    defsBeforeFree = [
        controller.defNameFor('/demo/ping'.asSymbol),
        controller.defNameFor('/chain/setNext'.asSymbol),
        controller.defNameFor('/chain/switchNow'.asSymbol)
    ];

    // Send messages
    n.sendMsg("/demo/ping", "hello");
    n.sendMsg("/chain/setNext", "B");  // normalized to \B
    n.sendMsg("/chain/switchNow");

    // Allow dispatch (legal inside Routine/fork)
    0.2.wait;

    // Assertions (lightweight)
    if(gotPing) { "[TEST][OK] ping callback".postln } { "[TEST][FAIL] ping callback".postln };
    if(gotSetNext and: { lastSetNextName == \B }) {
        "[TEST][OK] setNext normalized to Symbol \\B".postln
    } {
        ("[TEST][FAIL] setNext, got:" + lastSetNextName.asString).postln
    };
    if(gotSwitch) { "[TEST][OK] switchNow callback".postln } { "[TEST][FAIL] switchNow callback".postln };

    // Free and verify removal
    controller.free;
    0.05.wait;

    defsBeforeFree.do { arg defSym;
        if(defSym.notNil and: { OSCdef(defSym).isNil }) {
            ("[TEST][OK] removed " ++ defSym.asString).postln
        } {
            ("[TEST][FAIL] not removed or unknown " ++ defSym.asString).postln
        }
    };

    "--- Smoke test complete ---".postln;
}, SystemClock);
)
