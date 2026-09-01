package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MaterialGroupRegistry} loading and lookup.
 */
public class MaterialGroupRegistryTest
{
    private static Map<String, Object> group(final String structure, final String portal,
        final String iris, final String light)
    {
        final Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (structure != null) m.put("structure", structure);
        if (portal != null) m.put("portal", portal);
        if (iris != null) m.put("iris", iris);
        if (light != null) m.put("light", light);
        return m;
    }

    @Test
    public void firstDeclaredGroupIsTheDefault()
    {
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Atlantis", group("LAPIS_BLOCK", "WATER", "YELLOW_STAINED_GLASS", "SEA_LANTERN"));

        MaterialGroupRegistry.load(section);

        assertEquals("Standard", MaterialGroupRegistry.getDefaultGroup().getName());
        assertEquals(2, MaterialGroupRegistry.getGroups().size());
    }

    @Test
    public void groupsAreFoundByTheirStructureMaterial()
    {
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Atlantis", group("LAPIS_BLOCK", "WATER", "YELLOW_STAINED_GLASS", "SEA_LANTERN"));

        MaterialGroupRegistry.load(section);

        // This lookup is the detection hot path — one map hit regardless of group count.
        final MaterialGroup atlantis = MaterialGroupRegistry.getGroupByStructureMaterial(Material.LAPIS_BLOCK);
        assertNotNull(atlantis);
        assertEquals("Atlantis", atlantis.getName());
        assertEquals(Material.SEA_LANTERN, atlantis.getLightMaterial());
        assertEquals(Material.YELLOW_STAINED_GLASS, atlantis.getIrisMaterial());

        assertNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIRT));
    }

    @Test
    public void lookupByNameIsCaseInsensitive()
    {
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Atlantis", group("LAPIS_BLOCK", "WATER", "STONE", "GLOWSTONE"));

        MaterialGroupRegistry.load(section);

        assertNotNull(MaterialGroupRegistry.getGroup("atlantis"));
        assertNotNull(MaterialGroupRegistry.getGroup("ATLANTIS"));
        assertNull(MaterialGroupRegistry.getGroup("nope"));
    }

    @Test
    public void duplicateStructureMaterialIsRejectedRatherThanShadowing()
    {
        // The frame material is what identifies a palette, so two groups claiming the
        // same one would make detection ambiguous. The first declared keeps it.
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Impostor", group("OBSIDIAN", "LAVA", "BEDROCK", "SEA_LANTERN"));

        MaterialGroupRegistry.load(section);

        assertEquals(1, MaterialGroupRegistry.getGroups().size());
        assertEquals("Standard", MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getName());
        assertNull(MaterialGroupRegistry.getGroup("Impostor"));
    }

    @Test
    public void groupWithUnreadableStructureMaterialIsSkipped()
    {
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Good", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Bad", group("NOT_A_REAL_BLOCK", "WATER", "STONE", "GLOWSTONE"));

        MaterialGroupRegistry.load(section);

        assertEquals(1, MaterialGroupRegistry.getGroups().size());
        assertNull(MaterialGroupRegistry.getGroup("Bad"));
    }

    @Test
    public void missingPortalIrisAndLightFallBackToDefaults()
    {
        final Map<String, Object> section = new LinkedHashMap<String, Object>();
        section.put("Sparse", group("LAPIS_BLOCK", null, null, null));

        MaterialGroupRegistry.load(section);

        final MaterialGroup g = MaterialGroupRegistry.getGroup("Sparse");
        assertNotNull(g);
        assertEquals(Material.WATER, g.getPortalMaterial());
        assertEquals(Material.STONE, g.getIrisMaterial());
        assertEquals(Material.GLOWSTONE, g.getLightMaterial());
    }

    @Test
    public void emptyConfigStillYieldsAWorkingDefaultGroup()
    {
        // A server that has never touched this section must keep working.
        MaterialGroupRegistry.load(null);

        final MaterialGroup fallback = MaterialGroupRegistry.getDefaultGroup();
        assertNotNull(fallback);
        assertEquals(Material.OBSIDIAN, fallback.getStructureMaterial());
        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN));
    }
}
