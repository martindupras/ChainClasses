// ProcessorLibrary.sc
// v0.1
// MD 20250905:1345

ProcessorLibrary : Object {
    classvar < version = "v0.1";
	classvar < global;
    var verbose = true;
    var processors;

    *new { |verbose = true|
        var instance;
        instance = super.new.init(verbose);
        ^instance
    }

	*initClass {
    global = ProcessorLibrary.new(true);
	}

    init { |v|
        verbose = v;
        processors = IdentityDictionary.new;
        if (verbose) {
            ("[ProcessorLibrary] Initialized (% version)").format(version).postln;
        };
        this.defineDefaults;
        ^this
    }

    defineDefaults {
        this.add(\hp, {
            \filter -> { |in, freq = 300|
                var cut;
                cut = freq.clip(10, 20000);
                HPF.ar(in, cut)
            }
        });

        this.add(\lp, {
            \filter -> { |in, freq = 2000|
                var cut;
                cut = freq.clip(50, 20000);
                LPF.ar(in, cut)
            }
        });

        this.add(\tremolo, {
            \filter -> { |in, rate = 12, depth = 1.0, duty = 0.5|
                var chop, amount, dutyClamped;
                dutyClamped = duty.clip(0.05, 0.95);
                amount = depth.clip(0, 1);
                chop = LFPulse.kr(rate.max(0.1), 0, dutyClamped).lag(0.001);
                in * (chop * amount + (1 - amount))
            }
        });

        this.add(\bypass, { \filter -> { |in| in } });

        this.add(\testsignal, {
            {
                var trig, env, freqs, tone, pan, amp;
                trig = Impulse.kr(2);
                env = Decay2.kr(trig, 0.01, 0.15);
                freqs = [220, 330];
                tone = SinOsc.ar(freqs, 0, 0.15).sum;
                amp = env * 0.9;
                pan = [-0.2, 0.2];
                [tone * amp, tone * amp] * (1 + pan)
            }
        });
    }

    add { |key, func|
        processors.put(key.asSymbol, func);
        if (verbose) {
            ("[ProcessorLibrary] Added processor: %".format(key)).postln;
        };
        ^this
    }

    get { |key|
        ^processors.at(key.asSymbol);
    }

    list {
        ^processors.keys.asArray.sort;
    }

    describe {
        ("[ProcessorLibrary] Available processors: %".format(this.list)).postln;
        ^this
    }
}
