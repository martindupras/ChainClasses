// readyandlistening2.scd
// MD 20250905-1547

(
// READY & LISTENING — OSC routes for chain editing and switching (audio-free)
// Named OSCdefs for hot-reload safety. Clear receiver logs gated by ~verbose.

// --- config ---
~verbose = true;

// --- helpers ---
~log = { |label, msg|
    if(~verbose) { ("[demo] " ++ label ++ ": " ++ msg.asString).postln };
    nil
};

~cleanup = {
    OSCdef.freeAll;
    OSCFunc.removeAll;
    ~log.("init", "cleared existing OSC responders");
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
    a = slots.collect { |x| if(x.isNil) { "-" } { x.asString } };
    ^a.join(" | ")
};

~dump = {
    var cur, nxt;
    cur = ~state[\current];
    nxt = ~state[\next];

    if(~verbose) {
        "\n[STATE]".postln;
        (" current: " ++ cur[\name]).postln;
        (" slots:   " ++ ~prettySlots.(cur[\slots])).postln;
        (" next:    " ++ nxt[\name]).postln;
        (" slots:   " ++ ~prettySlots.(nxt[\slots])).postln;
    };
    nil
};

// --- in-memory demo state (no audio) ---
~state = (
    current: ~mkChain.(\INIT, 8),
    next: ~mkChain.(\A, 8)
);

// --- install OSC receivers ---
~install = {
    var base, id, key;

    base = "MDChain";
    id = this.identityHash;

    ~updateUI = {
        var cur, nxt;
        cur = ~state[\current];
        nxt = ~state[\next];
        ~log.("display", "current: % | next: %".format(cur[\name], nxt[\name]));
        nil
    };

    // /chain/create <name> [<slots>]
    key = ("%_%_/chain/create".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var name, slots;
        name = msg[1];
        slots = msg[2];
        if(slots.isNil) { slots = 8 };
        ~log.("RECV", "/chain/create name:% slots:% from %".format(name, slots, addr));
        ~state[\next] = ~mkChain.(name, slots);
        ~log.("DO", "next := % (slots: %)".format(name, slots));
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
        ~log.("RECV", "/chain/add slot:% proc:% from %".format(slot, proc, addr));
        c = ~state[\next];
        ~ensureSlots.(c, slot + 1);
        c[\slots][slot] = proc;
        ~log.("DO", "next[%] := %".format(slot, proc));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/add");

    // /chain/remove <slot>
    key = ("%_%_/chain/remove".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var slot, c;
        slot = msg[1].asInteger;
        ~log.("RECV", "/chain/remove slot:% from %".format(slot, addr));
        c = ~state[\next];
        if(slot >= 0 and: { slot < c[\slots].size }) {
            c[\slots][slot] = nil;
            ~log.("DO", "next[%] cleared".format(slot));
        }{
            ~log.("WARN", "slot % out of range".format(slot));
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
        ~log.("RECV", "/chain/setFrom start:% list:% from %".format(start, names, addr));
        c = ~state[\next];
        ~ensureSlots.(c, start + names.size);
        names.do { |sym, i| c[\slots][start + i] = sym };
        ~log.("DO", "next from % := %".format(start, names));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/setFrom");

    // /chain/next <name>
    key = ("%_%_/chain/next".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var name;
        name = msg[1].asSymbol;
        ~log.("RECV", "/chain/next % from %".format(name, addr));
        ~state[\next][\name] = name;
        ~log.("DO", "next name := %".format(name));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/next");

    // /chain/switchNow
    key = ("%_%_/chain/switchNow".format(base, id)).asSymbol;
    OSCdef(key, { |msg, time, addr, recvPort|
        var new;
        ~log.("RECV", "/chain/switchNow from %".format(addr));
        new = ~state[\next];
        ~state[\current] = (name: new[\name], slots: new[\slots].copy);
        ~log.("DO", "current := %".format(new[\name]));
        ~dump.();
        ~updateUI.();
        nil
    }, "/chain/switchNow");

    ~log.("READY", "Listening on NetAddr.langPort: " ++ NetAddr.langPort);
    ~dump.();
    ~updateUI.();
    nil
};

// boot fresh responders and install
~cleanup.();
~install.();
)



// TEST LINES — send OSC to the language port (run lines one at a time)

// convenience sender that returns nil so the interpreter doesn't print the NetAddr
~toLang = NetAddr("127.0.0.1", NetAddr.langPort);
~send = { |path ...args| ~toLang.sendMsg(path, *args); nil };

// 1) Create a new chain called 'B' with 8 slots
~send.("/chain/create", "B", 8);

// 2) Put a delay in slot 2  (0-based indexing ⇒ third slot)
~send.("/chain/add", 2, "delay");

// 3) Remove whatever is in slot 2
~send.("/chain/remove", 2);

// 4) Put [\tremolo, \phaser, \reverb] starting at slot 3
~send.("/chain/setFrom", 3, "tremolo", "phaser", "reverb");

// 5) (Optional) Mark next chain label
~send.("/chain/next", "B");

// 6) Switch from current → next immediately
~send.("/chain/switchNow");
