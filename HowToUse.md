# How to Use — Hands‑On Walkthrough

This guide shows minimal, audible steps you can run **line‑by‑line** (or block‑by‑block) in SuperCollider to:

1. Hear a chain and see it in the UI,
2. Edit the chain in place,
3. Switch between chains,
4. (Optionally) control it with OSC.

> Tip: Set your monitoring level moderately—these examples are meant to be **audible**.

---

## 0) Prep — Boot server and open the status UI

```supercollider
s.waitForBoot({
    ~ui = ChainStatusUI.new("Chain Status v6", 4);  // read-only current/next view
});
```

You should see a window with **Current** and **Next** panels.

---

## 1) Create a chain and make sound

```supercollider
// Create a 6-slot chain named \Demo
c = ChainManager.new(\Demo, 6);

// For now, slot 0 defaults to \testsignal. Let's keep it and add some effects:
c.setSlot(1, \tremolo);   // obvious chopper
c.setSlot(2, \hp);        // gentle high-pass

// Start audio and show it as current
c.play;
~ui.setCurrent(c);

// Inspect the slot spec (what the UI uses)
c.getSpec.postln;
```

You should hear a pulsing test tone. The UI shows **Current** with slot labels: `testsignal`, `tremolo`, `hp`, …

Try toggling a slot:

```supercollider
c.setSlot(1, \bypass);  // turn tremolo off
c.setSlot(1, \tremolo); // and back on
```

---

## 2) Add a more obvious effect (quick chorus)

```supercollider
// Extend this one chain with a chorus filter
c.addProcessor(\chorus, {
    \filter -> { |in, rate=0.2, depth=0.008, mix=0.45|
        var max=0.03, mod=SinOsc.kr(rate).range(0,depth);
        var d = DelayC.ar(in, max, (mod+0.010).clip(0, max));
        XFade2.ar(in, d, mix*2-1)
    }
});

// Swap hp for chorus at slot 2
c.setSlot(2, \chorus);
```

You should hear widening/comb filtering.

---

## 3) Switch between chains using a controller

Create a second chain with slightly different effects:

```supercollider
d = ChainManager.new(\Alt, 6);
d.setSlot(1, \tremolo);
d.setSlot(2, \hp);

// Prepare a controller and point the UI
~ctl = ChainController.new(true);
~ctl.setNext(c);
~ui.setNext(c);

// Switch to c (becomes current)
~ctl.switchNow;
~ui.setCurrent(~ctl.tryPerform(\currentChain));
~ui.setNext(nil);

// Now queue d as next and switch
~ctl.setNext(d);
~ui.setNext(d);
~ctl.switchNow;
~ui.setCurrent(d);
~ui.setNext(nil);
```

You should see **Current** update in the UI for each switch. You’ll hear the new chain after each `switchNow`.

---

## 4) Schedule a future switch (seconds / on beat)

```supercollider
~sch = ChainSwitchScheduler.new(~ctl, true);

// Queue c to come after d, then switch in 4 seconds
~ctl.setNext(c); ~ui.setNext(c);
~sch.switchIn(4);

// Or schedule on the next downbeat:
t = TempoClock.default;
~ctl.setNext(d); ~ui.setNext(d);
~sch.switchOnBeat(t, 1);
```

---

## 5) Use OSC to drive the system (optional)

Create a dispatcher with small handlers to keep UI in sync:

```supercollider
~handlers = Dictionary[
  \new       -> { |nameSym, slots| var ch = ChainManager.new(nameSym, slots ? 8); ~ui.setNext(ch) },
  \setNext   -> { |nameSym| var ch = ChainManager.allInstances.at(nameSym); if(ch.notNil) { ~ctl.setNext(ch); ~ui.setNext(ch) } },
  \add       -> { |slot, proc| var ch = ~ctl.tryPerform(\nextChain); if(ch.notNil) { ch.setSlot(slot, proc); ~ui.setNext(ch) } },
  \remove    -> { |slot|      var ch = ~ctl.tryPerform(\nextChain); if(ch.notNil) { ch.setSlot(slot, \bypass); ~ui.setNext(ch) } },
  \setFrom   -> { |start, list| var ch = ~ctl.tryPerform(\nextChain); if(ch.notNil) { list.do{|p,i| ch.setSlot(start+i, p)}; ~ui.setNext(ch) } },
  \switchNow -> { ~ctl.switchNow; ~ui.setCurrent(~ctl.tryPerform(\currentChain)); ~ui.setNext(nil) }
];

~osc = ChainOSCDispatcher.new("demo", ~ctl, ~handlers, true);
```

Then from SC (or another OSC app), send:

```supercollider
n = NetAddr("127.0.0.1", NetAddr.langPort);

n.sendMsg("/chain/new", "Edit", 6);
n.sendMsg("/chain/add", 1, "tremolo");
n.sendMsg("/chain/add", 2, "hp");
n.sendMsg("/chain/switchNow");

// Create another and switch back
n.sendMsg("/chain/new", "B", 6);
n.sendMsg("/chain/add", 1, "tremolo");
n.sendMsg("/chain/setNext", "B");
n.sendMsg("/chain/switchNow");
```

The UI will show **Next** updates as you edit, and **Current** after the switch.

---

## 6) A simple clean‑up

```supercollider
// Stop audio, free chains, close UI
ChainRegistry.new.freeAll;
{ if(~ui.notNil) { ~ui.free; ~ui = nil } }.defer;
```

> If you created a dispatcher: `~osc.free; ~osc = nil;`.

---

## 7) Extra experiments

- Replace the source at slot 0:

```supercollider
c.addProcessor(\sawpad, {
  {
    var f=110, det=[1,1.005,0.995], w=SinOsc.kr(0.1).range(0.1,0.9);
    var sig = Mix(VarSaw.ar(det*f, 0, w))*0.1 ! 2; Limiter.ar(sig, 0.95)
  }
});

c.setSlot(0, \sawpad);
```

- Apply a sequence of processors in one go:

```supercollider
c.setChainSpec([\sawpad, \tremolo, \hp, \bypass, \bypass, \bypass]);
```

- Inspect all chains:

```supercollider
ChainRegistry.new.listAll;
ChainRegistry.new.describeAll;
```

Enjoy exploring—edit a **Next** chain, listen, then switch when ready.
