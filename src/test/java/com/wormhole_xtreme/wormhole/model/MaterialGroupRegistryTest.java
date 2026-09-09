package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MaterialGroupRegistry} loading and lookup.
 */
class MaterialGroupRegistryTest
{
    private static Map<String, Object> group(final String structure, final String portal,
        final String iris, final String light)
    {
        final Map<String, Object> m = new LinkedHashMap<>();
        if (structure != null) m.put("structure", structure);
        if (portal != null) m.put("portal", portal);
        if (iris != null) m.put("iris", iris);
        if (light != null) m.put("light", light);
        return m;
    }

    @Test
    void firstDeclaredGroupIsTheDefault()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Atlantis", group("LAPIS_BLOCK", "WATER", "YELLOW_STAINED_GLASS", "SEA_LANTERN"));

        MaterialGroupRegistry.load(section);

        assertEquals("Standard", MaterialGroupRegistry.getDefaultGroup().getName());
        assertEquals(2, MaterialGroupRegistry.getGroups().size());
    }

    @Test
    void groupsAreFoundByTheirStructureMaterial()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
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
    void lookupByNameIsCaseInsensitive()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Atlantis", group("LAPIS_BLOCK", "WATER", "STONE", "GLOWSTONE"));

        MaterialGroupRegistry.load(section);

        assertNotNull(MaterialGroupRegistry.getGroup("atlantis"));
        assertNotNull(MaterialGroupRegistry.getGroup("ATLANTIS"));
        assertNull(MaterialGroupRegistry.getGroup("nope"));
    }

    @Test
    void duplicateStructureMaterialIsRejectedRatherThanShadowing()
    {
        // The frame material is what identifies a palette, so two groups claiming the
        // same one would make detection ambiguous. The first declared keeps it.
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Impostor", group("OBSIDIAN", "LAVA", "BEDROCK", "SEA_LANTERN"));

        MaterialGroupRegistry.load(section);

        assertEquals(1, MaterialGroupRegistry.getGroups().size());
        assertEquals("Standard", MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getName());
        assertNull(MaterialGroupRegistry.getGroup("Impostor"));
    }

    @Test
    void groupWithUnreadableStructureMaterialIsSkipped()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Good", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        section.put("Bad", group("NOT_A_REAL_BLOCK", "WATER", "STONE", "GLOWSTONE"));

        MaterialGroupRegistry.load(section);

        assertEquals(1, MaterialGroupRegistry.getGroups().size());
        assertNull(MaterialGroupRegistry.getGroup("Bad"));
    }

    @Test
    void missingPortalIrisAndLightFallBackToDefaults()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Sparse", group("LAPIS_BLOCK", null, null, null));

        MaterialGroupRegistry.load(section);

        final MaterialGroup g = MaterialGroupRegistry.getGroup("Sparse");
        assertNotNull(g);
        assertEquals(Material.WATER, g.getPortalMaterial());
        assertEquals(Material.STONE, g.getIrisMaterial());
        assertEquals(Material.GLOWSTONE, g.getLightMaterial());
    }

    @Test
    void emptyConfigStillYieldsAWorkingDefaultGroup()
    {
        // A server that has never touched this section must keep working.
        MaterialGroupRegistry.load(null);

        final MaterialGroup fallback = MaterialGroupRegistry.getDefaultGroup();
        assertNotNull(fallback);
        assertEquals(Material.OBSIDIAN, fallback.getStructureMaterial());
        assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN));
    }

    /**
     * A palette can say what an unlit chevron is built from.
     *
     * <p>This is the one palette material that changes what a player has to build rather than
     * only how it looks, so it is read like the rest but defaulted like none of them -- see
     * the test below.
     */
    @Test
    void aPaletteCanNameTheMaterialItsUnlitChevronsAreBuiltFrom()
    {
        final Map<String, Object> standard = group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE");
        standard.put("chevron", "REDSTONE_LAMP");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", standard);

        MaterialGroupRegistry.load(section);

        assertEquals(Material.REDSTONE_LAMP,
            MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getChevronMaterial());
    }

    /**
     * A palette that says nothing about chevrons gets none, rather than a default.
     *
     * <p>Every other material here falls back to a built-in when the key is missing, and this
     * one deliberately does not. A default would widen what detection accepts as a gate frame
     * on every server that never asked for distinct chevrons -- silently, at the next restart,
     * with nothing in the config to explain why an unfamiliar block now builds a gate.
     */
    @Test
    void aPaletteThatNamesNoChevronMaterialGetsNoneRatherThanADefault()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));

        MaterialGroupRegistry.load(section);

        assertNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getChevronMaterial(),
            "an absent chevron key must mean no chevron material, not a built-in one");
    }

    /**
     * An unreadable chevron material leaves the palette usable.
     *
     * <p>Consistent with how a bad portal or iris name is treated: the group still loads. A
     * typo in an optional decorative key should cost the chevrons, not the whole palette and
     * every gate built from it.
     */
    @Test
    void anUnreadableChevronMaterialDoesNotCostThePalette()
    {
        final Map<String, Object> standard = group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE");
        standard.put("chevron", "NOT_A_REAL_BLOCK");
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", standard);

        MaterialGroupRegistry.load(section);

        final MaterialGroup g = MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN);
        assertNotNull(g, "the palette itself must survive a bad chevron name");
        assertNull(g.getChevronMaterial());
    }

    private static MaterialGroup palette(final String name, final Material frame)
    {
        return new MaterialGroup(name, frame, Material.WATER, Material.STONE,
            Material.GLOWSTONE, Material.OAK_WALL_SIGN);
    }

    /**
     * A palette derived from a shape is findable by both of the things that look it up.
     *
     * <p>Registering one used to mean three assignments in a row -- the name map, the material
     * map, then possibly the default. Nothing here proves the order they land in, which is the
     * point: a caller only ever sees a registry where all three agree, so a test can ask all
     * three without knowing anything about how it got that way.
     */
    @Test
    void aDiscoveredPaletteIsFoundByNameAndByFrameMaterial()
    {
        MaterialGroupRegistry.load(null);

        MaterialGroupRegistry.registerDiscoveredGroup(palette("Diamond", Material.DIAMOND_BLOCK));

        assertNotNull(MaterialGroupRegistry.getGroup("Diamond"),
            "a discovered palette should be findable by the name it was given");
        assertEquals("Diamond",
            MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIAMOND_BLOCK).getName(),
            "and by the frame material that identifies it during detection");
        assertEquals("Standard", MaterialGroupRegistry.getDefaultGroup().getName(),
            "registering one must not displace a default that was already there");
    }

    /** A frame material another group already claims is left with that group. */
    @Test
    void aDiscoveredPaletteDoesNotShadowOneAlreadyClaimingItsFrameMaterial()
    {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("Standard", group("OBSIDIAN", "WATER", "STONE", "GLOWSTONE"));
        MaterialGroupRegistry.load(section);

        MaterialGroupRegistry.registerDiscoveredGroup(palette("Impostor", Material.OBSIDIAN));

        assertEquals("Standard",
            MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getName(),
            "obsidian was already spoken for, so the discovered palette is dropped");
        assertNull(MaterialGroupRegistry.getGroup("Impostor"),
            "and it does not get in under its own name either");
    }

    /**
     * Two palettes discovered at the same moment both survive.
     *
     * <p>Registering one is a read-modify-write: copy both maps, add to the copies, put them
     * back. Done with plain assignments, two of these landing together means both copy the
     * same starting maps and the second write discards the first, and a palette the shapes
     * asked for is silently absent.
     *
     * <p>Nothing calls it that way today -- discovery is one sequential loop, and this plugin
     * has no async task anywhere -- so this guards a property rather than reproducing a bug
     * anybody has hit. It is worth holding anyway: the class declares itself safe to read
     * from another thread, and this is the half of that claim a single-threaded caller would
     * never expose. Written against plain assignments it fails on the first attempt, so it is
     * not a subtle window being papered over.
     *
     * <p>Repeated because a race needing a particular interleaving may not show in one go. It
     * cannot fail against a compare-and-set however the two threads interleave, so a failure
     * here is a regression rather than a flake.
     */
    @Test
    void twoPalettesDiscoveredAtTheSameMomentBothSurvive() throws Exception
    {
        for (int attempt = 0; attempt < 200; attempt++)
        {
            MaterialGroupRegistry.load(null);
            final CyclicBarrier together = new CyclicBarrier(2);

            final Thread diamond = registering(together, palette("Diamond", Material.DIAMOND_BLOCK));
            final Thread gold = registering(together, palette("Gold", Material.GOLD_BLOCK));
            diamond.start();
            gold.start();
            diamond.join();
            gold.join();

            assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.DIAMOND_BLOCK),
                "the diamond palette went missing on attempt " + attempt);
            assertNotNull(MaterialGroupRegistry.getGroupByStructureMaterial(Material.GOLD_BLOCK),
                "the gold palette went missing on attempt " + attempt);
        }
    }

    private static Thread registering(final CyclicBarrier together, final MaterialGroup group)
    {
        return new Thread(() ->
        {
            try
            {
                together.await();
            }
            catch (final Exception e)
            {
                throw new IllegalStateException(e);
            }
            MaterialGroupRegistry.registerDiscoveredGroup(group);
        });
    }
}
