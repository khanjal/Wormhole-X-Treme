package com.wormhole_xtreme.wormhole.model.ping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Color;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * The locator bar colour setter is reached by name, so a misspelt name would quietly leave every
 * ping its default colour. Run against each API in CI, this holds the name to the real method:
 * found from 1.21.6, where the API has it, and not before.
 */
class WaypointsTest
{
    @Test
    void theColourSetterIsFoundExactlyWhereTheApiHasIt()
    {
        boolean declared;
        try
        {
            LivingEntity.class.getMethod("setWaypointColor", Color.class);
            declared = true;
        }
        catch (final NoSuchMethodException e)
        {
            declared = false;
        }

        assertEquals(declared, Waypoints.canColour(),
            "the reflective lookup should find setWaypointColor on an API that declares it, and only there");
    }
}
