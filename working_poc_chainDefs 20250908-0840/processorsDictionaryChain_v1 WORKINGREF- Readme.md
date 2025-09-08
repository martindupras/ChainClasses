This version works. for doing really simple chains using a directory.

The key is that the setFxFilter does this:

- grab \fxSymbol from dictionary
- assign a new filter ("slot") with that function.


(~setFXFilter = { |ndefName, slotIndex, fxSymbol|
    var fxFunc = ~fxDict[fxSymbol];
    if (fxFunc.notNil) {
        Ndef(ndefName)[slotIndex] = \filter -> { |in| fxFunc.(in) };
    } {
        Ndef(ndefName)[slotIndex] = \filter -> { |in| in }; // bypass
    };
};)

The function needs in argument in order to receive from previous slot. Need to think what happens with multichannel and what happens when in and out numb channels don't match.