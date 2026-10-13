package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests the material resolution order on {@link Stargate}: an explicit per-gate override
 * wins, then the gate's material group, then the shape's own default.
 */
class StargateEffectiveMaterialTest
{
    @BeforeEach
    void loadGroups()
    {
        final Map<String, Object> atlantis = new LinkedHashMap<>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("portal", "WATER");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");

        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);
    }

    /** Adds a group beside the ones already loaded, which {@code load} would replace. */
    private static void addGroup(final String name, final String structure, final String portal)
    {
        MaterialGroupRegistry.registerDiscoveredGroup(new MaterialGroup(name, Material.valueOf(structure),
            Material.valueOf(portal), Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN));
    }

    @Test
    void shapeDefaultsApplyWhenThereIsNoGroupOrOverride()
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(null);

        assertEquals(Material.WATER, gate.getEffectivePortalMaterial());
        assertEquals(Material.STONE, gate.getEffectiveIrisMaterial());
        assertEquals(Material.GLOWSTONE, gate.getEffectiveLightMaterial());
        assertEquals(Material.OBSIDIAN, gate.getEffectiveStructureMaterial());
    }

    @Test
    void materialGroupOverridesShapeDefaults()
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.LAPIS_BLOCK, gate.getEffectiveStructureMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, gate.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
    }

    @Test
    void perGateCustomOverridesTheMaterialGroup()
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.BEDROCK);

        assertEquals(Material.BEDROCK, gate.getEffectiveIrisMaterial());
        // Only the overridden material changes; the rest still come from the group.
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
    }

    /**
     * An admin's per-gate override beats a shape that names the material in its own file.
     *
     * <p>The step below it -- a shape declaration beating the palette -- has been covered
     * since it was a regression. This is the step above, and it had nothing: every existing
     * test set an override on a shape that named nothing, so the two could not be told apart.
     * Swapping the first two steps of the resolution order left all 1483 tests green, which is
     * how this gap was found.
     *
     * <p>What it would look like in game: an admin runs the command to give one gate a
     * particular iris, on a gate whose shape asks for glass, and the gate keeps the glass with
     * no error and no explanation.
     */
    @Test
    void aPerGateOverrideBeatsAShapeThatNamesTheMaterialItself()
    {
        final StargateShape shape = new StargateShape();
        shape.setShapeStructureMaterial(Material.OBSIDIAN);
        shape.setShapeIrisMaterial(Material.GLASS);

        final Stargate gate = new Stargate();
        gate.setGateShape(shape);
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));
        gate.setGateCustom(true);
        gate.setGateCustomIrisMaterial(Material.BEDROCK);

        assertEquals(Material.BEDROCK, gate.getEffectiveIrisMaterial(),
            "the admin asked for this gate specifically; the shape file speaks for every gate "
            + "built from it, and the more specific answer wins");
    }

    @Test
    void customFlagWithNoOverrideFallsThroughInsteadOfReturningNull()
    {
        // The old inline ternaries returned the custom field unconditionally, so a gate
        // flagged custom with an unset material yielded null and blew up downstream.
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));
        gate.setGateCustom(true);

        assertNotNull(gate.getEffectivePortalMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
    }

    @Test
    void shapeMaterialNamedInItsFileOutranksThePalette()
    {
        // Regression: Horizontal.shape asks for a GLASS iris — a horizontal gate is meant
        // to be seen through — but is framed in obsidian, so it resolves to the Standard
        // palette. The palette must not overwrite what the shape asked for by name.
        final Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("structure", "OBSIDIAN");
        standard.put("iris", "STONE");
        standard.put("light", "GLOWSTONE");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", standard);
        MaterialGroupRegistry.load(section);

        final StargateShape shape = new StargateShape();
        shape.setShapeStructureMaterial(Material.OBSIDIAN);
        shape.setShapeIrisMaterial(Material.GLASS);

        final Stargate gate = new Stargate();
        gate.setGateShape(shape);
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN));

        assertEquals(Material.GLASS, gate.getEffectiveIrisMaterial());
        // The light material was never named by this shape, so the palette supplies it.
        assertEquals(Material.GLOWSTONE, gate.getEffectiveLightMaterial());
    }

    @Test
    void paletteStillSuppliesWhatTheShapeLeavesUnsaid()
    {
        final Map<String, Object> atlantis = new LinkedHashMap<>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);

        // A shape straight from the constructor has named nothing, so every material
        // comes from the palette — this is the case that makes palettes useful at all.
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.YELLOW_STAINED_GLASS, gate.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
        assertEquals(Material.LAPIS_BLOCK, gate.getEffectiveStructureMaterial());
    }

    @Test
    void frameMaterialFollowsThePaletteNotTheShapeDeclaration()
    {
        // Regression: a Standard-geometry gate built out of lapis resolves to the Atlantis
        // palette. Reporting the shape's declared OBSIDIAN would be a lie about what is
        // physically there, and StargateAnimator restores lit chevrons using this value —
        // so it would rebuild a lapis gate's chevrons in obsidian.
        final Map<String, Object> atlantis = new LinkedHashMap<>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);

        final StargateShape standard = new StargateShape();
        standard.setShapeStructureMaterial(Material.OBSIDIAN);
        standard.setShapeIrisMaterial(Material.STONE);

        final Stargate gate = new Stargate();
        gate.setGateShape(standard);
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.LAPIS_BLOCK, gate.getEffectiveStructureMaterial());
    }

    @Test
    void oneGeometryRendersDifferentlyInEachPalette()
    {
        // The whole point of material groups, and only true since the sample shapes
        // stopped declaring materials: build one shape in obsidian and it is a Standard
        // gate, build it in lapis and it is an Atlantis one.
        final Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("structure", "OBSIDIAN");
        standard.put("iris", "STONE");
        standard.put("light", "GLOWSTONE");
        standard.put("sign", "OAK_WALL_SIGN");
        final Map<String, Object> atlantis = new LinkedHashMap<>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        atlantis.put("sign", "WARPED_WALL_SIGN");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", standard);
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);

        // A shape parsed from a stripped sample file declares no materials at all.
        final StargateShape geometryOnly = new StargateShape();

        final Stargate plain = new Stargate();
        plain.setGateShape(geometryOnly);
        plain.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Standard"));

        final Stargate themed = new Stargate();
        themed.setGateShape(geometryOnly);
        themed.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.OBSIDIAN, plain.getEffectiveStructureMaterial());
        assertEquals(Material.STONE, plain.getEffectiveIrisMaterial());
        assertEquals(Material.OAK_WALL_SIGN, plain.getEffectiveSignMaterial());

        assertEquals(Material.LAPIS_BLOCK, themed.getEffectiveStructureMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, themed.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, themed.getEffectiveLightMaterial());
        assertEquals(Material.WARPED_WALL_SIGN, themed.getEffectiveSignMaterial());
    }

    @Test
    void customModeAloneDoesNotOptAGateOutOfItsPalette()
    {
        // Regression: /wormhole custom true used to snapshot the shape's materials into
        // the gate. With shapes no longer declaring any, that snapshot captured the
        // built-in defaults and pinned the gate to them forever — and `custom -all true`
        // would have done it to every gate on the server.
        final Map<String, Object> atlantis = new LinkedHashMap<>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);

        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));
        gate.setGateCustom(true);

        // Custom mode with nothing actually overridden still follows the palette.
        assertEquals(Material.YELLOW_STAINED_GLASS, gate.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());

        // And an override that is set still wins, which is the point of custom mode.
        gate.setGateCustomIrisMaterial(Material.BEDROCK);
        assertEquals(Material.BEDROCK, gate.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
    }

    @Test
    void unsetTickOverridesFallBackToTheShape()
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateCustom(true);

        // -1 means "not overridden"; returning it would stall the woosh animation.
        assertTrue(gate.getEffectiveWooshTicks() > 0);
        assertTrue(gate.getEffectiveLightTicks() > 0);
    }

    @Test
    void gateWithNoShapeAtAllStillReturnsUsableMaterials()
    {
        final Stargate gate = new Stargate();
        gate.setGateMaterialGroup(null);

        assertEquals(Material.WATER, gate.getEffectivePortalMaterial());
        assertEquals(Material.STONE, gate.getEffectiveIrisMaterial());
        assertEquals(Material.GLOWSTONE, gate.getEffectiveLightMaterial());
        assertEquals(Material.OBSIDIAN, gate.getEffectiveStructureMaterial());
    }

    private static Stargate gateInWorld(final World world)
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateWorld(world);
        gate.getGateStructureBlocks().add(new Location(world, 33, 64, -17));
        return gate;
    }

    /**
     * Asked for the portal material without loading anything, a gate whose group is not yet known
     * and whose frame is in an unloaded chunk answers null and reads no block, and still finds its
     * group once the chunk is there.
     *
     * <p>bStats counts the gates' portal materials on the server thread; without this, the first
     * count after boot would load the chunk of every gate nobody had used. The chunk is asked
     * about by its chunk coordinates, 33 and -17 shifted down by four bits: 2 and -2.
     */
    @Test
    void portalMaterialIsNotLookedUpInAnUnloadedChunk()
    {
        addGroup("Nether", "NETHERRACK", "LAVA");
        final World world = Mockito.mock(World.class);
        final Block frame = Mockito.mock(Block.class);
        Mockito.when(frame.getType()).thenReturn(Material.LAPIS_BLOCK);
        Mockito.when(world.getBlockAt(33, 64, -17)).thenReturn(frame);
        Mockito.when(world.isChunkLoaded(2, -2)).thenReturn(false);
        final Stargate gate = gateInWorld(world);

        assertNull(gate.getEffectivePortalMaterialIfLoaded());
        Mockito.verify(world, Mockito.never()).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());

        Mockito.when(world.isChunkLoaded(2, -2)).thenReturn(true);
        assertEquals(Material.WATER, gate.getEffectivePortalMaterialIfLoaded(),
            "the group is still found on a later ask, the first having cached nothing");
        Mockito.verify(world, Mockito.times(1)).getBlockAt(33, 64, -17);
    }

    /**
     * When every group gives the same portal material the group does not matter, so an unloaded
     * chunk need not be read: a server with one group, or groups that agree, gets a real answer
     * for every gate rather than "unknown". The default config has only Atlantis, whose portal is
     * water, so this is the case on most servers.
     */
    @Test
    void portalMaterialNeedsNoChunkWhenEveryGroupAgrees()
    {
        final World world = Mockito.mock(World.class);
        Mockito.when(world.isChunkLoaded(2, -2)).thenReturn(false);

        assertEquals(Material.WATER, gateInWorld(world).getEffectivePortalMaterialIfLoaded());

        addGroup("Pool", "PRISMARINE", "WATER");
        assertEquals(Material.WATER, gateInWorld(world).getEffectivePortalMaterialIfLoaded());
        Mockito.verify(world, Mockito.never()).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }

    /**
     * A frame that matches no group falls back to the shape's portal, so groups that agree with
     * each other but not with that fall-back still leave the answer to the frame.
     */
    @Test
    void portalMaterialStillNeedsTheChunkWhenTheGroupsAgreeOnADifferentMaterialThanNoGroup()
    {
        final Map<String, Object> only = new LinkedHashMap<>();
        only.put("structure", "NETHERRACK");
        only.put("portal", "LAVA");
        MaterialGroupRegistry.load(Map.of("Nether", only));
        final World world = Mockito.mock(World.class);
        Mockito.when(world.isChunkLoaded(2, -2)).thenReturn(false);

        assertNull(gateInWorld(world).getEffectivePortalMaterialIfLoaded());
        Mockito.verify(world, Mockito.never()).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }

    /** A gate whose group is already known needs no chunk, so it answers wherever it is. */
    @Test
    void portalMaterialOfAGateWithAKnownGroupNeedsNoChunk()
    {
        final World world = Mockito.mock(World.class);
        Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt())).thenReturn(false);
        final Stargate gate = gateInWorld(world);
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.WATER, gate.getEffectivePortalMaterialIfLoaded());
        Mockito.verify(world, Mockito.never()).getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }

    /** A world that cannot say whether a chunk is loaded is treated as not loaded rather than failing. */
    @Test
    void aWorldThatCannotAnswerIsTreatedAsUnloaded()
    {
        addGroup("Nether", "NETHERRACK", "LAVA");
        final World world = Mockito.mock(World.class);
        Mockito.when(world.isChunkLoaded(Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(new IllegalStateException("world unloaded"));

        assertNull(gateInWorld(world).getEffectivePortalMaterialIfLoaded());
    }
}
