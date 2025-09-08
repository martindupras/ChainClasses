//readyandlistening.scd
(
// READY & LISTENING — OSC routes for chain editing and switching
// Audio-free; keeps state in a simple structure. Named OSCdefs for hot-reload safety.

//~cleanup, ~post, ~mkChain, ~ensureSlots, ~prettySlots, ~dump, ~state, ~install;

~cleanup = {
    OSCdef.freeAll;
    OSCFunc.removeAll;
    "cleared existing OSC responders".postln;
};

~post = { |label, obj|
    var msg;
    msg = "[ " ++ label ++ " ]: " ++ obj.asString;
    msg.postln;
    nil
};

~mkChain = { |name, slots = 8|
    var c;
    c = (name: name.asSymbol, slots: Array.fill(slots, { nil }));
    ^c
};

~ensureSlots = { |c, n|
    var s;
    s = c[\slots];
    if(s.size < n) {
        c[\slots] = s.copyExtend(n, nil);
    };
    nil
};

~prettySlots = { |slots|
    var a;
    a = slots.collect { |x|
        if(x.isNil) { "-" } { x.asString }
    };
    ^a.join(" | ")
};

~dump = {
    var cur, nxt;
    cur = ~state[\current];
    nxt = ~state[\next];

    "\n[STATE]".postln;
    (" current: " ++ cur[\name]).postln;
    (" slots:   " ++ ~prettySlots.(cur[\slots])).postln;
    (" next:    " ++ nxt[\name]).postln;
    (" slots:   " ++ ~prettySlots.(nxt[\slots])).postln;
    nil
};

~state = (
    current: ~mkChain.(\INIT, 8),
    next: ~mkChain.(\A, 8)
);

~install = {
    var base, id, key;

    base = "MDChain";
    id = this.identityHash;

    ~updateUI = {
        var cur, nxt;
        cur = ~state[\current];
        nxt = ~state[\next];
        ~post.("DISPLAY", "current: % | next: %".format(cur[\name], nxt[\name]));
        nil
    };

    // /chain/create <name> [<slots>]
    key = ("%_%_/chain/create".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var name, slots;
        name = msg[1];
        slots = msg[2];
        if(slots.isNil) { slots = 8 };
        ~state[\next] = ~mkChain.(name, slots);
        ~post.("create", "next := % (slots: %)".format(name, slots));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/create");

    // /chain/add <slot> <processor>
    key = ("%_%_/chain/add".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var slot, proc, c;
        slot = msg[1].asInteger;
        proc = msg[2].asSymbol;
        c = ~state[\next];
        ~ensureSlots.(c, slot + 1);
        c[\slots][slot] = proc;
        ~post.("add", "next[%] := %".format(slot, proc));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/add");

    // /chain/remove <slot>
    key = ("%_%_/chain/remove".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var slot, c;
        slot = msg[1].asInteger;
        c = ~state[\next];
        if(slot >= 0 and: { slot < c[\slots].size }) {
            c[\slots][slot] = nil;
            ~post.("remove", "next[%] cleared".format(slot));
        }{
            ~post.("remove", "slot % out of range".format(slot));
        };
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/remove");

    // /chain/setFrom <startSlot> <p1> <p2> ...
    key = ("%_%_/chain/setFrom".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var start, names, c;
        start = msg[1].asInteger;
        names = msg.copyRange(2, msg.size - 1).collect(_.asSymbol);
        c = ~state[\next];
        ~ensureSlots.(c, start + names.size);
        names.do { |sym, i|
            c[\slots][start + i] = sym;
        };
        ~post.("setFrom", "next from % := %".format(start, names));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/setFrom");

    // /chain/next <name>
    key = ("%_%_/chain/next".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var name;
        name = msg[1].asSymbol;
        ~state[\next][\name] = name;
        ~post.("next", "next name := %".format(name));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/next");

    // /chain/switchNow
    key = ("%_%_/chain/switchNow".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var new;
        new = ~state[\next];
        ~state[\current] = (name: new[\name], slots: new[\slots].copy);
        ~post.("switch", "current := %".format(new[\name]));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/switchNow");

    ~post.("READY", "Listening on NetAddr.langPort: " ++ NetAddr.langPort);
    ~dump.();
    ~updateUI.();
    nil
};

// boot fresh responders and install
~cleanup.();
~install.();
)



// TEST LINES — send OSC to the language port (run lines one at a time)
//~toLang;
~toLang = NetAddr("127.0.0.1", NetAddr.langPort);

// 1) Create a new chain called 'B' with 8 slots
~toLang.sendMsg("/chain/create", "B", 8);

// 2) Put a delay in slot 2  (0-based indexing ⇒ third slot)
~toLang.sendMsg("/chain/add", 2, "delay");

// 3) Remove whatever is in slot 2
~toLang.sendMsg("/chain/remove", 2);

// 4) Put [\tremolo, \phaser, \reverb] starting at slot 3
~toLang.sendMsg("/chain/setFrom", 3, "tremolo", "phaser", "reverb");

// 5) (Optional) Mark next chain label (if you want to rename it)
~toLang.sendMsg("/chain/next", "B");

// 6) Switch from current → next immediately
~toLang.sendMsg("/chain/switchNow");

