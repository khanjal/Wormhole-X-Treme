package com.wormhole_xtreme.wormhole.model.ring;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Detection is the first thing a player meets, so its answers have to be right and its
 * refusals have to be useful.
 *
 * <p>The template is read for four separate things — which pattern, where the anchor is,
 * what the ring is made of, and which surface it is set into — and only the first of those
 * is obvious from the shape. The material comes from the slab the player chose, so a ring
 * laid in deepslate rises in deepslate without anyone running a command. The orientation
 * comes from the slab halves, because a slab resting on a floor is a bottom slab and one
 * hung under a ceiling is a top slab, which is a fact about the template rather than a
 * guess about its surroundings.
 */
class RingTemplateTest
{
    /** A probe backed by a map, so a test can lay out slabs without a server. */
    private static final class FakeWorld implements RingTemplate.BlockProbe
    {
        private final Map<String, Material> materials = new HashMap<String, Material>();
        private final Map<String, RingTemplate.SlabHalf> halves = new HashMap<String, RingTemplate.SlabHalf>();

        private static String key(final int x, final int y, final int z)
        {
            return x + ":" + y + ":" + z;
        }

        void put(final int x, final int y, final int z, final Material material,
            final RingTemplate.SlabHalf half)
        {
            materials.put(key(x, y, z), material);
            if (half != null)
            {
                halves.put(key(x, y, z), half);
            }
        }

        /** Lays a complete ring of one slab type at one height. */
        void layRing(final RingPattern pattern, final int ax, final int ay, final int az,
            final Material material, final RingTemplate.SlabHalf half)
        {
            for (final RingPattern.Offset offset : pattern.getPerimeter())
            {
                put(ax + offset.getDx(), ay, az + offset.getDz(), material, half);
            }
        }

        @Override
        public Material materialAt(final int x, final int y, final int z)
        {
            final Material found = materials.get(key(x, y, z));
            return found == null ? Material.AIR : found;
        }

        @Override
        public RingTemplate.SlabHalf halfAt(final int x, final int y, final int z)
        {
            return halves.get(key(x, y, z));
        }
    }

    private static RingTemplate.Result detect(final FakeWorld world, final int x, final int y, final int z)
    {
        return RingTemplate.detect(world, x, y, z, 5, Material.GLOWSTONE);
    }

    @Test
    void aRingOfBottomSlabsIsAFloorRingAnchoredAtItsCentre()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 50, 64, 50, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        final RingTemplate.Result result = detect(world, 50, 64, 50);

        assertTrue(result.isSuccess());
        final Ring ring = result.getRing();
        assertEquals(RingPattern.ODD, ring.getPattern());
        assertEquals(RingOrientation.FLOOR, ring.getOrientation());
        assertEquals(50, ring.getAnchorX());
        assertEquals(64, ring.getAnchorY());
        assertEquals(50, ring.getAnchorZ());
    }

    @Test
    void theRingKeepsWhateverSlabThePlayerLaidItIn()
    {
        // The point of reading the material rather than defaulting it: a ring built in a
        // deepslate base rises in deepslate, with no command run.
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.DEEPSLATE_TILE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        final RingTemplate.Result result = detect(world, 0, 64, 0);

        assertTrue(result.isSuccess());
        assertEquals(Material.DEEPSLATE_TILE_SLAB, result.getRing().getRingMaterial());
        assertEquals(Material.GLOWSTONE, result.getRing().getLightMaterial());
    }

    @Test
    void topSlabsMeanTheRingHangsFromACeiling()
    {
        // Slabs hung under a ceiling are top slabs. Reading the half means orientation is
        // something the template states rather than something inferred from surroundings.
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 67, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.TOP);

        final RingTemplate.Result result = detect(world, 0, 64, 0);

        assertTrue(result.isSuccess());
        assertEquals(RingOrientation.CEILING, result.getRing().getOrientation());
        assertEquals(67, result.getRing().getAnchorY(), "the plane is where the slabs are");
    }

    @Test
    void thePlayerCanStandAnywhereInsideTheRing()
    {
        // Nobody stands exactly on the centre block. Every interior square has to work as a
        // place to run the command from, and all of them must find the same anchor.
        for (final RingPattern.Offset standing : RingPattern.ODD.getInterior())
        {
            final FakeWorld world = new FakeWorld();
            world.layRing(RingPattern.ODD, 50, 64, 50, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

            final RingTemplate.Result result =
                detect(world, 50 + standing.getDx(), 64, 50 + standing.getDz());

            assertTrue(result.isSuccess(), "standing at " + standing);
            assertEquals(50, result.getRing().getAnchorX(), "standing at " + standing);
            assertEquals(50, result.getRing().getAnchorZ(), "standing at " + standing);
        }
    }

    @Test
    void theEvenPatternIsRecognisedAndAnchoredToACornerOfItsMiddleFour()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.EVEN, 10, 64, 10, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        final RingTemplate.Result result = detect(world, 10, 64, 10);

        assertTrue(result.isSuccess());
        assertEquals(RingPattern.EVEN, result.getRing().getPattern());
        assertEquals(10, result.getRing().getAnchorX());
        assertEquals(10, result.getRing().getAnchorZ());
    }

    @Test
    void barePlainGroundIsNotARing()
    {
        assertFalse(detect(new FakeWorld(), 0, 64, 0).isSuccess());
        assertEquals(RingTemplate.Failure.NO_RING_FOUND, detect(new FakeWorld(), 0, 64, 0).getFailure());
    }

    @Test
    void anIncompleteCircleIsNotARing()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        // Knock one slab out.
        world.put(0, 64, -3, Material.AIR, null);

        assertFalse(detect(world, 0, 64, 0).isSuccess());
    }

    @Test
    void twoKindsOfSlabAreRefusedWithTheReasonWhy()
    {
        // The specific message matters. Reporting "no ring found" for a ring the player can
        // plainly see would send them looking for the wrong problem.
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        world.put(0, 64, -3, Material.DEEPSLATE_TILE_SLAB, RingTemplate.SlabHalf.BOTTOM);

        final RingTemplate.Result result = detect(world, 0, 64, 0);

        assertFalse(result.isSuccess());
        assertEquals(RingTemplate.Failure.MIXED_MATERIALS, result.getFailure());
    }

    @Test
    void slabsFacingDifferentWaysAreRefusedWithTheReasonWhy()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        world.put(0, 64, -3, Material.STONE_SLAB, RingTemplate.SlabHalf.TOP);

        final RingTemplate.Result result = detect(world, 0, 64, 0);

        assertFalse(result.isSuccess());
        assertEquals(RingTemplate.Failure.MIXED_HALVES, result.getFailure());
    }

    @Test
    void aFilledInCircleIsRefusedBecauseARingIsAnOutline()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        for (final RingPattern.Offset offset : RingPattern.ODD.getInterior())
        {
            world.put(offset.getDx(), 64, offset.getDz(), Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        }

        final RingTemplate.Result result = detect(world, 0, 64, 0);

        assertFalse(result.isSuccess());
        assertEquals(RingTemplate.Failure.INTERIOR_NOT_CLEAR, result.getFailure());
    }

    @Test
    void somethingElseInsideTheRingIsFineAsLongAsItIsNotTheRingSlab()
    {
        // Only the ring's own slab is a problem inside the circle. A carpet, a rail or a
        // different slab someone is using as decoration is none of this feature's business.
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 64, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.BOTTOM);
        world.put(0, 64, 0, Material.RED_CARPET, null);

        assertTrue(detect(world, 0, 64, 0).isSuccess());
    }

    @Test
    void aCeilingRingBeyondTheSearchHeightIsNotFound()
    {
        final FakeWorld world = new FakeWorld();
        world.layRing(RingPattern.ODD, 0, 80, 0, Material.STONE_SLAB, RingTemplate.SlabHalf.TOP);

        assertFalse(RingTemplate.detect(world, 0, 64, 0, 5, Material.GLOWSTONE).isSuccess());
    }
}
