/*
MDMockRegistry - minimal registry for structural tests
- Records addChain calls
*/


MDMockRegistry : Object {
    var <addCount = 0, <lastChain;

    addChain { |chain|
        lastChain = chain;
        addCount = addCount + 1;
        ^this
    }
}
