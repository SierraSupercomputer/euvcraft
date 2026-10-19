package com.euvcraft.lithmod.mc;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;

/**
 * Gate for test-only content (fixtures) so it can be disabled for shipping builds
 * and production environments.
 *
 * <p>The fixture gate uses BOTH an explicit JVM boolean property AND
 * {@link FMLEnvironment#isProduction()} so that production can never enable
 * fixtures even if the property is set to true.</p>
 *
 * <p>The property is read once on first access and cached. An early return
 * when the property is false prevents unit JVM dependency initialization.</p>
 */
public final class GameTestFixtures {

    /** JVM property name to enable fixtures (default: false). */
    private static final String FIXTURES_PROPERTY = "lithmodFixtures";

    /** JVM property name to enable the shipping probe (default: false). */
    private static final String SHIPPING_PROBE_PROPERTY = "lithmodShippingProbe";

    /** Cached fixture enabled state. */
    private static Boolean fixturesEnabled;

    /** Cached shipping probe enabled state. */
    private static Boolean shippingProbeEnabled;

    private GameTestFixtures() {
    }

    /**
     * Whether test fixtures are enabled.
     *
     * <p>Returns true only when BOTH:</p>
     * <ul>
     *   <li>The {@code lithmodFixtures} JVM property is explicitly {@code true}</li>
     *   <li>{@link FMLEnvironment#isProduction()} returns {@code false}</li>
     * </ul>
     *
     * <p>Production environments (where {@code FMLEnvironment.isProduction()} is true)
     * can never enable fixtures, even if the property is set.</p>
     *
     * @return true if fixtures should be registered and active
     */
    public static boolean enabled() {
        if (fixturesEnabled != null) {
            return fixturesEnabled;
        }
        String prop = System.getProperty(FIXTURES_PROPERTY);
        if (prop == null || !Boolean.parseBoolean(prop)) {
            fixturesEnabled = false;
            return false;
        }
        // Guard FMLLoader.getCurrentOrNull if outside-loader calls require it;
        // property false early return prevents unit JVM dependency init.
        // Installed loader 12.0.10 has FMLEnvironment.isProduction() METHOD, not .production field.
        boolean isProd = FMLEnvironment.isProduction();
        fixturesEnabled = !isProd;
        return fixturesEnabled;
    }

    /**
     * Whether the shipping probe is enabled.
     *
     * <p>Only the GameTestServer Gradle run sets this via
     * {@code -PlithmodShippingProbe=true}. Default is false.
     * Production must not register the probe even if the property is set.</p>
     *
     * @return true if the shipping probe should be registered
     */
    public static boolean shippingProbeEnabled() {
        if (shippingProbeEnabled != null) {
            return shippingProbeEnabled;
        }
        String prop = System.getProperty(SHIPPING_PROBE_PROPERTY);
        if (prop == null || !Boolean.parseBoolean(prop)) {
            shippingProbeEnabled = false;
            return false;
        }
        // Production must not register probe even if property is true
        boolean isProd = FMLEnvironment.isProduction();
        shippingProbeEnabled = !isProd;
        return shippingProbeEnabled;
    }

    /**
     * Whether the current context is a normal fixture run (fixtures on, shipping probe off).
     *
     * @return true for normal gameTestServer runs with fixtures enabled
     */
    public static boolean normalFixtureRun() {
        return enabled() && !shippingProbeEnabled();
    }

    /**
     * Whether the current context is a shipping probe run (fixtures off, shipping probe on).
     *
     * @return true for gameTestServer runs with -PlithmodShippingProbe=true and fixtures off
     */
    public static boolean shippingProbeRun() {
        return !enabled() && shippingProbeEnabled();
    }
}