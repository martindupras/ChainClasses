// File: tests/readyandlistening_v1.0.scd
// MD 2025-09-05
(
// Audio-free demo: named OSCdefs, conservative syntax, explicit install checks.
// NOTE: We DO NOT call OSCFunc.removeAll (it doesn't exist). We only free our own OSCdefs.

var verbose, log, mkChain, ensureSlots, prettySlots, dump, state, freeIfExists, installedOK;

// --- config ---
verbose = true;

// --- helpers ---
log = { |label, msg|
    if(verbose) { ("[demo] " ++ label ++ ": " ++ msg.asString).postln };
    nil
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

freeIfExists = { |defName|
    var d;
    d = OSCdef(defName);
    if(d.notNil) { d.free };
    nil
};

// --- in-memory state (no audio) ---
state = (
    current: mkChain.(\INIT, 8),
    next: mkChain.(\A, 8)
);

// --- clean only our responders ---
[
    \md_demo_ping,
    \md_chain_create,
    \md_chain_add,
    \md_chain_remove,
    \md_chain_setFrom,
    \md_chain_next,
    \md_chain_switchNow
].do { |sym| freeIfExists.(sym) };

// --- install routes ---
OSCdef(\md_demo_ping, { |msg, time, addr, recvPort|
    var args;
    args = if(msg.size > 1) { msg.copyRange(1, msg.size - 1) } { [] };
    log.("RECV", "/demo/ping " ++ args.asString ++ " from " ++ addr.asString);
    log.("DO", "pong");
    nil
}, "/demo/ping");
"installed: /demo/ping".postln;

OSCdef(\md_chain_create, { |msg, time, addr, recvPort|
    var name, slots;
    name  = msg[1];
    slots = msg[2];
    if(slots.isNil) { slots = 8 } { slots = slots.asInteger.max(1) };
    log.("RECV", "/chain/create name:" ++ name.asString ++ " slots:" ++ slots.asString ++ " from " ++ addr.asString);
    state[\next] = mkChain.(name, slots);
    log.("DO", "next := " ++ name.asString ++ " (slots: " ++ slots.asString ++ ")");
    dump.();
    nil
}, "/chain/create");
"installed: /chain/create".postln;

OSCdef(\md_chain_add, { |msg, time, addr, recvPort|
    var slot, proc, c;
    slot = msg[1].asInteger;
    proc = msg[2].asSymbol;
    log.("RECV", "/chain/add slot:" ++ slot.asString ++ " proc:" ++ proc.asString ++ " from " ++ addr.asString);
    c = state[\next];
    ensureSlots.(c, slot + 1);
    c[\slots][slot] = proc;
    log.("DO", "next[" ++ slot.asString ++ "] := " ++ proc.asString);
    dump.();
    nil
}, "/chain/add");
"installed: /chain/add".postln;

OSCdef(\md_chain_remove, { |msg, time, addr, recvPort|
    var slot, c;
    slot = msg[1].asInteger;
    log.("RECV", "/chain/remove slot:" ++ slot.asString ++ " from " ++ addr.asString);
    c = state[\next];
    if((slot >= 0) and: { slot < c[\slots].size }) {
        c[\slots][slot] = nil;
        log.("DO", "next[" ++ slot.asString ++ "] cleared");
    }{
        log.("WARN", "slot " ++ slot.asString ++ " out of range");
    };
    dump.();
    nil
}, "/chain/remove");
"installed: /chain/remove".postln;

OSCdef(\md_chain_setFrom, { |msg, time, addr, recvPort|
    var start, names, c, i;
    start = msg[1].asInteger;
    names = msg.copyRange(2, msg.size - 1).collect({ |x| x.asSymbol });
    log.("RECV", "/chain/setFrom start:" ++ start.asString ++ " list:" ++ names.asString ++ " from " ++ addr.asString);
    c = state[\next];
    ensureSlots.(c, start + names.size);
    i = 0;
    while { i < names.size } {
        c[\slots][start + i] = names[i];
        i = i + 1;
    };
    log.("DO", "next from " ++ start.asString ++ " := " ++ names.asString);
    dump.();
    nil
}, "/chain/setFrom");
"installed: /chain/setFrom".postln;

OSCdef(\md_chain_next, { |msg, time, addr, recvPort|
    var name;
    name = msg[1].asSymbol;
    log.("RECV", "/chain/next " ++ name.asString ++ " from " ++ addr.asString);
    state[\next][\name] = name;
    log.("DO", "next name := " ++ name.asString);
    dump.();
    nil
}, "/chain/next");
"installed: /chain/next".postln;

OSCdef(\md_chain_switchNow, { |msg, time, addr, recvPort|
    var newNext;
    log.("RECV", "/chain/switchNow from " ++ addr.asString);
    newNext = state[\next];
    state[\current] = (name: newNext[\name], slots: newNext[\slots].copy);
    log.("DO", "current := " ++ newNext[\name].asString);
    dump.();
    nil
}, "/chain/switchNow");
"installed: /chain/switchNow".postln;

// --- verify installs explicitly ---
installedOK = [
    \md_demo_ping,
    \md_chain_create,
    \md_chain_add,
    \md_chain_remove,
    \md_chain_setFrom,
    \md_chain_next,
    \md_chain_switchNow
].collect { |sym| [sym, OSCdef(sym).notNil] };

("READY: receivers installed on langPort " ++ NetAddr.langPort).postln;
("INSTALL CHECK: " ++ installedOK.asString).postln;
dump.();
)
