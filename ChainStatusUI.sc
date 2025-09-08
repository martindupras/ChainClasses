// Filename: ChainStatusUI.sc
// Version: v0.1
// Purpose: Read-only GUI to display "Current" and "Next" chains (name, playing state, slot spec).
// Notes:
// - Display only (no interaction). The controller is not required.
// - Call .setCurrent(chainOrNil) and .setNext(chainOrNil) from your runtime to keep it in sync.
// - Uses a lightweight AppClock poll to keep 'playing' states fresh.
// - Follows var-first rule in all Functions; no multi-line comments or exotic syntax.

ChainStatusUI {
    var <window;
    var <currentNameText, <currentStateText, <currentSpecView;
    var <nextNameText, <nextStateText, <nextSpecView;
    var <pollRoutine, <pollHz;
    var <currentChainRef, <nextChainRef;
    var <isOpen;
    var <monospace;

    *new { arg title = "Chain Status v6", pollHz = 4;
        var ui;
        ui = super.new.init(title, pollHz);
        ^ui
    }

    init { arg title, pollRate;
        var w, top, left, right, lineH, pad, fontSize;
        pollHz = pollRate;
        isOpen = false;
        currentChainRef = nil;
        nextChainRef = nil;

        fontSize = 12;
        monospace = Font("Monaco", fontSize) ? Font("Menlo", fontSize) ? Font.default;

        w = Window(title, Rect(100, 100, 600, 340)).alwaysOnTop_(true);
        window = w;

        // Layout constants
        lineH = 22;
        pad = 10;

        // LEFT COLUMN: CURRENT
        left = CompositeView(w, Rect(pad, pad, 280, 320));
        StaticText(left, Rect(0, 0, 200, lineH)).string_("Current").font_(Font.default.size_(14)).background_(Color(0.85, 1.0, 0.85));
        currentNameText = StaticText(left, Rect(0, lineH + 4, 260, lineH)).string_("name: -").font_(Font.default);
        currentStateText = StaticText(left, Rect(0, 2*lineH + 6, 260, lineH)).string_("state: -").font_(Font.default);
        currentSpecView = TextView(left, Rect(0, 3*lineH + 10, 260, 240))
            .string_("[]")
            .font_(monospace)
            .editable_(false);

        // RIGHT COLUMN: NEXT
        right = CompositeView(w, Rect(300, pad, 280, 320));
        StaticText(right, Rect(0, 0, 200, lineH)).string_("Next").font_(Font.default.size_(14)).background_(Color(1.0, 0.98, 0.85));
        nextNameText = StaticText(right, Rect(0, lineH + 4, 260, lineH)).string_("name: -").font_(Font.default);
        nextStateText = StaticText(right, Rect(0, 2*lineH + 6, 260, lineH)).string_("state: -").font_(Font.default);
        nextSpecView = TextView(right, Rect(0, 3*lineH + 10, 260, 240))
            .string_("[]")
            .font_(monospace)
            .editable_(false);

        w.onClose_({
            var wasOpen;
            wasOpen = isOpen;
            if(wasOpen) { this.stopPolling };
            isOpen = false;
        });

        isOpen = true;
        w.front;
        this.startPolling;
        this.refresh;
        ^this
    }

    // --- Public API ---

    setCurrent { arg chainOrNil;
        var ch;
        ch = chainOrNil;
        currentChainRef = ch;
        this.refresh;
        ^this
    }

    setNext { arg chainOrNil;
        var ch;
        ch = chainOrNil;
        nextChainRef = ch;
        this.refresh;
        ^this
    }

    front {
        var w;
        w = window;
        if(w.notNil) { w.front };
        ^this
    }

    free {
        var w;
        this.stopPolling;
        w = window;
        if(w.notNil) { w.close };
        window = nil;
        ^this
    }

    // --- Internal ---

    startPolling {
        var r, hz, wait;
        hz = pollHz ? 4;
        wait = (1.0 / hz).max(0.05);
        this.stopPolling;
        r = Routine({
            var localWait;
            localWait = wait;
            while { window.notNil } {
                this.refresh;
                localWait.wait;
            }
        });
        pollRoutine = r.play(AppClock);
        ^this
    }

    stopPolling {
        var r;
        r = pollRoutine;
        if(r.notNil) { r.stop; pollRoutine = nil };
        ^this
    }

    refresh {
        var c, n, cName, nName, cState, nState, cSpecStr, nSpecStr;

        c = currentChainRef;
        n = nextChainRef;

        cName = if(c.notNil) { c.getName.asString } { "-" };
        nName = if(n.notNil) { n.getName.asString } { "-" };

        cState = if(c.notNil and: { c.isPlaying }) { "playing" } { if(c.notNil) { "stopped" } { "-" } };
        nState = if(n.notNil and: { n.isPlaying }) { "playing" } { if(n.notNil) { "stopped" } { "-" } };

        cSpecStr = if(c.notNil) { c.getSpec.asString } { "[]" };
        nSpecStr = if(n.notNil) { n.getSpec.asString } { "[]" };

        currentNameText.string = "name: " ++ cName;
        nextNameText.string = "name: " ++ nName;

        currentStateText.string = "state: " ++ cState;
        nextStateText.string = "state: " ++ nState;

        currentSpecView.string = cSpecStr;
        nextSpecView.string = nSpecStr;

        ^this
    }
}
