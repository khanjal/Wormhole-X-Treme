package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * A ring carries two materials, and they are not interchangeable.
 *
 * <p>The travelling ring has to be a slab, because the rise is built out of slab halves —
 * a bottom slab fills the lower half of its block, a top slab the upper half — and that is
 * the only way to step half a block at a time. Anything else moves a whole block per frame
 * and stops reading as rings rising out of the floor.
 *
 * <p>The countdown light has no such constraint. It is a block that appears in the ring's
 * own pattern and then goes away again, so anything placeable will do.
 */
public class RingTest
{
    private static Ring ring()
    {
        return new Ring(0, 64, 0, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
    }

    @Test
    public void slabsAreAcceptedAsTheTravellingRing()
    {
        assertTrue(Ring.isUsableAsRing(Material.STONE_SLAB));
        assertTrue(Ring.isUsableAsRing(Material.DEEPSLATE_TILE_SLAB));
        assertTrue(Ring.isUsableAsRing(Material.WARPED_SLAB));
    }

    @Test
    public void anythingThatIsNotASlabIsRejectedAsTheTravellingRing()
    {
        // A full block here would silently cost the animation its half-block resolution,
        // which is the whole visual effect, so this is refused rather than accepted quietly.
        assertFalse(Ring.isUsableAsRing(Material.STONE));
        assertFalse(Ring.isUsableAsRing(Material.GLOWSTONE));
        assertFalse(Ring.isUsableAsRing(Material.AIR));
        assertFalse(Ring.isUsableAsRing(null));
    }

    @Test
    public void theSlabTestFallsBackToTheNameWithNoServerToAsk()
    {
        // On a server this reads minecraft:slabs, so a data pack that adds one gets a ring
        // material for free. There is no registry here, so this is the fallback answering —
        // and it has to be exact for everything the game ships, or the fallback would be a
        // wrong answer rather than merely a worse source.
        assertTrue(Ring.isUsableAsRing(Material.CUT_COPPER_SLAB));
        assertTrue(Ring.isUsableAsRing(Material.PETRIFIED_OAK_SLAB));
        assertTrue(Ring.isUsableAsRing(Material.MUD_BRICK_SLAB));
        assertFalse(Ring.isUsableAsRing(Material.SMOOTH_STONE));
    }

    @Test
    public void thereIsNoSuchTagForLightsSoTheyAreListedByHand()
    {
        // Minecraft has no light-emitting group and Bukkit cannot read a light level from a
        // Material at all, so this list is written out. It only has to look right, since a
        // drawn ring emits nothing whatever it is made of.
        assertTrue(Ring.glowingMaterials().contains(Material.GLOWSTONE));
        assertTrue(Ring.glowingMaterials().contains(Material.SEA_LANTERN));
        assertFalse(Ring.glowingMaterials().contains(Material.TORCH),
            "a torch cannot be drawn inside a floor");
        assertFalse(Ring.glowingMaterials().contains(Material.DIRT));
        assertThrows(UnsupportedOperationException.class,
            () -> Ring.glowingMaterials().add(Material.DIRT));
    }

    @Test
    public void bothMaterialsAreEditableIndependently()
    {
        final Ring ring = ring();
        assertEquals(Material.STONE_SLAB, ring.getRingMaterial());
        assertEquals(Material.GLOWSTONE, ring.getLightMaterial());

        ring.setRingMaterial(Material.DEEPSLATE_TILE_SLAB);
        ring.setLightMaterial(Material.SEA_LANTERN);

        assertEquals(Material.DEEPSLATE_TILE_SLAB, ring.getRingMaterial());
        assertEquals(Material.SEA_LANTERN, ring.getLightMaterial());
    }

    @Test
    public void aRingCoversEveryColumnOfItsOwnFootprint()
    {
        // Used to refuse overlapping builds. It ignores y on purpose: two rings sharing a
        // column at different heights still means one animating through the other.
        final Ring ring = ring();
        assertTrue(ring.coversColumn(0, 0), "the anchor itself");
        assertTrue(ring.coversColumn(-3, 0), "perimeter on the anchor row");
        assertTrue(ring.coversColumn(1, 1), "interior");
        assertFalse(ring.coversColumn(-3, -3), "the cut corner is not part of the ring");
        assertFalse(ring.coversColumn(9, 9), "well outside");
    }

    @Test
    public void anchorDistanceIsSquaredSoSeparationChecksNeedNoSquareRoot()
    {
        final Ring here = ring();
        final Ring there = new Ring(3, 64, 4, RingPattern.ODD, RingOrientation.FLOOR,
            Material.STONE_SLAB, Material.GLOWSTONE);
        assertEquals(25L, here.anchorDistanceSquared(there));
    }

    @Test
    public void anchorDistanceIgnoresHeight()
    {
        // Minimum separation is about footprints on the ground, not about how far apart two
        // rings are in the air. A ring directly above another is zero apart horizontally,
        // and that is exactly the collision the check needs to catch.
        final Ring low = ring();
        final Ring high = new Ring(0, 200, 0, RingPattern.ODD, RingOrientation.CEILING,
            Material.STONE_SLAB, Material.GLOWSTONE);
        assertEquals(0L, low.anchorDistanceSquared(high));
    }

    @Test
    public void theTriggerVolumeRepeatsTheInteriorOncePerLayer()
    {
        final Ring ring = ring();
        assertEquals(21, ring.interiorBlocks().size());
        assertEquals(16, ring.perimeterBlocks().size());
        assertEquals(21 * 4, ring.triggerVolumeBlocks(4).size());
    }
}
