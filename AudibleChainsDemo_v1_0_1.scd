/*  AudibleChainsDemo_v1_0_1.scd
    Purpose: Obvious-audio demo for ChainManager + ChainStatusUI with sustained source and bold FX.
    Requires:
      - ChainManager v2.2
      - ChainController v0.2
      - ChainStatusUI v0.3.2+ (patched to use List, etc.)

    Controls:
      - Evaluate entire file once
      - ~diagnose.()   -> prints what's playing
      - ~stopPlay.()   -> stops sound, leaves UI
      - ~cleanup.()    -> stops sound, frees chains, closes UI
*/

(
// Comfortable server settings (optional)
s.options.numBuffers = s.options.numBuffers.max(2048);
s.options.memSize    = s.options.memSize.max(2.pow(20));  // 1M frames
s.latency = 0.02;

s.waitForBoot({
    var installTime;
    // --- helper post
    ~post = { arg m; ("[Demo] " ++ m.asString).postln; };

    // --- one UI instance
    if(~statusUI.notNil) { { ~statusUI.free }.defer; ~statusUI = nil; };
    ~statusUI = ChainStatusUI.new("Chain Status v6", 4, false);

    // --- controller
    ~controller = ChainController.new(true);

    // --- per-chain FX installer (adds processors to that ChainManager instance)
    ~installExtraFx = { arg cm;
        // Sustained detuned saw pad (slot 0)
        cm.addProcessor(\sawpad, {
            {
                var base, det, freqs, lfoW, sig, spread, left, right;
                base  = 110; // A2
                det   = [1.0, 1.005, 0.995, 1.010, 0.990];
                freqs = det * base;
                lfoW  = SinOsc.kr(0.08).range(0.1, 0.9);
                sig   = Mix.fill(freqs.size, { arg i; VarSaw.ar(freqs[i], 0, lfoW) }) * 0.09;
                spread = [-0.3, 0.3];
                left  = sig * (1 + spread[0]);
                right = sig * (1 + spread[1]);
                Limiter.ar([left, right], 0.95)
            }
        });

        // Chorus: 3 short, modulated delays mixed with dry (clear widening)
        cm.addProcessor(\chorus, {
            \filter -> { arg in, rate=0.22, depth=0.008, mix=0.42;
                var maxDel, mod1, mod2, mod3, d1, d2, d3, wet;
                maxDel = 0.03; // 30 ms
                mod1 = SinOsc.kr(rate         ).range(0.0, depth);
                mod2 = SinOsc.kr(rate * 1.333).range(0.0, depth);
                mod3 = SinOsc.kr(rate * 0.777).range(0.0, depth);
                d1 = DelayC.ar(in, maxDel, (mod1 + 0.007).clip(0.0, maxDel));
                d2 = DelayC.ar(in, maxDel, (mod2 + 0.011).clip(0.0, maxDel));
                d3 = DelayC.ar(in, maxDel, (mod3 + 0.015).clip(0.0, maxDel));
                wet = (d1 + d2 + d3) / 3;
                XFade2.ar(in, wet, (mix.clip(0,1) * 2 - 1))
            }
        });

        // Flanger: very short moving delay (fix: avoid -depth in .range; use base + SinOsc*depth)
        cm.addProcessor(\flanger, {
            \filter -> { arg in, rate=0.18, base=0.003, depth=0.002, decay=0.25, mix=0.55;
                var maxDel, dtime, wet;
                maxDel = 0.02;
                // was: .range(-depth, depth) -> parser can choke on -depth; use mul/add mapping
                dtime = (base + (SinOsc.kr(rate) * depth)).clip(0.0001, maxDel);
                wet   = AllpassC.ar(in, maxDel, dtime, decay.clip(0.05, 1.0));
                XFade2.ar(in, wet, (mix.clip(0,1) * 2 - 1))
            }
        });

        // Wah: sweep RLPF center freq (slow, obvious vowel sweep)
        cm.addProcessor(\wah, {
            \filter -> { arg in, rate=0.12, fmin=300, fmax=2500, rq=0.2, mix=0.75;
                var cf, wet;
                cf  = LinExp.kr(SinOsc.kr(rate), -1, 1, fmin.max(40), fmax.min(12000));
                wet = RLPF.ar(in, cf, rq.clip(0.05, 0.9));
                XFade2.ar(in, wet, (mix.clip(0,1) * 2 - 1))
            }
        });

        // Delay: audible echoes (CombN)
        cm.addProcessor(\delay, {
            \filter -> { arg in, time=0.45, decay=3.2, mix=0.40;
                var maxDel, wet;
                maxDel = 2.0;
                wet = CombN.ar(in, maxDel, time.clip(0.05, 1.2), decay.clip(0.2, 8.0));
                XFade2.ar(in, wet, (mix.clip(0,1) * 2 - 1))
            }
        });

        // Reverb: FreeVerb2 stereo (audible tail)
        cm.addProcessor(\reverb, {
            \filter -> { arg in, mix=0.33, room=0.75, damp=0.45;
                var l, r, wl, wr;
                l = in[0]; r = in[1];
                # wl, wr = FreeVerb2.ar(l, r, mix.clip(0,1), room.clip(0,1), damp.clip(0,1));
                [wl, wr]
            }
        });

        // Distortion: tanh soft clip with makeup and mix
        cm.addProcessor(\dist, {
            \filter -> { arg in, drive=2.8, makeup=0.9, mix=0.55;
                var driven, wet;
                driven = in * drive.clip(0.5, 8.0);
                wet = (tanh(driven) / tanh(drive)) * makeup;
                XFade2.ar(in, wet, (mix.clip(0,1) * 2 - 1))
            }
        });
    };

    // --- build 3 chains with bold FX
    ~makeChain = { arg nameSym;
        var c;
        c = ChainManager.new(nameSym, 8);
        ~installExtraFx.(c);
        c.setSlot(0, \sawpad);  // sustained source
        c
    };

    ~a = ~makeChain.(\A);
    // A: wide lush pad (chorus + delay + reverb)
    ~a.setSlot(1, \chorus);
    ~a.setSlot(2, \delay);
    ~a.setSlot(3, \reverb);

    ~b = ~makeChain.(\B);
    // B: motion & vowel (flanger + wah + reverb)
    ~b.setSlot(1, \flanger);
    ~b.setSlot(2, \wah);
    ~b.setSlot(3, \reverb);

    ~c = ~makeChain.(\C);
    // C: thicker & forward (chorus + dist + delay)
    ~c.setSlot(1, \chorus);
    ~c.setSlot(2, \dist);
    ~c.setSlot(3, \delay);

    // --- wire controller + UI
    ~controller.setCurrent(~a);
    ~statusUI.setCurrent(~a);
    ~statusUI.setNext(nil);

    // --- play timeline (A -> B -> C -> A)
    ~playRoutine = Routine({
        ~post.("Starting: A -> B -> C -> A");

        ~post.("Play A (chorus + delay + reverb)");
        ~controller.setNext(~a); ~statusUI.setNext(~a);
        0.3.wait;
        ~controller.switchNow;   { ~statusUI.setCurrent(~a); ~statusUI.setNext(nil) }.defer;

        10.wait;

        ~post.("Switch to B (flanger + wah + reverb)");
        ~controller.setNext(~b); ~statusUI.setNext(~b);
        0.3.wait;
        ~controller.switchNow;   { ~statusUI.setCurrent(~b); ~statusUI.setNext(nil) }.defer;

        10.wait;

        ~post.("Switch to C (chorus + dist + delay)");
        ~controller.setNext(~c); ~statusUI.setNext(~c);
        0.3.wait;
        ~controller.switchNow;   { ~statusUI.setCurrent(~c); ~statusUI.setNext(nil) }.defer;

        10.wait;

        ~post.("Back to A");
        ~controller.setNext(~a); ~statusUI.setNext(~a);
        0.3.wait;
        ~controller.switchNow;   { ~statusUI.setCurrent(~a); ~statusUI.setNext(nil) }.defer;

        ~post.("Timeline finished (last chain keeps playing).");
    }).play(SystemClock);

    // --- helpers
    ~diagnose = {
        ~post.("Controller status:");
        ~controller.status;
        ChainManager.allInstances.values.do({ arg ch; [ch.getName, ch.isPlaying].postln });
    };

    ~stopPlay = {
        ChainManager.allInstances.values.do({ arg ch; ch.stop });
        ~post.("All chains stopped (UI remains).");
    };

    ~cleanup = {
        if(~playRoutine.notNil) { ~playRoutine.stop; ~playRoutine = nil; };
        ChainManager.freeAll;
        if(~statusUI.notNil) { { ~statusUI.free }.defer; ~statusUI = nil; };
        ~post.("Cleanup complete.");
    };

    ~statusUI.front;
    installTime = Date.localtime;
    ~post.("Demo ready at " ++ installTime.asString ++ ". Use ~diagnose.() / ~stopPlay.() / ~cleanup.().");
});
)
