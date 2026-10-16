package com.euvcraft.lithmod.core;

import java.util.List;

/**
 * The outcome of one {@link ProcessRun#tick(ProcessEnvironment)} call.
 */
public record ProcessTickResult(ProcessState state, List<ProcessRunEvent> events) {

    public ProcessTickResult {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (events == null) {
            throw new IllegalArgumentException("events must not be null");
        }
        events = List.copyOf(events);
    }
}
