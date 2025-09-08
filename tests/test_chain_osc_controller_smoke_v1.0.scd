// File: tests/test_chain_osc_controller_smoke_v1.0.scd
// MD 2025-09-05
(
// Smoke test: install controller, send a couple of OSC messages, assert callbacks were hit.
// Audio‑free. Uses handler Functions instead of real ChainController/ChainManager.

var assert, calls, osc, toLang, dnPing, dnSetNext, dnSwitch;

// tiny assert helper
assert = { |cond, label|
    if (cond) {
        ("PASS: " ++ label).postln;
    }{
        Error("FAIL: " ++ label).throw;
    };
    nil
};

// start fresh (free only defs we’re about to install, in case of reruns)
[
    "/demo/ping",
    "/chain/setNext",
    "/chain/switchNow"
].do { |p|
    var defSym, d;
    defSym = ("ChainOSCController_v0.3_%_" ++ p.replace($/, $_)).format(0); // dummy pattern, just in case
    d = OSCdef(defSym.asSymbol);
    if (d.notNil) { d.free };
};

// record invocations here
calls = List.new;

// Create the controller with handler Functions (so we don't need real classes)
osc = ChainOSCController.new(
    nil,     // controller
    nil,     // registry
    true,    // verbose
    { calls.add(\switchNow) },               // switchFunc
    { |name| calls.add([\setNext, name]) }   // setNextFunc
);

// verify routes installed
dnPing   = osc.defNameForPath("/demo/ping");
dnSetNext= osc.defNameForPath("/chain/setNext");
dnSwitch = osc.defNameForPath("/chain/switchNow");

assert.(dnPing.notNil  and: { OSCdef(dnPing).notNil  }, "installed /demo/ping");
assert.(dnSetNext.notNil and: { OSCdef(dnSetNext).notNil }, "installed /chain/setNext");
assert.(dnSwitch.notNil and: { OSCdef(dnSwitch).notNil }, "installed /chain/switchNow");

// sender to language OSC port
toLang = NetAddr("127.0.0.1", NetAddr.langPort);

// send a few commands
toLang.sendMsg("/demo/ping", 1);
toLang.sendMsg("/chain/setNext", "B");
toLang.sendMsg("/chain/switchNow");

// wait a moment and assert
Routine({
    0.15.wait;
    assert.(calls.size == 2, "two callbacks received");
    assert.(calls[0] == [\setNext, "B"], "setNext payload captured");
    assert.(calls[1] == \switchNow, "switchNow captured");

    // cleanup
    osc.free;
    assert.(OSCdef(dnPing).isNil and: { OSCdef(dnSetNext).isNil } and: { OSCdef(dnSwitch).isNil }, "defs freed");

    "OK: tests/test_chain_osc_controller_smoke_v1.0.scd".postln;
}).play(AppClock);
)
