// File: tests/test_chain_osc_controller_smoke_v1.3.scd
// MD 2025-09-08
(
// Smoke test: verify install -> receive -> callbacks -> free. Audio-free.
// FIX: expect Symbol (\B) for setNext payload, because the controller does msg[1].asSymbol.

var assert, calls, osc, toLang, dnPing, dnSetNext, dnSwitch;

assert = { |cond, label|
    var ok;
    ok = cond;
    if (ok) { ("PASS: " ++ label).postln } { Error("FAIL: " ++ label).throw };
    nil
};

calls = List.new;

osc = ChainOSCController.new(
    nil,     // controller
    nil,     // registry
    true,    // verbose
    { calls.add(\switchNow) },               // switchFunc
    { |name| calls.add([\setNext, name]) }   // setNextFunc
);

// verify routes installed
dnPing    = osc.defNameForPath("/demo/ping");
dnSetNext = osc.defNameForPath("/chain/setNext");
dnSwitch  = osc.defNameForPath("/chain/switchNow");

assert.(dnPing.notNil    and: { OSCdef(dnPing).notNil    }, "installed /demo/ping");
assert.(dnSetNext.notNil and: { OSCdef(dnSetNext).notNil }, "installed /chain/setNext");
assert.(dnSwitch.notNil  and: { OSCdef(dnSwitch).notNil  }, "installed /chain/switchNow");

// sender to language OSC port
toLang = NetAddr("127.0.0.1", NetAddr.langPort);

// send messages
toLang.sendMsg("/demo/ping", 1);
toLang.sendMsg("/chain/setNext", "B");
toLang.sendMsg("/chain/switchNow");

// wait a moment and assert
Routine({
    var n;
    0.2.wait;
    n = calls.size;

    // debug dump (helpful if a future change breaks expectations)
    // ("calls: " ++ calls.asString).postln;

    assert.(n == 2, "two callbacks received");
    assert.(calls[0] == [\setNext, \B], "setNext payload captured as Symbol");  // <-- FIXED HERE
    assert.(calls[1] == \switchNow, "switchNow captured");

    // cleanup
    osc.free;
    assert.(OSCdef(dnPing).isNil and: { OSCdef(dnSetNext).isNil } and: { OSCdef(dnSwitch).isNil }, "defs freed");

    "OK: tests/test_chain_osc_controller_smoke_v1.3.scd".postln;
}).play(AppClock);
)
