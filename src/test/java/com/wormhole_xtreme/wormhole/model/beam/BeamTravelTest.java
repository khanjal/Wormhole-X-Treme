package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigManager;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.plugin.EconomySupport;

/**
 * {@link BeamTravel#resolveCost} -- what a destination actually costs to beam to.
 *
 * <p>Every other cost path in this plugin (a gate's use cost, its build cost) collapses to
 * free rather than showing a charge that never happens whenever economy is not actually
 * active -- {@code ConfigManager.isEconomyEnabled()} false, or no Vault provider attached
 * ({@code EconomySupport.isAvailable()} false). {@code resolveCost} originally skipped that
 * check: a destination with a cost override, or a non-zero global default, would still show
 * "This will cost X..." and "Charged X..." even though {@link EconomySupport#canAfford} and
 * {@link EconomySupport#charge} both fail open in that situation and nothing is actually
 * withdrawn. These tests pin the fix -- economy inactive, in either sense, always means free,
 * regardless of what a destination or the global default says.
 */
class BeamTravelTest
{
    private static BeamDestination destinationWithCost(final Double cost)
    {
        return new BeamDestination("Spawn", "world", 0.0, 64.0, 0.0, 0f, 0f, cost);
    }

    @BeforeEach
    void clearConfig()
    {
        // Real config is a shared static map -- starting from a known-empty state, rather
        // than whatever an earlier test in the same JVM left behind, is what keeps these
        // tests from depending on run order.
        ConfigTestSupport.clear();
        EconomySupport.disableEconomy();
    }

    @AfterEach
    void tidyUp()
    {
        ConfigTestSupport.clear();
        EconomySupport.disableEconomy();
    }

    @Test
    void economyNeverConfiguredMeansFreeEvenWithADestinationOverride()
    {
        // No loadDefaults() call at all: ECONOMY_ENABLED is absent, which
        // ConfigManager.isEconomyEnabled() already treats as false.
        assertEquals(0.0, BeamTravel.resolveCost(destinationWithCost(25.0)), 1e-9);
    }

    @Test
    void economyExplicitlyDisabledMeansFreeEvenWithADestinationOverride()
    {
        ConfigTestSupport.loadDefaults();
        ConfigManager.setConfigValue(ConfigKeys.ECONOMY_ENABLED, false);
        assertEquals(0.0, BeamTravel.resolveCost(destinationWithCost(25.0)), 1e-9);
    }

    @Test
    void economyEnabledInConfigButNoVaultProviderAttachedStillMeansFree()
    {
        // The exact scenario the missing check let through: ECONOMY_ENABLED is true, but
        // EconomySupport.isAvailable() is false (no Vault provider attached, the default
        // and only reachable state in a test JVM) -- canAfford/charge would already fail
        // open here, so the honest cost is zero, not whatever the config says.
        ConfigTestSupport.loadDefaults();
        ConfigManager.setConfigValue(ConfigKeys.ECONOMY_ENABLED, true);
        ConfigManager.setConfigValue(ConfigKeys.BEAM_ECONOMY_USE_COST, 10.0);
        assertFalse(EconomySupport.isAvailable(), "precondition: no Vault provider is attached in this test JVM");

        assertEquals(0.0, BeamTravel.resolveCost(destinationWithCost(null)),
            "global default cost must not leak through with economy inactive");
        assertEquals(0.0, BeamTravel.resolveCost(destinationWithCost(25.0)),
            "a destination's own override must not leak through either");
    }
}
