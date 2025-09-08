//ChainStarterTest.scd
// MD 20250908-1338


// Create a chain named "Edit" with 6 slots
~send.("/chain/new", ["Edit", 6]);

// Add processors (0-based: slot 0 = \testsignal source)
~send.("/chain/add", [1, "tremolo"]);
~send.("/chain/add", [2, "hp"]);

// Make "Edit" the next chain, then switch to it
~send.("/chain/setNext", ["Edit"]);
~send.("/chain/switchNow");
