// test_readyandlistening_sendlines_v0.9.scd
// MD 2025-09-05
(
// Sender helpers — IMPORTANT: use ~toLang *inside* ~send (with tilde).

~toLang = NetAddr("127.0.0.1", NetAddr.langPort); nil;  // add ; nil if you run just this line
~send   = { |path ...args| ~toLang.sendMsg(path, *args); nil }; nil;

// Sanity: ping (expect [demo] RECV/DO "pong")
~send.("/demo/ping", 1, 2, 3);

// 1) Create a new chain called 'B' with 8 slots
~send.("/chain/create", "B", 8);

// 2) Put a delay in slot 2  (0-based indexing ⇒ third slot)
~send.("/chain/add", 2, "delay");

// 3) Remove whatever is in slot 2
~send.("/chain/remove", 2);

// 4) Put [\tremolo, \phaser, \reverb] starting at slot 3
~send.("/chain/setFrom", 3, "tremolo", "phaser", "reverb");

// 5) Mark next chain label
~send.("/chain/next", "B");

// 6) Switch from current → next immediately
~send.("/chain/switchNow");

nil // evaluating the whole block prints a single -> nil
)
