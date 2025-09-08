// Filename: ChainStatusUI.sc
// Version: v0.1.1
// Purpose: Read-only GUI showing Current and Next chains (name, playing state, slot spec).
// Notes:
// - Display only. Update via .setCurrent(chainOrNil) and .setNext(chainOrNil).
// - Uses a light AppClock poll to keep 'playing' state fresh.
// - Conservative syntax: no '??', no '?', var-first in all Functions.

ChainStatusUI {
    var <window;
    var <currentNameText, <currentStateText, <currentSpecView;
    var <nextNameText, <nextStateText, <nextSpecView;
    var <pollRoutine, <pollHz;
    var <currentChainRef, <nextChainRef;
    var <isOpen;
    var <monoFont;

    *new { arg title = "Chain Status v6", pollRate = 4;
        var ui;
        ui = super.new.init(title, pollRate);
        ^ui
    }

    init { arg title, pollRate;
        var w, left, right, lineH, pad, fontSize, f1, f2;
        pollHz = pollRate;
        isOpen = false;
        currentChainRef = nil;
        nextChainRef = nil;

        fontSize = 12;
        f1 = Font("Monaco", fontSize);
        f2 = Font("Menlo", fontSize);
        monoFont = f1;
        if(monoFont.isNil) { monoFont = f2 };
        if(monoFont.isNil) { monoFont = Font.default };

        w = Window(title, Rect(100, 100, 600, 340)).alwaysOnTop_(true);
        window = w;

        lineH = 22;
        pad = 10;

        left = CompositeView(w, Rect(pad, pad, 280, 320));
        StaticText(left, Rect(0, 0, 200, lineH)).string_("Current").font_(Font.default.size_(14)).background_(Color(0.85, 1.0, 0.85));
        currentNameText = StaticText(left, Rect(0, lineH + 4, 260, lineH)).string_("name: -").font_(Font.default);
        currentStateText = StaticText(left, Rect(0, 2*lineH + 6, 260, lineH)).string_("state: -").font_(Font.default);
        currentSpecView = TextView(left, Rect(0, 3*lineH + 10, 260, 240)).string_("[]").font_(monoFont).editable_(false);

        right = CompositeView(w, Rect(300, pad, 280, 320));
        StaticText(right, Rect(0, 0, 200, lineH)).string_("Next").font_(Font.default.size_(14)).background_(Color(1.0, 0.98, 0.85));
        nextNameText = StaticText(right, Rect(0, lineH + 4, 260, lineH)).string_("name: -").font_(Font.default);
        nextStateText = StaticText(right, Rect(0, 2*lineH + 6, 260, lineH)).string_("state: -").font_(Font.default);
        nextSpecView = TextView(right, Rect(0, 3*lineH + 10, 260, 240)).string_("[]").font_(monoFont).editable_(false);

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

    startPolling {
        var hz, wait, r;
        hz = pollHz;
        if(hz.isNil) { hz = 4 };
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

        cName = "-";
        if(c.notNil) { cName = c.getName.asString };

        nName = "-";
        if(n.notNil) { nName = n.getName.asString };

        cState = "-";
        if(c.notNil) { cState = if(c.isPlaying) { "playing" } { "stopped" } };

        nState = "-";
        if(n.notNil) { nState = if(n.isPlaying) { "playing" } { "stopped" } };

        cSpecStr = "[]";
        if(c.notNil) { cSpecStr = c.getSpec.asString };

        nSpecStr = "[]";
        if(n.notNil) { nSpecStr = n.getSpec.asString };

        currentNameText.string = "name: " ++ cName;
        nextNameText.string = "name: " ++ nName;

        currentStateText.string = "state: " ++ cState;
        nextStateText.string = "state: " ++ nState;

        currentSpecView.string = cSpecStr;
        nextSpecView.string = nSpecStr;

        ^this
    }
}
