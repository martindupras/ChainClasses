// Filename: tests/test_chain_osc_controller_smoke_v0_3_6.scd
// Description: Smoke test for ChainOSCController v0.3.6 (install -> receive -> callbacks -> free)

(
fork({
    var verbose = true;
    var n = NetAddr("127.0.0.1", NetAddr.langPort);
    var controller, defsBeforeFree;
    var gotPing = false, gotSetNext = false, gotSwitch = false;
    var lastSetNextName = nil;

    var handlers = Dictionary[
        \ping -> { arg msg, time, addr, recvPort; gotPing = true; if(verbose) { "[TEST] onPing".postln } },
        \setNext -> { arg nameSym; gotSetNext = true; lastSetNextName = nameSym; if(verbose) { "[TEST] onSetNext %".format(nameSym).postln } },
        \switchNow -> { gotSwitch = true; if(verbose) { "[TEST] onSwitchNow".postln } }
    ];

    controller = ChainOSCController.new("smoke", nil, handlers, true);

    ['/demo/ping','/chain/setNext','/chain/switchNow'].do { arg addrStr;
        var defSym = controller.defNameFor(addrStr.asSymbol);
        if(defSym.isNil or: { OSCdef(defSym).isNil }) {
            ("[TEST][FAIL] OSCdef missing for " ++ addrStr).postln;
        } {
            ("[TEST][OK] OSCdef installed for " ++ addrStr ++ " -> " ++ defSym.asString).postln;
        }
    };

    defsBeforeFree = [
        controller.defNameFor('/demo/ping'.asSymbol),
        controller.defNameFor('/chain/setNext'.asSymbol),
        controller.defNameFor('/chain/switchNow'.asSymbol)
    ];

    n.sendMsg("/demo/ping", "hello");
    n.sendMsg("/chain/setNext", "B");  // normalized to \B
    n.sendMsg("/chain/switchNow");

    0.2.wait;

    if(gotPing) { "[TEST][OK] ping callback".postln } { "[TEST][FAIL] ping callback".postln };
    if(gotSetNext and: { lastSetNextName == \B }) {
        "[TEST][OK] setNext normalized to Symbol \\B".postln
    } {
        ("[TEST][FAIL] setNext, got:" + lastSetNextName.asString).postln
    };
    if(gotSwitch) { "[TEST][OK] switchNow callback".postln } { "[TEST][FAIL] switchNow callback".postln };

    controller.free;
    0.15.wait; // small grace period

    // Use the internal registry for bulletproof checks
    defsBeforeFree.do { arg defSym;
        var gone = OSCdef.all[defSym].isNil;
        if(gone) {
            ("[TEST][OK] removed " ++ defSym.asString).postln
        } {
            ("[TEST][FAIL] still present " ++ defSym.asString).postln
        }
    };

    "--- Smoke test complete ---".postln;
}, SystemClock);
)
