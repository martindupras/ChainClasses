// Filename: ChainStatusUI.sc
// Version: v0.2.2
// Purpose: Read-only GUI showing Current and Next chains with colored slot boxes.
// Notes:
// - Boxes per slot: source (slot 0) = blue; active effect = green; bypass = light grey; next (queued) = orange.
// - Auto-resizes up to maxSlots by observing current/next chain sizes.
// - Call .setCurrent(chainOrNil) and .setNext(chainOrNil). Calls schedule refresh on AppClock.
// - Conservative syntax: no '??', no '?', var-first in all Functions.

ChainStatusUI {
	var <window;
	var <currentPanel, <nextPanel;
	var <currentBoxes, <nextBoxes;
	var <currentLabels, <nextLabels;
	var <titleCurrent, <titleNext, <legendText;
	var <pollRoutine, <pollHz;
	var <currentChainRef, <nextChainRef;
	var <maxSlots;
	var <monoFont;

	*new { arg title = "Chain Status v6", pollRate = 4;
		var ui;
		ui = super.new.init(title, pollRate);
		^ui
	}

	init { arg title, pollRate;
		var w, lineH, pad, fontSize, f1, f2;
		pollHz = pollRate;
		currentChainRef = nil;
		nextChainRef = nil;
		maxSlots = 8;

		fontSize = 12;
		f1 = Font("Monaco", fontSize);
		f2 = Font("Menlo", fontSize);
		monoFont = f1;
		if(monoFont.isNil) { monoFont = f2 };
		if(monoFont.isNil) { monoFont = Font.default };

		lineH = 22;
		pad = 10;

		w = Window(title, Rect(100, 100, 720, 360)).alwaysOnTop_(true);
		window = w;

		titleCurrent = StaticText(w, Rect(pad, pad, 320, lineH))
		.string_("Current")
		.font_(Font.default.size_(14))
		.background_(Color(0.85, 1.0, 0.85));

		titleNext = StaticText(w, Rect(360, pad, 320, lineH))
		.string_("Next")
		.font_(Font.default.size_(14))
		.background_(Color(1.0, 0.98, 0.85));

		currentPanel = CompositeView(w, Rect(pad, pad + lineH + 6, 320, 260)).background_(Color.white);
		nextPanel    = CompositeView(w, Rect(360, pad + lineH + 6, 320, 260)).background_(Color.white);

		legendText = StaticText(w, Rect(pad, 330, 680, 18))
		.string_("Legend: source=blue | active=green | bypass=grey | queued(next)=orange")
		.font_(Font.default.size_(11));

		currentBoxes = Array.new;
		nextBoxes = Array.new;
		currentLabels = Array.new;
		nextLabels = Array.new;

		this.buildBoxes(currentPanel, currentBoxes, currentLabels, maxSlots);
		this.buildBoxes(nextPanel,    nextBoxes,    nextLabels,    maxSlots);

		w.onClose_({
			var r;
			r = pollRoutine;
			if(r.notNil) { r.stop; pollRoutine = nil };
		});

		this.startPolling;
		{ this.refresh }.defer;
		w.front;
		^this
	}

	buildBoxes { arg panel, boxArray, labelArray, slots;
		var i, cols, spacing, boxW, boxH, x0, y0, row, col, bx, by, box, lab, fontSmall;
		cols = slots.min(8);
		spacing = 6;
		boxW = ((panel.bounds.width - (cols - 1) * spacing).max(160) / cols).clip(28, 80);
		boxH = 44;
		x0 = 0;
		y0 = 0;
		fontSmall = monoFont.size_(11);

		boxArray.do({ arg v; v.remove });
		labelArray.do({ arg v; v.remove });
		boxArray.clear; labelArray.clear;

		i = 0;
		while({ i < slots }, {
			row = (i / 8).floor;
			col = i % 8;
			bx = x0 + col * (boxW + spacing);
			by = y0 + row * (boxH + spacing);

			box = CompositeView(panel, Rect(bx, by, boxW, boxH)).background_(Color(0.95, 0.95, 0.95));
			lab = StaticText(box, Rect(4, 4, boxW - 8, boxH - 8))
			.string_("-")
			.font_(fontSmall);

			boxArray.add(box);
			labelArray.add(lab);

			i = i + 1;
		});
	}

	setCurrent { arg chainOrNil;
		var ch;
		ch = chainOrNil;
		currentChainRef = ch;
		{ this.refresh }.defer;
		^this
	}

	setNext { arg chainOrNil;
		var ch;
		ch = chainOrNil;
		nextChainRef = ch;
		{ this.refresh }.defer;
		^this
	}

	setMaxSlots { arg slots;
		var s;
		s = slots;
		if(s.isNil) { s = maxSlots };
		if(s < 1) { s = 1 };
		if(s > 16) { s = 16 };
		maxSlots = s;

		this.buildBoxes(currentPanel, currentBoxes, currentLabels, maxSlots);
		this.buildBoxes(nextPanel,    nextBoxes,    nextLabels,    maxSlots);
		{ this.refresh }.defer;
		^this
	}

	front {
		var w;
		w = window;
		if(w.notNil) { w.front };
		^this
	}

	free {
		var w, r;
		r = pollRoutine;
		if(r.notNil) { r.stop; pollRoutine = nil };
		w = window;
		if(w.notNil) { w.close };
		window = nil;
		^this
	}

	startPolling {
		var hz, wait, r;
		hz = pollHz;
		if(hz.isNil) { hz = 4 };
		wait = (1.0 / hz).max(0.05);
		r = Routine({
			var localWait;
			localWait = wait;
			while({ window.notNil }, {
				this.refresh;
				localWait.wait;
			});
		});
		pollRoutine = r.play(AppClock);
		^this
	}

	slotColorFor { arg symbol, isCurrent, index;
		var sym, isByp, isSrc;
		sym = symbol.asSymbol;
		isByp = (sym == \bypass);
		isSrc = (index == 0);
		if(isSrc) { ^Color(0.60, 0.80, 1.00) };
		if(isByp and: { isCurrent }) { ^Color(0.90, 0.90, 0.90) };
		if(isByp and: { isCurrent.not }) { ^Color(0.93, 0.93, 0.93) };
		if(isCurrent) { ^Color(0.70, 1.00, 0.70) };
		^Color(1.00, 0.78, 0.40)
	}

	shortName { arg symbol;
		var s;
		s = symbol.asString;
		if(s.size > 8) { ^(s.copyRange(0, 7) ++ "…") };
		^s
	}


refresh {
    var c, n, cSpec, nSpec, cSlots, nSlots, needed;
    var i, sym, box, lab, color, cname, nname;

    c = currentChainRef;
    n = nextChainRef;

    // EARLY: See what the UI is holding
    this.traceOnce;
    this.validateViews;

    // ... your existing refresh code ...

    // After cSpec/nSpec are set and before painting:
    cSpec = Array.fill(maxSlots, { \bypass });
    nSpec = Array.fill(maxSlots, { \bypass });
    if(c.notNil) { cSpec = c.getSpec };
    if(n.notNil) { nSpec = n.getSpec };

    // LATE: confirm what will actually be painted
    ("[ChainStatusUI] paint with maxSlots:%  cur:%  next:%")
    .format(maxSlots, cSpec, nSpec).postln;

    i = 0;
    while({ i < maxSlots }, {
        // ... paint as you already do ...
        i = i + 1;
    });
    ^this
}



	// Add to ChainStatusUI (near utilities)
	normalizeToSymbol { arg item, fallback=\bypass;
		var sym;
		if(item.isNil) { ^fallback };
		if(item.isKindOf(Symbol)) { ^item };
		if(item.isKindOf(String)) { ^item.asSymbol };
		if(item.respondsTo(\key)) {  // Associations
			sym = item.key; if(sym.isKindOf(Symbol)) { ^sym };
		};
		if(item.isKindOf(Dictionary)) {
			sym = item[\name]; if(sym.notNil) { ^sym.asSymbol };
			^fallback;
		};
		^fallback
	}

	// --- Add to ChainStatusUI ---

validateViews {
    var ok;
    ok = true;

    if(window.isNil) {
        "ChainStatusUI.validateViews: window is nil (closed?)".warn;
        ok = false;
    };

    if(currentBoxes.size != maxSlots) {
        ("ChainStatusUI: currentBoxes.size % vs maxSlots %")
        .format(currentBoxes.size, maxSlots).warn;
        ok = false;
    };
    if(nextBoxes.size != maxSlots) {
        ("ChainStatusUI: nextBoxes.size % vs maxSlots %")
        .format(nextBoxes.size, maxSlots).warn;
        ok = false;
    };
    if(currentLabels.size != maxSlots or: { nextLabels.size != maxSlots }) {
        ("ChainStatusUI: label array sizes mismatch (cur % next % max %)")
        .format(currentLabels.size, nextLabels.size, maxSlots).warn;
        ok = false;
    };
    ^ok
}

traceOnce {
    var c, n, cSpec, nSpec, cName, nName;
    c = currentChainRef; n = nextChainRef;
    cName = if(c.notNil) { c.getName } { "nil" };
    nName = if(n.notNil) { n.getName } { "nil" };
    cSpec = if(c.notNil) { c.getSpec } { [] };
    nSpec = if(n.notNil) { n.getSpec } { [] };

    ("[ChainStatusUI] TRACE  cur:% next:%  curSpec:%  nextSpec:%")
    .format(cName, nName, cSpec, nSpec).postln;
    ^this
}

}
