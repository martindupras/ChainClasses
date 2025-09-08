// Filename: ChainStatusUI.sc
// Version: v0.2.0
// Purpose: Read-only GUI showing Current and Next chains with colored slot boxes.
// Notes:
// - Boxes per slot: source (slot 0) = blue; active effect = green; bypass = light grey; next (queued) = orange.
// - Auto-resizes to show up to 'maxSlots' (detects from current/next chains).
// - Call .setCurrent(chainOrNil) and .setNext(chainOrNil). Calls are GUI-safe (.defer refresh).
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

    // Create N slot boxes in 'panel'
    buildBoxes { arg panel, boxArray, labelArray, slots;
        var i, cols, spacing, boxW, boxH, x0, y0, row, col, bx, by, box, lab;
        var fontSmall;

        // simple grid: up to 8 slots -> 8 in one row; 9+ -> wrap to two rows
        cols = slots.min(8);
        spacing = 6;
        boxW = ((panel.bounds.width - (cols - 1) * spacing).max(160) / cols).clip(28, 80);
        boxH = 44;
        x0 = 0;
        y0 = 0;
        fontSmall = monoFont.size_(11);

        // clear old if any
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

    // Public API
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

    // Utility
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

        cSlots = 0;
        nSlots = 0;

        if(c.notNil) { cSlots = c.getNumSlots };
        if(n.notNil) { nSlots = n.getNumSlots };

        needed = max(cSlots, nSlots);
        if(needed == 0) { needed = maxSlots };
        if(needed != maxSlots) {
            this.setMaxSlots(needed);
            ^this
        };

        cname = "Current: -";
        if(c.notNil) { cname = "Current: " ++ c.getName.asString };
        titleCurrent.string = cname;

        nname = "Next: -";
        if(n.notNil) { nname = "Next: " ++ n.getName.asString };
        titleNext.string = nname;

        cSpec = Array.fill(maxSlots, { \bypass });
        nSpec = Array.fill(maxSlots, { \bypass });

        if(c.notNil) { cSpec = c.getSpec };
        if(n.notNil) { nSpec = n.getSpec };

        i = 0;
        while({ i < maxSlots }, {
            sym = (i < cSpec.size).if({ cSpec[i] }, { \bypass });
            box = currentBoxes[i];
            lab = currentLabels[i];
            color = this.slotColorFor(sym, true, i);
            if(box.notNil) { box.background = color };
            if(lab.notNil) { lab.string = this.shortName(sym) };

            sym = (i < nSpec.size).if({ nSpec[i] }, { \bypass });
            box = nextBoxes[i];
            lab = nextLabels[i];
            color = this.slotColorFor(sym, false, i);
            if(box.notNil) { box.background = color };
            if(lab.notNil) { lab.string = this.shortName(sym) };

            i = i + 1;
        });

        ^this
    }
}
