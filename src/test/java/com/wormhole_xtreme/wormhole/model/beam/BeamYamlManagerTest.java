package com.wormhole_xtreme.wormhole.model.beam;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * A destination's cost must round-trip as null, not silently become free.
 *
 * <p>{@code Double} was chosen over a primitive {@code double} specifically so a destination
 * with no cost override could be told apart from one explicitly set to zero -- null inherits
 * whatever {@code BEAM_ECONOMY_USE_COST} currently says, zero is a permanent "this one is
 * free" that a later change to the global default cannot override. Reading an absent YAML
 * field as {@code 0.0} instead of {@code null} would collapse that distinction and make every
 * destination written before this field existed, and every place (which never gets one set on
 * it at all), quietly free forever regardless of the configured default.
 */
class BeamYamlManagerTest
{
    @Test
    void aDestinationWithNoCostFieldReadsBackAsNullNotZero()
    {
        final Map<String, Object> stored = BeamYamlManager.writeDestination(
            new BeamDestination("spawn", "world", 1.0, 2.0, 3.0, 0.0f, 0.0f, null));

        assertFalse(stored.containsKey("Cost"), "no override means nothing should be written at all");

        final BeamDestination read = BeamYamlManager.readDestination("spawn", stored);
        assertNull(read.getCost(), "an absent field must load as null (inherit), not 0.0 (free)");
    }

    @Test
    void aDestinationExplicitlySetFreeRoundTripsAsZeroNotNull()
    {
        final Map<String, Object> stored = BeamYamlManager.writeDestination(
            new BeamDestination("market", "world", 1.0, 2.0, 3.0, 0.0f, 0.0f, 0.0));

        assertEquals(0.0, stored.get("Cost"), "an explicit free override is written, not omitted");

        final BeamDestination read = BeamYamlManager.readDestination("market", stored);
        assertEquals(0.0, read.getCost(), "0.0 must come back as 0.0, not be mistaken for absent");
    }

    @Test
    void aDestinationWithAPositiveCostRoundTrips()
    {
        final Map<String, Object> stored = BeamYamlManager.writeDestination(
            new BeamDestination("arena", "world", 1.0, 2.0, 3.0, 0.0f, 0.0f, 25.0));

        final BeamDestination read = BeamYamlManager.readDestination("arena", stored);
        assertEquals(25.0, read.getCost());
    }

    @Test
    void aMalformedCostFieldIsIgnoredRatherThanFailingTheWholeEntry()
    {
        final Map<String, Object> stored = BeamYamlManager.writeDestination(
            new BeamDestination("spawn", "world", 1.0, 2.0, 3.0, 0.0f, 0.0f, 10.0));
        stored.put("Cost", "not a number");

        final BeamDestination read = BeamYamlManager.readDestination("spawn", stored);

        assertNotNull(read, "the rest of a valid entry should not be thrown away over one bad field");
        assertNull(read.getCost(), "a Cost that is not a number falls back to inherit, same as absent");
    }
}
