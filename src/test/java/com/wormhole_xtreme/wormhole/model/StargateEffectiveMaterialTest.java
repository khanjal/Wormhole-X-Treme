package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the material resolution order on {@link Stargate}: an explicit per-gate override
 * wins, then the gate's material group, then the shape's own default.
 */
public class StargateEffectiveMaterialTest
{
    @BeforeEach
    public void loadGroups()
    {
        final Map<String, Object> atlantis = new LinkedHashMap<String, Object>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("portal", "WATER");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");

        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Atlantis", atlantis);
        MaterialGroupRegistry.load(section);
    }

    @Test
    public void shapeDefaultsApplyWhenThereIsNoGroupOrOverride()
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
    public void materialGroupOverridesShapeDefaults()
    {
        final Stargate gate = new Stargate();
        gate.setGateShape(new StargateShape());
        gate.setGateMaterialGroup(MaterialGroupRegistry.getGroup("Atlantis"));

        assertEquals(Material.LAPIS_BLOCK, gate.getEffectiveStructureMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, gate.getEffectiveIrisMaterial());
        assertEquals(Material.SEA_LANTERN, gate.getEffectiveLightMaterial());
    }

    @Test
    public void perGateCustomOverridesTheMaterialGroup()
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

    @Test
    public void customFlagWithNoOverrideFallsThroughInsteadOfReturningNull()
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
    public void shapeMaterialNamedInItsFileOutranksThePalette()
    {
        // Regression: Horizontal.shape asks for a GLASS iris — a horizontal gate is meant
        // to be seen through — but is framed in obsidian, so it resolves to the Standard
        // palette. The palette must not overwrite what the shape asked for by name.
        final Map<String, Object> standard = new LinkedHashMap<String, Object>();
        standard.put("structure", "OBSIDIAN");
        standard.put("iris", "STONE");
        standard.put("light", "GLOWSTONE");
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
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
    public void paletteStillSuppliesWhatTheShapeLeavesUnsaid()
    {
        final Map<String, Object> atlantis = new LinkedHashMap<String, Object>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
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
    public void frameMaterialFollowsThePaletteNotTheShapeDeclaration()
    {
        // Regression: a Standard-geometry gate built out of lapis resolves to the Atlantis
        // palette. Reporting the shape's declared OBSIDIAN would be a lie about what is
        // physically there, and StargateAnimator restores lit chevrons using this value —
        // so it would rebuild a lapis gate's chevrons in obsidian.
        final Map<String, Object> atlantis = new LinkedHashMap<String, Object>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
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
    public void oneGeometryRendersDifferentlyInEachPalette()
    {
        // The whole point of material groups, and only true since the sample shapes
        // stopped declaring materials: build one shape in obsidian and it is a Standard
        // gate, build it in lapis and it is an Atlantis one.
        final Map<String, Object> standard = new LinkedHashMap<String, Object>();
        standard.put("structure", "OBSIDIAN");
        standard.put("iris", "STONE");
        standard.put("light", "GLOWSTONE");
        standard.put("sign", "OAK_WALL_SIGN");
        final Map<String, Object> atlantis = new LinkedHashMap<String, Object>();
        atlantis.put("structure", "LAPIS_BLOCK");
        atlantis.put("iris", "YELLOW_STAINED_GLASS");
        atlantis.put("light", "SEA_LANTERN");
        atlantis.put("sign", "WARPED_WALL_SIGN");
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
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
    public void gateWithNoShapeAtAllStillReturnsUsableMaterials()
    {
        final Stargate gate = new Stargate();
        gate.setGateMaterialGroup(null);

        assertEquals(Material.WATER, gate.getEffectivePortalMaterial());
        assertEquals(Material.STONE, gate.getEffectiveIrisMaterial());
        assertEquals(Material.GLOWSTONE, gate.getEffectiveLightMaterial());
        assertEquals(Material.OBSIDIAN, gate.getEffectiveStructureMaterial());
    }
}
