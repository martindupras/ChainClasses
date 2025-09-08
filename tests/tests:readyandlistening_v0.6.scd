// tests/readyandlistening_v0.6.scd
// MD 20250905
(
var verbose, log, cleanup, mkChain, ensureSlots, prettySlots, dump, state, install;

// --- config ---
verbose = true;

// --- helpers (lexical, safely closed over by OSCdef callbacks) ---
log = { |label, msg|
    if(verbose) { ("[demo] " ++ label ++ ": " ++ msg.asString).postln };
    nil
};

cleanup = {
    OSCdef.freeAll;
    OSCFunc.removeAll;
    log.("init", "cleared existing OSC responders");
};

mkChain = { |name, slots = 8|
    ^(name: name.asSymbol, slots: Array.fill(slots, { nil }))
};

ensureSlots = { |c, n|
    var s;
    s = c[\slots];
    if(s.size < n) { c[\slots] = s.copyExtend(n, nil) };
    nil
};

prettySlots = { |slots|
    ^slots.collect { |x| if(x.isNil) { "-" } { x.asString } }.join(" | ")
};

dump = {
    var cur, nxt;
    cur = state[\current];
    nxt = state[\next];
    if(verbose) {
        "\n[STATE]".postln;
        (" current: " ++ cur[\name]).postln;
        (" slots:   " ++ prettySlots.(cur[\slots])).postln;
        (" next:    " ++ nxt[\name]).postln;
        (" slots:   " ++ prettySlots.(nxt[\slots])).postln;
    };
    nil
};

// --- in-memory demo state (no audio) ---
state = (
    current: mkChain.(\INIT, 8),
    next: mkChain.(\A, 8)
);

// --- install OSC receivers (fixed, named OSCdefs) ---
install = {
    // /demo/ping — sanity check that receivers are alive
    OSCdef(\md_demo_ping, { |msg, time, addr, recvPort|
        log.("RECV", "/demo/ping % from %".format(msg.copyRange(1, msg.size-1), addr));
        log.("DO", "pong");
        nil
    }, "/demo/ping");

    // /chain/create <name> [<slots>]
    OSCdef(\md_chain_create, { |msg, time, addr, recvPort|
        var name, slots;
        name  = msg[1];
        slots = msg[2];
        if(slots.isNil) { slots = 8 } { slots = slots.asInteger.max(1) };
        log.("RECV", "/chain/create name:% slots:% from %".format(name, slots, addr));
        state[\next] = mkChain.(name, slots);
        log.("DO", "next := % (slots: %)".format(name, slots));
        dump.();
        nil
    }, "/chain/create");

    // /chain/add <slot> <processor>  (0-based indexing)
    OSCdef(\md_chain_add, { |msg, time, addr, recvPort|
        var slot, proc, c;
        slot = msg[1].asInteger;
        proc = msg[2].asSymbol;
        log.("RECV", "/chain/add slot:% proc:% from %".format(slot, proc, addr));
        c = state[\next];
        ensureSlots.(c, slot + 1);
        c[\slots][slot] = proc;
        log.("DO", "next[%] := %".format(slot, proc));
        dump.();
        nil
    }, "/chain/add");

    // /chain/remove <slot>
    OSCdef(\md_chain_remove, { |msg, time, addr, recvPort|
        var slot, c;
        slot = msg[1].asInteger;
        log.("RECV", "/chain/remove slot:% from %".format(slot, addr));
        c = state[\next];
        if(slot >= 0 and: { slot < c[\slots].size }) {
            c[\slots][slot] = nil;
            log.("DO", "next[%] cleared".format(slot));
        }{
            log.("WARN", "slot % out of range".format(slot));
        };
        dump.();
        nil
    }, "/chain/remove");

    // /chain/setFrom <startSlot> <p1> <p2> ...
    OSCdef(\md_chain_setFrom, { |msg, time, addr, recvPort|
        var start, names, c;
        start = msg[1].asInteger;
        names = msg.copyRange(2, msg.size - 1).collect(_.asSymbol);
        log.("RECV", "/chain/setFrom start:% list:% from %".format(start, names, addr));
        c = state[\next];
        ensureSlots.(c, start + names.size);
        names.do { |sym, i| c[\slots][start + i] = sym };
        log.("DO", "next from % := %".format(start, names));
        dump.();
        nil
    }, "/chain/setFrom");

    // /chain/next <name>
    OSCdef(\md_chain_next, { |msg, time, addr, recvPort|
        var name;
        name = msg[1].asSymbol;
        log.("RECV", "/chain/next % from %".format(name, addr));
        state[\next][\name] = name;
        log.("DO", "next name := %".format(name));
        dump.();
        nil
    }, "/chain/next");

    // /chain/switchNow
    OSCdef(\md_chain_switchNow, { |msg, time, addr, recvPort|
        var new;
        log.("RECV", "/chain/switchNow from %".format(addr));
        new = state[\next];
        state[\current] = (name: new[\name], slots: new[\slots].copy);
        log.("DO", "current := %".format(new[\name]));
        dump.();
        nil
    }, "/chain/switchNow");

    log.("READY", "Listening on NetAddr.langPort: " ++ NetAddr.langPort);
    dump.();
    nil
};

// boot fresh responders and install
cleanup.();
install.();
)
