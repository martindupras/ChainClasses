// testChainManager.scd
// MD 20250904-1713

(
// Boot and test
s.waitForBoot {
    ~cm;

    // Instantiate (auto-unique Ndef name)
    ~cm = ChainManager.new(nil, 8);  // or ChainManager.new(\myChain, 8)
    ~cm.play;

    // Inspect
    ~cm.status;  // [ \testsignal, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass ]

    // Add processors in the middle
    ~cm.setSlot(3, \hp);
    ~cm.setSlot(4, \lp);
    ~cm.setSlot(5, \tremolo);
    ~cm.status;

    // Parameter tweaks (generic passthrough)
    // (affects whichever slot currently exposes these names)
    Ndef(~cm.getName).set(\freq, 600);      // LP/HP freq if present
    Ndef(~cm.getName).set(\rate, 14, \depth, 1.0); // tremolo params

    // Replace the whole spec (slot 0 coerced to \testsignal if not a generator)
    ~cm.setChainSpec([\testsignal, \hp, \tremolo, \lp, \bypass, \bypass, \bypass, \bypass]);

    // Stop when done
    // ~cm.stop;
};
)


~cm.setSlot(3, \hp);
    ~cm.setSlot(4, \lp);
    ~cm.setSlot(5, \tremolo);
    ~cm.status;
~cm.setChainSpec([ \testsignal, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass ]);
~cm.setChainSpec([ \testsignal, \tremolo, \hp, \bypass, \bypass, \bypass, \bypass, \bypass ]);
~cm.setChainSpec([ \testsignal, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass, \bypass ]);