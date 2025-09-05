//tests_ChainManagerv2_1.scd
(
// Minimal quiet tests using no-fade and no-latency helpers

s.waitForBoot {
    // ---------------- VARS FIRST ----------------
    var chain, spec;

    // Test 1: no-latency + no-fade around a slot replacement, then free
    chain = ChainManager.new(\tmpTestA, 8).play;
    chain.withNoLatencyNoFade({
        chain.setSlot(2, \iDoNotExist);
    });
    spec = chain.getSpec;
    ("[Test1] Slot 2 now: " ++ spec[2]).postln;  // -> bypass
    chain.free;  // should be quiet
    // Test 2: explicit helpers
    chain = ChainManager.new(\tmpTestB, 8).play;
    chain.withNoLatency({ chain.setSlotNoFade(2, \iDoNotExist) });
    spec = chain.getSpec;
    ("[Test2] Slot 2 now: " ++ spec[2]).postln;  // -> bypass
    chain.free;  // should be quiet

    // Test 3: batch edit with setChainSpecNoFade then free
    chain = ChainManager.new(\tmpTestC, 8).play;
    chain.withNoLatency({
        chain.setChainSpecNoFade([\testsignal, \hp, \lp, \tremolo, \bypass, \bypass, \bypass, \bypass]);
    });
    spec = chain.getSpec;
    ("[Test3] Spec: " ++ spec.asString).postln;
    chain.free;  // should be quiet
};
)
