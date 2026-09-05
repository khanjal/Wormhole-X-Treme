package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.MaterialGroupRegistry;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeLayer;

/**
 * A gate can show where its chevrons are before any of them light.
 *
 * <p>Until this, a chevron was invisible while the gate sat idle: an {@code [S:L#n]} cell is a
 * frame block that happens to be in a lighting wave, and detection required every frame block
 * to be the one material the palette named, so a chevron could only ever be more obsidian.
 * The lighting animation then drew glowstone over blocks that had looked like nothing in
 * particular a moment earlier.
 *
 * <p>Two things had to give for a distinct unlit chevron to be buildable, and both are what
 * these tests hold in place. A shape can now name a {@code CHEVRON_MATERIAL} and mark cells
 * {@code [C]}, and detection accepts that material where a chevron belongs -- while still
 * accepting the frame material there, because every gate standing in every world today was
 * built that way and has to go on being found.
 */
public class UnlitChevronTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    /**
     * Parses a shipped shape, optionally rewriting one cell on the way in.
     *
     * <p>Editing a real shape beats hand-rolling a synthetic one: the parser rejects a shape
     * with no {@code :EP} cell outright, and a fixture built just far enough to get past that
     * would not prove a {@code [C]} cell survives alongside everything else a real gate has.
     *
     * @param name
     *            the shipped shape to read
     * @param from
     *            the cell text to replace, or null to load the file unchanged
     * @param to
     *            what to replace it with
     * @return the parsed shape
     */
    private static Stargate3DShape load(final String name, final String from, final String to)
        throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);

        final List<String> lines = Files.readAllLines(SHAPE_DIR.resolve(name + ".shape"));
        final List<String> out = new ArrayList<String>();
        for (final String line : lines)
        {
            out.add((from == null) || line.trim().startsWith("#") ? line : line.replace(from, to));
        }
        return new Stargate3DShape(out.toArray(new String[0]));
    }

    private static Stargate3DShape load(final String name) throws Exception
    {
        return load(name, null, null);
    }

    /** Every cell of a shape that parsed as a frame block, across all layers. */
    private static List<Integer[]> frameCells(final Stargate3DShape shape)
    {
        final List<Integer[]> all = new ArrayList<Integer[]>();
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if (layer != null)
            {
                all.addAll(layer.getLayerBlockPositions());
            }
        }
        return all;
    }

    /** Every cell of a shape that parsed as a chevron block, across all layers. */
    private static List<Integer[]> chevronCells(final Stargate3DShape shape)
    {
        final List<Integer[]> all = new ArrayList<Integer[]>();
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if (layer != null)
            {
                all.addAll(layer.getLayerChevronPositions());
            }
        }
        return all;
    }

    private static boolean containsCell(final List<Integer[]> cells, final Integer[] wanted)
    {
        for (final Integer[] c : cells)
        {
            if (c[1].intValue() == wanted[1].intValue() && c[2].intValue() == wanted[2].intValue())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * A {@code [C]} cell is a block the player has to build, but it is deliberately not in the
     * frame list.
     *
     * <p>Detection works out which palette a gate belongs to by reading the first frame block
     * it finds and looking that material up. A chevron sitting in that list would have a gate
     * fronted with redstone lamps resolve to the lamp palette if one happened to exist, and to
     * no palette at all if one did not -- so a shape using {@code [C]} would become
     * undetectable rather than merely differently coloured.
     */
    @Test
    public void aChevronCellIsNotCountedAsAFrameBlock() throws Exception
    {
        final Stargate3DShape shape = load("Standard", "[S:L#1]", "[C:L#1]");
        assertEquals(1, chevronCells(shape).size(),
            "the one rewritten cell should be the shape's only chevron cell");
        final Integer[] chevron = chevronCells(shape).get(0);
        assertFalse(containsCell(frameCells(shape), chevron),
            "a [C] cell in the frame list would drag the palette lookup onto the chevron "
                + "material and stop the gate being detected at all");
    }

    /**
     * A {@code [C]} cell still lights in its turn.
     *
     * <p>The two markers are independent -- {@code [C]} says what the block is made of,
     * {@code :L#n} says when it lights -- and a chevron that showed its unlit state but never
     * joined the dialling sequence would be the worse half of the feature.
     */
    @Test
    public void aChevronCellCanStillCarryALightOrder() throws Exception
    {
        final Stargate3DShape shape = load("Standard", "[S:L#1]", "[C:L#1]");
        final Integer[] chevron = chevronCells(shape).get(0);

        boolean lit = false;
        for (final StargateShapeLayer layer : shape.getShapeLayers())
        {
            if ((layer == null) || (layer.getLayerLightPositions().size() <= 1))
            {
                continue;
            }
            final ArrayList<Integer[]> wave = layer.getLayerLightPositions().get(1);
            if ((wave != null) && containsCell(wave, chevron))
            {
                lit = true;
            }
        }
        assertTrue(lit, "[C:L#1] should still be in lighting wave 1, or the chevron never lights");
    }

    /**
     * A shape that names no chevron material has none, rather than inheriting a default.
     *
     * <p>This is what keeps every already-built gate safe. Detection only relaxes its
     * frame-material check when a chevron material exists to relax it towards; a default here
     * would quietly widen what counts as a gate for every shape that never asked for it.
     */
    @Test
    public void aShapeThatNamesNoChevronMaterialHasNone() throws Exception
    {
        assertNull(load("Minimal").getShapeChevronMaterial(),
            "Minimal names no CHEVRON_MATERIAL, so it must not acquire one by default");
    }

    /**
     * Standard offers unlit chevrons without requiring them, and without pinning them.
     *
     * <p>Its seven chevrons stay written {@code [S:L#n]}, so they remain frame blocks and an
     * obsidian gate built years ago still parses, still detects, and still dials. It also
     * names no chevron material of its own: a shape describes geometry, and the same geometry
     * built in the Atlantis palette should get Atlantis's chevrons rather than obsidian
     * Standard's.
     */
    @Test
    public void standardOffersUnlitChevronsWithoutBreakingObsidianOnes() throws Exception
    {
        final Stargate3DShape standard = load("Standard");
        assertNull(standard.getShapeChevronMaterial(),
            "Standard should take its chevron material from the palette -- pinning one in the "
                + "shape would force the same block on every palette the shape is built in");
        assertTrue(chevronCells(standard).isEmpty(),
            "Standard must use no [C] cells -- a [C] would make the chevron material "
                + "mandatory and every obsidian Standard gate in the world undetectable");
    }

    /**
     * The palette is where a chevron material normally comes from.
     *
     * <p>Detection has to answer this before there is a gate to ask, so the rule lives as a
     * function of the shape and the palette the frame resolved to. Getting it from the palette
     * is what lets one shape be built in obsidian with lamp chevrons and in lapis with
     * something else, without a shape file per combination.
     */
    @Test
    public void aPaletteSuppliesTheChevronMaterialWhenTheShapeDoesNot() throws Exception
    {
        final Stargate3DShape standard = load("Standard");
        final MaterialGroup palette = new MaterialGroup("Standard", Material.OBSIDIAN,
            Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN,
            Material.REDSTONE_LAMP);

        assertEquals(Material.REDSTONE_LAMP, Stargate.resolveChevronMaterial(standard, palette));
    }

    /**
     * A shape that names a chevron material outranks the palette.
     *
     * <p>Same precedence the other materials already use: a shape authored around a particular
     * block means it, and a palette that never considered chevrons should not take it away.
     */
    @Test
    public void aShapeThatNamesAChevronMaterialOutranksThePalette() throws Exception
    {
        final Stargate3DShape pinned = load("Standard", null, null);
        pinned.setShapeChevronMaterial(Material.SEA_LANTERN);
        final MaterialGroup palette = new MaterialGroup("Standard", Material.OBSIDIAN,
            Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN,
            Material.REDSTONE_LAMP);

        assertEquals(Material.SEA_LANTERN, Stargate.resolveChevronMaterial(pinned, palette));
    }

    /**
     * With neither naming one, there is no chevron material at all.
     *
     * <p>The ordinary case, and the one that keeps every existing gate safe: detection only
     * relaxes its frame-material check when there is a second material to relax it towards.
     */
    @Test
    public void neitherShapeNorPaletteNamingOneMeansNoChevronMaterial() throws Exception
    {
        final MaterialGroup plain = new MaterialGroup("Standard", Material.OBSIDIAN,
            Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN);

        assertNull(Stargate.resolveChevronMaterial(load("Standard"), plain));
        assertNull(Stargate.resolveChevronMaterial(null, null));
    }

    /**
     * The shipped Standard palette offers unlit chevrons out of the box.
     *
     * <p>Read from the real {@code config.yml} rather than a fixture, because the point is
     * that somebody installing this can build a lamp-chevron gate without editing anything.
     * A default dropped during an unrelated edit to that file would otherwise go unnoticed.
     */
    @Test
    public void theShippedStandardPaletteOffersUnlitChevrons() throws Exception
    {
        final Object parsed = new org.yaml.snakeyaml.Yaml().load(new String(
            Files.readAllBytes(Paths.get("src/main/resources/config.yml")),
            java.nio.charset.StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked")
        final java.util.Map<String, Object> root = (java.util.Map<String, Object>) parsed;
        @SuppressWarnings("unchecked")
        final java.util.Map<String, Object> section =
            (java.util.Map<String, Object>) root.get("gate-material-groups");

        MaterialGroupRegistry.load(section);

        assertEquals(Material.REDSTONE_LAMP,
            MaterialGroupRegistry.getGroupByStructureMaterial(Material.OBSIDIAN).getChevronMaterial(),
            "the shipped Standard palette should let an obsidian gate be built with lamp "
                + "chevrons without the server owner editing any file");
    }

    /**
     * The permissive rule rests on a chevron being a frame block, so this pins that.
     *
     * <p>Detection allows the chevron material only at a frame cell that also carries a light
     * marker. A shipped shape that put {@code :L} on a {@code [P]} cell instead would silently
     * fall outside that rule: no error, no warning, just a chevron that cannot be built from
     * the chevron material and nobody able to say why.
     */
    @Test
    public void everyLightMarkedCellOfEveryShippedShapeIsAlsoAFrameBlock() throws Exception
    {
        try (java.util.stream.Stream<Path> listing = Files.list(SHAPE_DIR))
        {
            for (final Path p : listing.toList())
            {
                final String file = p.getFileName().toString();
                if (!file.endsWith(".shape"))
                {
                    continue;
                }
                final Stargate3DShape shape = load(file.substring(0, file.length() - 6));
                for (final StargateShapeLayer layer : shape.getShapeLayers())
                {
                    if (layer == null)
                    {
                        continue;
                    }
                    for (final ArrayList<Integer[]> wave : layer.getLayerLightPositions())
                    {
                        if (wave == null)
                        {
                            continue; // index 0 is the placeholder the runtime lighting expects
                        }
                        for (final Integer[] pos : wave)
                        {
                            assertTrue(containsCell(layer.getLayerBlockPositions(), pos),
                                file + " marks a light on a cell that is not a frame block, so "
                                    + "detection would never accept a chevron material there");
                        }
                    }
                }
            }
        }
    }

    /**
     * A cell key tells one cell of a layer from another, in both directions.
     *
     * <p>This is the join detection depends on: it decides whether a frame block is a chevron
     * by asking whether that block's cell key is in the layer's set of light-marked keys.
     * Both ways of getting that wrong are silent. A key that collapsed -- built from the
     * shape position's first element, say, which is always zero -- would put every frame block
     * in the set and let the chevron material be built anywhere in the frame. A key that
     * missed would accept it nowhere, and the feature would look like it had never been
     * written. Neither throws; both need asserting from the outside.
     */
    @Test
    public void aCellKeyTellsOneCellOfALayerFromAnother() throws Exception
    {
        final Stargate3DShape standard = load("Standard");
        final StargateShapeLayer face = standard.getShapeLayers().get(1);
        final java.util.Set<Long> lit = StargateHelper.lightCells(face);

        assertEquals(7, lit.size(),
            "Standard's seven chevrons must produce seven distinct keys -- a key that "
                + "collapses them would match every frame block in the layer");

        int plainFrameBlocks = 0;
        for (final Integer[] pos : face.getLayerBlockPositions())
        {
            boolean isChevron = false;
            for (final ArrayList<Integer[]> wave : face.getLayerLightPositions())
            {
                if ((wave != null) && containsCell(wave, pos))
                {
                    isChevron = true;
                }
            }
            if (isChevron)
            {
                assertTrue(lit.contains(StargateHelper.cellKey(pos)),
                    "a chevron's key must be in the set, or the chevron material is "
                        + "never accepted and no gate can be built with visible chevrons");
                continue;
            }
            assertFalse(lit.contains(StargateHelper.cellKey(pos)),
                "a plain [S] cell must not match a chevron key, or the chevron material "
                    + "would be accepted anywhere in the frame");
            plainFrameBlocks++;
        }
        assertTrue(plainFrameBlocks > 0, "the layer should have plain frame blocks to compare");
    }
}
