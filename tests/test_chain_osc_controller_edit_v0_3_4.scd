// Filename: tests/test_chain_osc_controller_edit_v0_3_4.scd
// Description: Tests edit routes for ChainOSCController v0.3.4 using handler Functions (no audio)

(
fork({
    // var-first
    var verbose = true;
    var n = NetAddr("127.0.0.1", NetAddr.langPort);
    var controller;

    // Captured results
    var calls = (
        new: List.new,
        add: List.new,
        remove: List.new,
        setFrom: List.new
    );

    // Handlers
    var handlers = Dictionary[
        \new -> { arg nameSym, slots; calls[\new].add([nameSym, slots]); if(verbose) { "[TEST] onNew name:% slots:%".format(nameSym, slots).postln } },
        \add -> { arg slot, procSym; calls[\add].add([slot, procSym]); if(verbose) { "[TEST] onAdd slot:% proc:%".format(slot, procSym).postln } },
        \remove -> { arg slot; calls[\remove].add([slot]); if(verbose) { "[TEST] onRemove slot:%".format(slot).postln } },
        \setFrom -> { arg start, procs; calls[\setFrom].add([start, procs]); if(verbose) { "[TEST] onSetFrom start:% procs:%".format(start, procs).postln } }
    ];

    // Instantiate
    controller = ChainOSCController.new("edit", nil, handlers, true);

    // Verify OSCdefs exist for edit routes
    [
        '/chain/new',
        '/chain/add',
        '/chain/remove',
        '/chain/setFrom'
    ].do { arg addrStr;
        var defSym;
        defSym = controller.defNameFor(addrStr.asSymbol);
        if(defSym.isNil or: { OSCdef(defSym).isNil }) {
            ("[TEST][FAIL] OSCdef missing for " ++ addrStr).postln;
        } {
            ("[TEST][OK] OSCdef installed for " ++ addrStr ++ " -> " ++ defSym.asString).postln;
        }
    };

    // Send messages (0-based slots)
    n.sendMsg("/chain/new", "Edit", 6);
    n.sendMsg("/chain/add", 2, "tremolo");
    n.sendMsg("/chain/remove", 3);
    n.sendMsg("/chain/setFrom", 1, "hp", "lp", "tremolo");

    // Allow dispatch
    0.2.wait;

    // Assertions
    if(calls[\new].size == 1 and: { calls[\new][0][0] == \Edit } and: { calls[\new][0][1] == 6 }) {
        "[TEST][OK] /chain/new normalization and slots".postln
    } {
        ("[TEST][FAIL] /chain/new got:" + calls[\new].asString).postln
    };

    if(calls[\add].size == 1 and: { calls[\add][0][0] == 2 } and: { calls[\add][0][1] == \tremolo }) {
        "[TEST][OK] /chain/add slot=2 proc=\\tremolo".postln
    } {
        ("[TEST][FAIL] /chain/add got:" + calls[\add].asString).postln
    };

    if(calls[\remove].size == 1 and: { calls[\remove][0][0] == 3 }) {
        "[TEST][OK] /chain/remove slot=3".postln
    } {
        ("[TEST][FAIL] /chain/remove got:" + calls[\remove].asString).postln
    };

    if(
        calls[\setFrom].size == 1 and: {
            calls[\setFrom][0][0] == 1
        } and: {
            calls[\setFrom][0][1] == [\hp, \lp, \tremolo]
        }
    ) {
        "[TEST][OK] /chain/setFrom start=1 procs=[\\hp, \\lp, \\tremolo]".postln
    } {
        ("[TEST][FAIL] /chain/setFrom got:" + calls[\setFrom].asString).postln
    };

    // Cleanup
    controller.free;
    0.05.wait;
    "--- Edit routes test complete ---".postln;
}, SystemClock);
)
