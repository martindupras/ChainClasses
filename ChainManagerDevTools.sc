// Filename: ChainManagerDevTools.sc
//    taken out of the main class definition because they are not used right now.

// Purpose: Optional teardown helpers for advanced scheduling.
// Note: These are not required for normal live use.

+ ChainManager {
    editAndFreeBundled { |editFunction, delta = 0.05|
        var server, previousLatency, previousFadeTime;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        previousFadeTime = Ndef(name).fadeTime;
        server.latency = 0.0;
        Ndef(name).fadeTime = 0.0;
        server.makeBundle(delta, {
            editFunction.value;
            Ndef(name).clear;
        });
        Ndef(name).fadeTime = previousFadeTime;
        server.latency = previousLatency;
        registry.removeAt(name);
        isPlaying = false;
        ^this
    }

    endThenClear { |waitSeconds = nil|
        var server, previousLatency, previousFadeTime, waitTime;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        previousFadeTime = Ndef(name).fadeTime;
        waitTime = if(waitSeconds.isNil) { (previousLatency + 0.05).max(0.05) } { waitSeconds.max(0.01) };
        server.makeBundle(0.0, {
            Ndef(name).fadeTime = 0.0;
            Ndef(name).stop;
        });
        server.makeBundle(waitTime, { Ndef(name).clear; });
        Ndef(name).fadeTime = previousFadeTime;
        AppClock.sched(waitTime, {
            registry.removeAt(name);
            isPlaying = false;
            ^nil
        });
        ^this
    }

    editEndThenClearBundled { |editFunction, waitSeconds = nil|
        var server, previousLatency, previousFadeTime, waitTime;
        server = Ndef(name).server ? Server.default;
        previousLatency = server.latency;
        previousFadeTime = Ndef(name).fadeTime;
        waitTime = if(waitSeconds.isNil) { (previousLatency + 0.05).max(0.05) } { waitSeconds.max(0.01) };
        server.makeBundle(0.0, {
            Ndef(name).fadeTime = 0.0;
            editFunction.value;
            Ndef(name).stop;
        });
        server.makeBundle(waitTime, { Ndef(name).clear; });
        Ndef(name).fadeTime = previousFadeTime;
        AppClock.sched(waitTime, {
            registry.removeAt(name);
            isPlaying = false;
            ^nil
        });
        ^this
    }
}
