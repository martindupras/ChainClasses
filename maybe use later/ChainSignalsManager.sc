// ChainSignalsManager.sc
// v0.1
// MD 20250905:1245
ChainSignalsManager : Object {
	classvar < version = "v0.1";
	var verbose = true;
	var inputChannels = 6;
	var outputMode = \stereo; // could be \mono, \stereo, \sixout

	*new { |verbose = true|
		var instance;
		instance = super.new.init(verbose);
		^instance
	}

	init { |v|
		var modes;
		verbose = v;
		if (verbose) {
			("[ChainSignalsManager] Initialized (% version)").format(version).postln;
		};
		modes = [\mono, \stereo, \sixout];
		if (modes.includes(outputMode).not) {
			outputMode = \stereo;
		};
		^this
	}

	setOutputMode { |modeSymbol|
		var mode;
		mode = modeSymbol.asSymbol;
		if ([\mono, \stereo, \sixout].includes(mode)) {
			outputMode = mode;
			("[ChainSignalsManager] Output mode set to %.".format(mode)).postln;
		} {
			("[ChainSignalsManager] Invalid output mode: %.".format(mode)).warn;
		};
		^this
	}

	describeRouting {
		("[ChainSignalsManager] Routing: % in → % out (% mode)"
			.format(inputChannels, this.getOutputChannels, outputMode)).postln;
		^this
	}

	getOutputChannels {
		if (outputMode == \mono) { ^1 };
		if (outputMode == \stereo) { ^2 };
		if (outputMode == \sixout) { ^6 };
		^2; // fallback
	}
}
