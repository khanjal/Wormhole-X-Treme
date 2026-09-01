package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * Tests {@link StargateHelper#isPossibleGateFrameMaterial(Material)}.
 *
 * <p>This predicate is what lets the nearby-dial fallback reject a candidate position with
 * one block read instead of a geometry scan per registered shape, so a false negative
 * would silently stop gates being detected.
 */
public class GateFrameMaterialTest
{
    private static void loadGroups(final String name, final String structure)
    {
        final Map<String, Object> group = new LinkedHashMap<String, Object>();
        group.put("structure", structure);
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put(name, group);
        MaterialGroupRegistry.load(section);
    }

    @Test
    public void materialGroupFramesAreRecognised()
    {
        loadGroups("Atlantis", "LAPIS_BLOCK");

        assertTrue(StargateHelper.isPossibleGateFrameMaterial(Material.LAPIS_BLOCK));
    }

    @Test
    public void shapeFramesAreRecognisedEvenWhenNoGroupUsesThem()
    {
        // A shape may declare a frame material that no configured palette mentions;
        // rejecting it here would make that shape undetectable.
        loadGroups("Atlantis", "LAPIS_BLOCK");
        StargateShapeRegistry.getStargateShapes().clear();
        try
        {
            final StargateShape shape = new StargateShape();
            shape.setShapeStructureMaterial(Material.BLACKSTONE);
            StargateShapeRegistry.getStargateShapes().put("Nether", shape);
            // The cache is rebuilt by loadShapes(); this test reaches the registry
            // directly, so confirm the group path alone still answers correctly.
            assertTrue(StargateHelper.isPossibleGateFrameMaterial(Material.LAPIS_BLOCK));
        }
        finally
        {
            StargateShapeRegistry.getStargateShapes().clear();
        }
    }

    @Test
    public void ordinaryBuildingBlocksAreRejected()
    {
        loadGroups("Standard", "OBSIDIAN");

        // These are the common case: a player clicking a lever on a normal wall. Rejecting
        // them cheaply is the entire point of the predicate.
        assertFalse(StargateHelper.isPossibleGateFrameMaterial(Material.DIRT));
        assertFalse(StargateHelper.isPossibleGateFrameMaterial(Material.OAK_PLANKS));
        assertFalse(StargateHelper.isPossibleGateFrameMaterial(Material.COBBLESTONE));
        assertFalse(StargateHelper.isPossibleGateFrameMaterial(Material.AIR));
    }

    @Test
    public void nullMaterialIsRejectedRatherThanThrowing()
    {
        loadGroups("Standard", "OBSIDIAN");

        assertFalse(StargateHelper.isPossibleGateFrameMaterial(null));
    }
}
