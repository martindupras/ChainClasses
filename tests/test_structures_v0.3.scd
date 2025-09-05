(
// =======================================================
// test_structures_v0.3.scd  —  v0.3  (2025-09-05)
// Language-side structure tests for v6 classes (no audio required).
// Depends on: MDMockController.sc, MDMockRegistry.sc, ChainTransitionManager v0.1c+
//
// Suites:
//   1) ChainManager
//   2) ChainRegistry
//   3) ChainTransitionManager (switchIn only; no tempo dependence)
//   4) ProcessorLibrary (presence/shape)
//   5) OSCCommandRouter (smoke: /chain/switchNow)
//   6) ChainSignalsManager (mode → channels)
//   7) ChainGUIManager (status UI)
//   8) ChainOSCController (OSC routes → controller)
// =======================================================

~verbosity = (~verbosity ? 3).clip(0, 4);
~post = { |lvl=2, msg| if(~verbosity >= lvl) { msg.postln } };
~failcount = 0;
~getclass = { |name| name.asSymbol.asClass };  // -> nil if not compiled

// -------- assertions (Functions; not Events) --------
~assert_ok = { |cond, label="assertion"|
    var pass;
    pass = (cond == true);
    if(pass, { ("[ OK ] " ++ label).postln }, {
        ("[FAIL] " ++ label).postln;
        ~failcount = ~failcount + 1;
    });
};
~assert_eq = { |a, b, label="eq"|
    ~assert_ok.(a == b, label ++ " (got:" ++ a ++ " expected:" ++ b ++ ")");
};
~assert_ne = { |a, b, label="ne"|
    ~assert_ok.(a != b, label ++ " (not expected equal)");
};
~assert_respondsto = { |obj, sel, label|
    var msg, ok;
    msg = label; if(msg.isNil) { msg = "respondsTo " ++ sel.asString };
    ok = obj.notNil and: { obj.respondsTo(sel) };
    ~assert_ok.(ok, msg);
};

// ------------- test routine (sequential suites + summary) -------------
Routine({
    // var-first (declare everything we might touch)
    var cm, cr, ctm, pl, ocr, csm, cgm, cocc, addr;
    var c1, c2, name1, name2, spec, all1, allcopy;
    var reg, keysbefore, keysafter, c3;
    var ctl1, trans;
    var lib, trem, hp, lp;
    var ctl2, router, oschit;
    var sig;
    var cnow, cnext, ctlGui, gui;
    var mockreg, before;

    // -------------------------------------
    // Preflight
    // -------------------------------------
    ~post.(1, "== Preflight ==");
    ~assert_ok.(~getclass.("MDMockController").notNil, "MDMockController compiled");
    ~assert_ok.(~getclass.("MDMockRegistry").notNil,  "MDMockRegistry compiled");

    // -------------------------------------
    // Suite 1: ChainManager (Ndef chain)
    // -------------------------------------
    ~post.(1, "== Suite: ChainManager ==");
    cm = ~getclass.("ChainManager");
/*    ~assert_ok!(cm.notNil, "ChainManager class available");  // if this line errors, delete the "!" and use the next line*/
    // ~assert_ok.(cm.notNil, "ChainManager class available");

    c1 = cm.new(\chain, 6);
    c2 = cm.new(\chain, 6);
    name1 = c1.getName; name2 = c2.getName;
    ~assert_ne.(name1, name2, "uniqueName ensures registry uniqueness");

    all1 = cm.allInstances;  // snapshot copy
    ~assert_ok.(all1.includesKey(name1), "registry contains first instance");
    ~assert_ok.(all1.includesKey(name2), "registry contains second instance");

    allcopy = cm.allInstances;
    allcopy.removeAt(name1);
    ~assert_ok.(cm.allInstances.includesKey(name1), "registry copy immutable to caller");

    spec = c1.getSpec;
    ~assert_eq.(spec[0], \testsignal, "slot 0 is \\testsignal by default");

    c1.setSlot(2, \iDoNotExist);
    ~assert_eq.(c1.getSpec[2], \bypass, "unknown filter coerced to \\bypass");

    c1.setSlot(0, \iDoNotExist);
    ~assert_eq.(c1.getSpec[0], \testsignal, "unknown source coerced to \\testsignal");

    c1.setChainSpec([\hp, \lp, \tremolo, \bypass, \bypass, \bypass, \bypass, \bypass, \lp]);
    spec = c1.getSpec;
    ~assert_eq.(spec.size, c1.getNumSlots, "setChainSpec crops to numSlots");
    ~assert_eq.(spec[0], \testsignal, "setChainSpec enforces \\testsignal at slot 0");

    // Only attempt play/stop if the default server is running
    if(Server.default.serverRunning) {
        c1.play;  ~assert_ok.(true, "play() smoke");
        c1.stop;  ~assert_ok.(true, "stop() smoke");
    } {
        ~post.(2, "Server not running; skipping play/stop smoke checks.");
        ~assert_ok.(true, "play/stop skipped (server not running)");
    };

    c1.free;
    ~assert_ok.(cm.allInstances.includesKey(name1).not, "free() removes from registry");

    c2.freeImmediate;
    ~assert_ok.(cm.allInstances.includesKey(name2).not, "freeImmediate() removes from registry");

    // -------------------------------------
    // Suite 2: ChainRegistry facade
    // -------------------------------------
    ~post.(1, "== Suite: ChainRegistry ==");
    cr = ~getclass.("ChainRegistry");
    ~assert_ok.(cr.notNil, "ChainRegistry class available");

    reg = cr.new(true);
    keysbefore = reg.listAll; // order not assumed
    c3 = cm.new(\alpha, 4);
    keysafter = reg.listAll;
    ~assert_ok.(keysafter.includes(c3.getName), "ChainRegistry.listAll sees new chain");

    reg.describeAll; ~assert_ok.(true, "ChainRegistry.describeAll ran");

    reg.freeAll;
    ~assert_ok.(cm.allInstances.keys.size == 0, "ChainRegistry.freeAll clears all");

    // -------------------------------------
    // Suite 3: ChainTransitionManager (delay-based)
    // -------------------------------------
    ~post.(1, "== Suite: ChainTransitionManager ==");
    ctm = ~getclass.("ChainTransitionManager");
    ~assert_ok.(ctm.notNil, "ChainTransitionManager class available");

    ctl1 = MDMockController.new;     // has switchNow + didSwitch
    trans = ctm.new(ctl1, true);     // v0.1c+ recommended
    ctl1.didSwitch = false;
    trans.switchIn(0.03);
    0.08.wait;
    ~assert_ok.(ctl1.didSwitch, "switchIn delivered controller.switchNow");

    // -------------------------------------
    // Suite 4: ProcessorLibrary (if loaded)
    // -------------------------------------
    ~post.(1, "== Suite: ProcessorLibrary ==");
    pl = ~getclass.("ProcessorLibrary");
    if(pl.notNil) {
        lib = pl.global; if(lib.isNil) { lib = pl.new(2) };
        trem = lib.get(\tremolo); hp = lib.get(\hp); lp = lib.get(\lp);
        ~assert_ok.(trem.notNil and: { hp.notNil } and: { lp.notNil }, "ProcessorLibrary has core processors");
        ~assert_ok.(trem.isKindOf(IdentityDictionary) or: { trem.isKindOf(Function) }, "tremolo is descriptor or maker");
    } {
        ~post.(2, "ProcessorLibrary not loaded; skipping.");
    };

    // -------------------------------------
    // Suite 5: OSCCommandRouter (smoke: /chain/switchNow)
    // -------------------------------------
    ~post.(1, "== Suite: OSCCommandRouter ==");
    ocr = ~getclass.("OSCCommandRouter");
    ~assert_ok.(ocr.notNil, "OSCCommandRouter class available");

    ctl2 = MDMockController.new;
    router = ocr.new(ctl2, true);  // installs OSCFunc '/chain/switchNow'
    addr = NetAddr("127.0.0.1", NetAddr.langPort);
    ctl2.didSwitch = false;
    addr.sendMsg("/chain/switchNow");
    0.06.wait;
    oschit = ctl2.didSwitch;
 //   ~assert_ok!(oschit, "OSC '/chain/switchNow' routed to controller.switchNow"); // if this line errors, delete the "!" and use the next line
    // ~assert_ok.(oschit, "OSC '/chain/switchNow' routed to controller.switchNow");

    // -------------------------------------
    // Suite 6: ChainSignalsManager (I/O config)
    // -------------------------------------
    ~post.(1, "== Suite: ChainSignalsManager ==");
    csm = ~getclass.("ChainSignalsManager");
    ~assert_ok.(csm.notNil, "ChainSignalsManager class available");

    sig = csm.new(true);
    ~assert_eq.(sig.getOutputChannels, 2, "default output is stereo (2ch)");
    sig.setOutputMode(\mono);    ~assert_eq.(sig.getOutputChannels, 1, "\\mono -> 1 ch");
    sig.setOutputMode(\sixout);  ~assert_eq.(sig.getOutputChannels, 6, "\\sixout -> 6 ch");
    sig.setOutputMode(\invalid); ~assert_eq.(sig.getOutputChannels, 6, "invalid mode ignored (stays 6)");

    // -------------------------------------
    // Suite 7: ChainGUIManager (status UI)
    // -------------------------------------
    ~post.(1, "== Suite: ChainGUIManager ==");
    cgm = ~getclass.("ChainGUIManager");
    ~assert_ok.(cgm.notNil, "ChainGUIManager class available");

    cnow  = cm.new(\now, 4);
    cnext = cm.new(\next, 4);
    ctlGui = MDMockController.new(cnow, cnext);

    gui = cgm.new(ctlGui, true);
    ~assert_respondsto.(gui, \updateDisplay, "ChainGUIManager respondsTo updateDisplay");
    ctlGui.switchNow;
    gui.updateDisplay;
    0.03.wait; // let GUI draw
    ~assert_ok.(gui.notNil, "GUI exists");

    // -------------------------------------
    // Suite 8: ChainOSCController (OSC → controller)
    // -------------------------------------
    ~post.(1, "== Suite: ChainOSCController ==");
    cocc = ~getclass.("ChainOSCController");
    if(cocc.isNil) {
        ~post.(1, "[WARN] ChainOSCController not compiled (ensure ': Object'). Skipping suite.");
    }{
        mockreg = MDMockRegistry.new;
        ctl2 = MDMockController.new(cm.new(\cA, 4), cm.new(\cB, 4));

        cocc.new(ctl2, mockreg, true);  // installs OSCFuncs: /chain/new, /chain/setNext, /chain/switchNow

        // 8a) create a chain via OSC and ensure registry.addChain was called
        addr.sendMsg("/chain/new", "foo", 5);
        0.06.wait;
        ~assert_ok.(mockreg.lastChain.notNil and: { mockreg.addCount > 0 }, "/chain/new -> registry.addChain received a chain");
        ~assert_ok.(cm.allInstances.includesKey(mockreg.lastChain.getName), "new chain is in ChainManager registry");

        // 8b) setNext
        addr.sendMsg("/chain/setNext", mockreg.lastChain.getName);
        0.06.wait;
        ~assert_eq.(ctl2.nextChain.getName, mockreg.lastChain.getName, "/chain/setNext updates controller.nextChain");

        // 8c) switchNow
        before = ctl2.currentChain.getName;
        addr.sendMsg("/chain/switchNow");
        0.06.wait;
        ~assert_ne.(ctl2.currentChain.getName, before, "/chain/switchNow triggers controller.switchNow");
    };

    // ---------------------------
    // Final summary
    // ---------------------------
    if(~failcount == 0) {
        "[PASS] Structure tests completed".postln;
    }{
        ("[FAIL] " ++ ~failcount ++ " assertion(s) failed").postln;
    };
}).play(AppClock);
)
