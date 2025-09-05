(
// tests/test_controller_integration_v0.1.scd
// Verifies ChainController cooperates with GUI, OSC, and transition manager.

var cm, ctl, gui, ocrClass, ocr, addr, ctmClass, ctm, next, didSwap;
var now;

// Sanity: classes present
cm = \ChainManager.asClass;
ctl = ChainController.new(true);   // using the v0.2 controller with accessors

// Build two simple chains
now = cm.new(\now, 4);
next    = cm.new(\next, 4);

// Hook controller
ctl.setCurrent(now).setNext(next);

// GUI should read controller.currentChain/nextChain via accessors
ChainGUIManager.new(ctl, true).updateDisplay;

// OSC: route /chain/switchNow to this controller (uses your existing OSCCommandRouter)
ocrClass = \OSCCommandRouter.asClass;
if(ocrClass.notNil) {
    ocr = ocrClass.new(ctl, true);
};

// Transition manager: schedule a switch after 0.03s
ctmClass = \ChainTransitionManager.asClass;
if(ctmClass.notNil) {
    ctm = ctmClass.new(ctl, true);
    ctm.switchIn(0.03);
};

// Also trigger via OSC after a short time (if router present)
addr = NetAddr("127.0.0.1", NetAddr.langPort);
Routine({
    0.08.wait;  // allow transition manager to fire first
    didSwap = ctl.currentChain.notNil and: { ctl.currentChain.getName == \next };
    ("after switchIn: swapped to 'next'? " ++ didSwap).postln;

    if(ocr.notNil) {
        // queue a new next and then call switch via OSC
        ctl.setNext(cm.new(\foo, 4));
        addr.sendMsg("/chain/switchNow");
        0.06.wait;
        didSwap = ctl.currentChain.notNil and: { ctl.currentChain.getName == \foo };
        ("after OSC /chain/switchNow: swapped to 'foo'? " ++ didSwap).postln;
    };
}).play(AppClock);
)
