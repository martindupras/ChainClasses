/*  Filename: ChainStatusUI.sc
    Version:  v0.3.2
    Purpose:  Read-only GUI showing Current and Next chains with colored slot boxes.

    Key fixes:
      - Use List instead of Array for box/label collections (Array.add returns a new Array; List.add mutates).
      - Re-entrancy guard uses a normal ivar (no accessor, no underscore): isPaintingLock.
      - Defaults set in init rather than in var declarations.

    Notes:
      - Boxes per slot: source (slot 0) = blue; active effect = green; bypass = light grey; next (queued) = orange.
      - Auto-resizes up to maxSlots by observing current/next chain sizes.
      - Call .setCurrent(chainOrNil) and .setNext(chainOrNil). UI refresh is AppClock-safe.
      - Optional debug prints: pass debug:true to *new or use .setDebug(true).
*/

ChainStatusUI : Object {
    var <window;
    var <currentPanel, <nextPanel;
    var <currentBoxes, <nextBoxes;       // List
    var <currentLabels, <nextLabels;     // List
    var <titleCurrent, <titleNext, <legendText;
    var <pollRoutine, <pollHz;
    var <currentChainRef, <nextChainRef;
    var <maxSlots;
    var <monoFont;
    var <debug;                // set via setDebug
    var isPaintingLock;        // re-entrancy guard (no accessor, no underscore)

    *new { arg title = "Chain Status v6", pollRate = 4, debug = false;
        ^super.new.init(title, pollRate, debug)
    }

    init { arg title, pollRate, debug = false;
        var w, lineH, pad, fontSize, f1, f2;

        // defaults
        isPaintingLock = false;
        this.setDebug(debug);

        pollHz = pollRate;
        currentChainRef = nil;
        nextChainRef    = nil;
        maxSlots        = 8;

        fontSize = 12;
        f1 = Font("Monaco", fontSize);
        f2 = Font("Menlo",  fontSize);
        monoFont = f1;
        if(monoFont.isNil) { monoFont = f2 };
        if(monoFont.isNil) { monoFont = Font.default };

        lineH = 22;
        pad   = 10;

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

        currentPanel = CompositeView(w, Rect(pad,  pad + lineH + 6, 320, 260)).background_(Color.white);
        nextPanel    = CompositeView(w, Rect(360, pad + lineH + 6, 320, 260)).background_(Color.white);

        legendText = StaticText(w, Rect(pad, 330, 680, 18))
            .string_("Legend: source=blue | active=green | bypass=grey | queued(next)=orange")
            .font_(Font.default.size_(11));

        // Lists (mutate in place with .add)
        currentBoxes  = List.new;
        nextBoxes     = List.new;
        currentLabels = List.new;
        nextLabels    = List.new;

        // Build initial views to maxSlots; no chain yet
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

    // --- Diagnostics & control ---

    setDebug { arg flag = true;
        debug = (flag == true);
        if(debug) {
            this.log("debug enabled (id:" ++ this.identityHash.asString ++ ")");
        };
        ^this
    }

    describe {
        var curN, nxtN;
        curN = if(currentChainRef.notNil) { currentChainRef.getName } { "nil" };
        nxtN = if(nextChainRef.notNil)    { nextChainRef.getName    } { "nil" };
        (
            "[ChainStatusUI] id:% maxSlots:% curBoxes:% nextBoxes:% curLabels:% nextLabels:% cur:% next:%"
        ).format(
            this.identityHash, maxSlots,
            currentBoxes.size, nextBoxes.size,
            currentLabels.size, nextLabels.size,
            curN, nxtN
        ).postln;
        ^this
    }

    validateViews {
        var ok;
        ok = true;
        if(window.isNil) {
            "WARNING: ChainStatusUI.validateViews: window is nil (closed?)".warn;
            ok = false;
        };
        if(currentBoxes.size != maxSlots) {
            ("WARNING: ChainStatusUI: currentBoxes.size % vs maxSlots %")
            .format(currentBoxes.size, maxSlots).warn;
            ok = false;
        };
        if(nextBoxes.size != maxSlots) {
            ("WARNING: ChainStatusUI: nextBoxes.size % vs maxSlots %")
            .format(nextBoxes.size, maxSlots).warn;
            ok = false;
        };
        if(currentLabels.size != maxSlots or: { nextLabels.size != maxSlots }) {
            ("WARNING: ChainStatusUI: label array sizes mismatch (cur % next % max %)")
            .format(currentLabels.size, nextLabels.size, maxSlots).warn;
            ok = false;
        };
        ^ok
    }

    log { arg line;
        if(debug) { ("[ChainStatusUI] " ++ line.asString).postln; };
        ^this
    }

    // --- View construction ---

    buildBoxes { arg panel, boxArray, labelArray, slots;
        var s, i, cols, spacing, boxW, boxH, x0, y0, row, col, bx, by, box, lab, fontSmall;

        s = slots.asInteger;
        if(s.isNil or: { s < 1 }) { s = 1 };
        if(s > 16) { s = 16 };

        if(debug) { this.log(("buildBoxes(slots:%) panel:%").format(s, panel.bounds)); };

        cols = s.min(8).max(1);
        spacing = 6;
        boxW = ((panel.bounds.width - (cols - 1) * spacing).max(160) / cols).clip(28, 80);
        boxH = 44;
        x0 = 0; y0 = 0;
        fontSmall = monoFont.size_(11);

        // Clear existing child views and collections
        boxArray.do({ arg v; v.remove });
        labelArray.do({ arg v; v.remove });
        boxArray.clear; labelArray.clear;

        i = 0;
        while({ i < s }, {
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

        if(debug) {
            this.log(("built arrays -> boxes:% labels:% (this side)")
                .format(boxArray.size, labelArray.size));
        };
        ^this
    }

    // --- Public API (runtime hooks) ---

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
        if(debug) { this.log(("setMaxSlots request:% (prev max:%)").format(s, maxSlots)); };

        if(s.isNil) { s = maxSlots };
        if(s < 1)   { s = 1 };
        if(s > 16)  { s = 16 };
        maxSlots = s;

        this.buildBoxes(currentPanel, currentBoxes, currentLabels, maxSlots);
        this.buildBoxes(nextPanel,    nextBoxes,    nextLabels,    maxSlots);

        if(debug) {
            this.log(("setMaxSlots done -> max:% curBoxes:% nextBoxes:%")
                .format(maxSlots, currentBoxes.size, nextBoxes.size));
        };

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

    // --- Poller ---

    startPolling {
        var hz, wait, r;

        if(pollRoutine.notNil) { pollRoutine.stop; pollRoutine = nil; };

        hz = pollHz;
        if(hz.isNil) { hz = 4 };
        if(hz <= 0)  { ^this }; // disabled

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

    // --- Paint helpers ---

    slotColorFor { arg symbol, isCurrent, index;
        var sym, isByp, isSrc;
        sym = symbol.asSymbol;
        isByp = (sym == \bypass);
        isSrc = (index == 0);

        if(isSrc)                        { ^Color(0.60, 0.80, 1.00) }; // blue: source
        if(isByp and: { isCurrent })     { ^Color(0.90, 0.90, 0.90) }; // light grey (current)
        if(isByp and: { isCurrent.not }) { ^Color(0.93, 0.93, 0.93) }; // slightly lighter grey (next)
        if(isCurrent)                    { ^Color(0.70, 1.00, 0.70) }; // green: active
        ^Color(1.00, 0.78, 0.40)                                      // orange: queued (next)
    }

    shortName { arg symbol;
        var s;
        s = symbol.asString;
        if(s.size > 8) { ^(s.copyRange(0, 7) ++ "…") };
        ^s
    }

    normalizeToSymbol { arg item, fallback = \bypass;
        var sym;
        if(item.isNil)            { ^fallback };
        if(item.isKindOf(Symbol)) { ^item     };
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

    // --- Main paint ---

    refresh {
        var c, n, cSpec, nSpec, cSlots, nSlots, needed;
        var i, sym, box, lab, color, cname, nname;

        if(window.isNil) { ^this }; // closed

        // Re-entrancy guard
        if(isPaintingLock) { ^this };
        isPaintingLock = true;

        if(debug) { this.validateViews; };

        c = currentChainRef;
        n = nextChainRef;

        cSlots = 0; if(c.notNil) { cSlots = c.getNumSlots };
        nSlots = 0; if(n.notNil) { nSlots = n.getNumSlots };

        needed = cSlots.max(nSlots);
        if(needed == 0) { needed = maxSlots };

        // Resize and bail; deferred refresh will repaint freshly built views.
        if(needed != maxSlots) {
            if(debug) { this.log(("resize: needed:% (cur max:%) — rebuilding").format(needed, maxSlots)); };
            this.setMaxSlots(needed);
            isPaintingLock = false;
            ^this;
        };

        cname = "Current: -"; if(c.notNil) { cname = "Current: " ++ c.getName.asString };
        titleCurrent.string_(cname);

        nname = "Next: -"; if(n.notNil) { nname = "Next: " ++ n.getName.asString };
        titleNext.string_(nname);

        cSpec = Array.fill(maxSlots, { \bypass });
        nSpec = Array.fill(maxSlots, { \bypass });
        if(c.notNil) { cSpec = c.getSpec };
        if(n.notNil) { nSpec = n.getSpec };

        if(debug) {
            this.log(("paint with maxSlots:%  cur:%  next:%  (curName:% nextName:%)")
                .format(
                    maxSlots, cSpec, nSpec,
                    (c.notNil).if({ c.getName }, { "nil" }),
                    (n.notNil).if({ n.getName }, { "nil" })
                ));
        };

        i = 0;
        while({ i < maxSlots }, {
            // Current
            sym  = (i < cSpec.size).if({ this.normalizeToSymbol(cSpec[i]) }, { \bypass });
            box  = currentBoxes[i];
            lab  = currentLabels[i];
            color = this.slotColorFor(sym, true, i);
            if(box.notNil) { box.background_(color) };
            if(lab.notNil) { lab.string_(this.shortName(sym)) };

            // Next
            sym  = (i < nSpec.size).if({ this.normalizeToSymbol(nSpec[i]) }, { \bypass });
            box  = nextBoxes[i];
            lab  = nextLabels[i];
            color = this.slotColorFor(sym, false, i);
            if(box.notNil) { box.background_(color) };
            if(lab.notNil) { lab.string_(this.shortName(sym)) };

            i = i + 1;
        });

        isPaintingLock = false;
        ^this
    }
}
