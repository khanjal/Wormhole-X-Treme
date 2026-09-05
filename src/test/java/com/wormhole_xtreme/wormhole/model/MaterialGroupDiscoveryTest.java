package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests palette discovery from gate shapes.
 */
class MaterialGroupDiscoveryTest
{
    private static StargateShape shape(final Material frame, final Material iris, final Material light)
    {
        final StargateShape s = new StargateShape();
        s.setShapeStructureMaterial(frame);
        s.setShapeIrisMaterial(iris);
        s.setShapeLightMaterial(light);
        return s;
    }

    @BeforeEach
    void loadStandardOnly()
    {
        final Map<String, Object> standard = new LinkedHashMap<String, Object>();
        standard.put("structure", "OBSIDIAN");
        standard.put("iris", "STONE");
        standard.put("light", "GLOWSTONE");
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Standard", standard);
        MaterialGroupRegistry.load(section);
    }

    @Test
    void aLoneDiamondGateIsOfferedAsAPalette()
    {
        // The motivating case: someone builds a diamond gate with gold chevrons.
        final List<StargateShape> shapes = new ArrayList<StargateShape>();
        shapes.add(shape(Material.DIAMOND_BLOCK, Material.GLASS, Material.GOLD_BLOCK));

        final List<MaterialGroup> found = MaterialGroupRegistry.discoverUndeclaredGroups(shapes);

        assertEquals(1, found.size());
        assertEquals("Diamond", found.get(0).getName());
        assertEquals(Material.DIAMOND_BLOCK, found.get(0).getStructureMaterial());
        assertEquals(Material.GOLD_BLOCK, found.get(0).getLightMaterial());
    }

    @Test
    void framesAlreadyClaimedByAConfiguredGroupAreLeftAlone()
    {
        final List<StargateShape> shapes = new ArrayList<StargateShape>();
        shapes.add(shape(Material.OBSIDIAN, Material.STONE, Material.GLOWSTONE));

        assertTrue(MaterialGroupRegistry.discoverUndeclaredGroups(shapes).isEmpty());
    }

    @Test
    void shapesDisagreeingOnMaterialsYieldNoPalette()
    {
        // This is the shipped situation: every stock shape is framed in obsidian but they
        // ask for three different irises, so no single obsidian palette exists. Guessing
        // one would silently restyle whichever shapes lost the vote.
        final List<StargateShape> shapes = new ArrayList<StargateShape>();
        shapes.add(shape(Material.BLACKSTONE, Material.GLASS, Material.GLOWSTONE));
        shapes.add(shape(Material.BLACKSTONE, Material.BEDROCK, Material.GLOWSTONE));

        assertTrue(MaterialGroupRegistry.discoverUndeclaredGroups(shapes).isEmpty());
    }

    @Test
    void shapesAgreeingOnMaterialsYieldOnePalette()
    {
        // Several shapes can share a palette, as long as they actually agree.
        final List<StargateShape> shapes = new ArrayList<StargateShape>();
        shapes.add(shape(Material.BLACKSTONE, Material.GLASS, Material.SHROOMLIGHT));
        shapes.add(shape(Material.BLACKSTONE, Material.GLASS, Material.SHROOMLIGHT));

        final List<MaterialGroup> found = MaterialGroupRegistry.discoverUndeclaredGroups(shapes);

        assertEquals(1, found.size());
        assertEquals(Material.BLACKSTONE, found.get(0).getStructureMaterial());
    }

    @Test
    void suggestedNamesReadLikeNamesNotEnumConstants()
    {
        assertEquals("Diamond", MaterialGroupRegistry.suggestGroupName(Material.DIAMOND_BLOCK));
        assertEquals("PolishedBlackstone", MaterialGroupRegistry.suggestGroupName(Material.POLISHED_BLACKSTONE));
        assertEquals("Obsidian", MaterialGroupRegistry.suggestGroupName(Material.OBSIDIAN));
    }

    @Test
    void aDiscoveredGroupTakesEffectWithoutARestart()
    {
        final MaterialGroup diamond = new MaterialGroup("Diamond", Material.DIAMOND_BLOCK,
            Material.WATER, Material.GLASS, Material.GOLD_BLOCK, Material.OAK_WALL_SIGN);

        MaterialGroupRegistry.registerDiscoveredGroup(diamond);

        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIAMOND_BLOCK));
        // Registering must not displace the configured default.
        assertEquals("Standard", MaterialGroupRegistry.getDefaultGroup().getName());
    }
}
