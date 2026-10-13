/*
 * Copyright (c) 2026 Ethan
 * SPDX-License-Identifier: MIT
 */

package com.euvcraft.lithmod.core;

import org.slf4j.Logger;

/**
 * Interface for providing {@link FabSettings} to core code.
 * Core code should depend on this interface, not on NeoForge config classes.
 * The implementation lives in the mc package and binds to NeoForge's server config.
 */
public interface FabSettingsProvider {

    /**
     * Returns the current fab settings.
     * Must only be called on the server thread.
     */
    FabSettings getSettings();

    /**
     * Called when the config is reloaded (server-side only).
     * The implementation should fetch the new settings and notify listeners.
     */
    void onConfigReload();

    /**
     * Registers a listener to be notified when settings change.
     * The listener is called on the server thread.
     */
    void addSettingsChangeListener(Runnable listener);

    /**
     * Removes a previously registered listener.
     */
    void removeSettingsChangeListener(Runnable listener);

    /**
     * Logs a warning message. Used by FabSettings for validation warnings.
     * The implementation should use the mod's logger.
     */
    static void logWarning(String message) {
        // Default implementation - the mc package implementation will override this
        // by setting a logger via setLogger()
        Logger logger = LOGGER_HOLDER.get();
        if (logger != null) {
            logger.warn("[EUVCRAFT] {}", message);
        } else {
            System.err.println("[EUVCRAFT] " + message);
        }
    }

    /**
     * Sets the logger to use for warnings. Called by the mc package implementation on startup.
     */
    static void setLogger(Logger logger) {
        LOGGER_HOLDER.set(logger);
    }

    // Package-private holder for the logger
    java.lang.ThreadLocal<Logger> LOGGER_HOLDER = new java.lang.ThreadLocal<>();
}