// ChainRegistry.sc
// v0.1
// MD 20250905:1230

ChainRegistry : Object {
	classvar < version = "v0.1";
	var verbose = true;

	*new { |verbose = true|
		var instance;
		instance = super.new.init(verbose);
		^instance
	}

	init { |v|
		var keys;
		verbose = v;
		if (verbose) {
			("[ChainRegistry] Initialized (% version)").format(version).postln;
		};
		^this
	}

	// ChainRegistry.sc
	listAll {
		var keys;
		keys = ChainManager.allInstances.keys.asArray.sort;   // <- deterministic, sorted
		("[ChainRegistry] Chains: %".format(keys)).postln;
		^keys
	}


	describeAll {
		var dict;
		dict = ChainManager.allInstances;
		dict.keysValuesDo { |key, chain|
			chain.status;
		};
		^this
	}

	freeAll {
		ChainManager.freeAll;
		"[ChainRegistry] All chains freed.".postln;
		^this
	}
}
