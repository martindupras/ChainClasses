/*
MDMockController - minimal controller for structural tests
- Holds currentChain / nextChain
- Implements switchNow and setNext
*/
MDMockController : Object {
    var <currentChain, <nextChain, <>didSwitch;  // didSwitch is now read/write

    *new { |currentChain = nil, nextChain = nil|
        ^super.new.init(currentChain, nextChain)
    }

    init { |cur, nxt|
        currentChain = cur;
        nextChain = nxt;
        didSwitch = false;  // default
        ^this
    }

    switchNow {
        var tmp;
        tmp = currentChain;
        currentChain = nextChain;
        nextChain = tmp;
        didSwitch = true;
        ^this
    }

    setNext { |chain|
        nextChain = chain;
        ^this
    }
}


// //MDMockController.sc
//
//
// /*
// MDMockController - minimal controller for structural tests
// - Holds currentChain / nextChain
// - Implements switchNow and setNext as your classes expect
// */
// MDMockController : Object {
// 	var <currentChain, <nextChain, <didSwitch = false;
//
// 	*new { |currentChain = nil, nextChain = nil|
// 		^super.new.init(currentChain, nextChain)
// 	}
//
// 	init { |cur, nxt|
// 		currentChain = cur;
// 		nextChain = nxt;
// 		^this
// 	}
//
// 	switchNow {
// 		var tmp;
// 		tmp = currentChain;
// 		currentChain = nextChain;
// 		nextChain = tmp;
// 		didSwitch = true;
// 		^this
// 	}
//
// 	setNext { |chain|
// 		nextChain = chain;
// 		^this
// 	}
// }
