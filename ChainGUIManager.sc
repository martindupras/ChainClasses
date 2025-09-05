// ChainGUIManager.sc
// v0.1
// MD 20250905:1330

ChainGUIManager : Object {
	classvar < version = "v0.1";
	var verbose = true;
	var controller;
	var window, currentText, nextText;

	*new { |controller, verbose = true|
		var instance;
		instance = super.new.init(controller, verbose);
		^instance
	}

	init { |ctrl, v|
		var name;
		controller = ctrl;
		verbose = v;
		if (verbose) {
			("[ChainGUIManager] Initialized (% version)").format(version).postln;
		};

		name = "ChainGUIManager_" ++ version;
		window = Window(name, Rect(100, 100, 400, 120));
		currentText = StaticText(window, Rect(10, 10, 380, 40));
		nextText = StaticText(window, Rect(10, 60, 380, 40));

		window.front;
		this.updateDisplay;
		^this
	}

	// safer: won't crash if controller/current/next are nil
	updateDisplay {
		var curObj, nextObj, currentName, nextName;
		curObj = controller.tryPerform(\currentChain);
		nextObj = controller.tryPerform(\nextChain);
		currentName = curObj.tryPerform(\getName) ?? { "None" };
		nextName    = nextObj.tryPerform(\getName) ?? { "None" };
		currentText.string = "Current Chain: " ++ currentName;
		nextText.string    = "Next Chain: " ++ nextName;
		if (verbose) {
			("[ChainGUIManager] Display updated: Current = %, Next = %"
				.format(currentName, nextName)).postln;
		};
		^this
	}

/*
    updateDisplay {
        var currentName, nextName;
        currentName = controller.currentChain.getName ? "None";
        nextName = controller.nextChain.getName ? "None";

        currentText.string = "Current Chain: " ++ currentName;
        nextText.string = "Next Chain: " ++ nextName;

        if (verbose) {
            ("[ChainGUIManager] Display updated: Current = %, Next = %"
                .format(currentName, nextName)).postln;
        };
        ^this
    }*/
}
