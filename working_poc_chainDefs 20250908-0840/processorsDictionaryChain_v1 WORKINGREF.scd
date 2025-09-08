// processorsDictionaryChain_v1.scd
// v1 working
// MD 20250909-0833


// 1. Define FX dictionary
// next steps: handle with a class
(~fxDict = IdentityDictionary[
    \phaser -> { |in| Phaser.ar(in, 0.3, 0.8, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5) },
    \flanger -> { |in| CombN.ar(in, 0.03, 0.03, 2) },
    \tremolo -> { |in| in * SinOsc.kr(5).range(0.5, 1.0) },
    \delay -> { |in| DelayN.ar(in, 0.5, 0.25) }
];)

// 2. Set up Ndef slots
// next step: make a class to handle. Create, name, keep track, spec, insert, remove, swap
Ndef(\chain1)[0] = { PinkNoise.ar(0.2) }; // input
Ndef(\chain1)[1] = \filter -> { |in| in }; // FX slot 1
Ndef(\chain1)[2] = \filter -> { |in| in }; // FX slot 2
Ndef(\chain1)[3] = \filter -> { |in| in }; // FX slot 3

// 3. Play the final slot
Ndef(\chain1).play; // play the output of the last slot

// 4. Helper to insert FX into a filter slot
// NOW: should this be handled with a class? What would the class do?
(~setFXFilter = { |ndefName, slotIndex, fxSymbol|
    var fxFunc = ~fxDict[fxSymbol];
    if (fxFunc.notNil) {
        Ndef(ndefName)[slotIndex] = \filter -> { |in| fxFunc.(in) };
    } {
        Ndef(ndefName)[slotIndex] = \filter -> { |in| in }; // bypass
    };
};)






// 5. Insert effects dynamically
// Next steps: have a class that handles this. OSCCommandController, something like that?
~setFXFilter.(\chain1, 1, \flanger);
~setFXFilter.(\chain1, 2, \phaser);
~setFXFilter.(\chain1, 3, \tremolo);

// Change effects on the fly
~setFXFilter.(\chain1, 2, \delay);


/* More things are needed:
- named controls fx
- sensible defaults
- prototype for multi-channel version of the effects

