package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.freya.FreyaCompanion;
import com.wormhole_xtreme.wormhole.model.window.WindowShape.Spot;

/**
 * Which creatures in a window's far room a viewer is shown, and where their stand-ins stand (#296).
 *
 * <p>The mistakes this guards against all still look like something: a creature drawn half a block
 * off the floor it stands on, facing the wrong way through a turned window, a stand-in of a
 * stand-in, a far chunk loaded just to see whether anybody was in it, or a mob farm on the far
 * side flooding the viewer's world.
 */
class FarCreaturesTest
{
    private World here;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        FreyaCompanion.forgetAll();
        here = mock(World.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        FreyaCompanion.forgetAll();
        PluginTestSupport.remove();
    }

    /** Where these windows go: the arrival block is (100, 70, -21). */
    private static Place far(final float yaw)
    {
        return new Place("far", 100.5, 70.0, -20.5, yaw, 0.0f);
    }

    /** A banner at the origin facing north, so its opening is the column at z 1. */
    private static WindowShape window(final float farYaw, final boolean reflection)
    {
        return WindowShape.of(new BlockPlace("world", 0, 64, 0), BlockFace.NORTH, far(farYaw), reflection);
    }

    /**
     * A creature anywhere inside a far block stands inside the block that shows it, through every
     * turn and through a reflection.
     *
     * <p>Mapped from the block's corner rather than its middle, a turned window moved every creature
     * up to a block sideways, onto the block beside the one it stands on.
     */
    @Test
    void aCreatureStandsInsideTheBlockThatShowsWhereItStands()
    {
        for (final boolean reflection : new boolean[] { false, true })
        {
            for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, -90.0f })
            {
                everyPointOfABlockStandsInItsBlock(window(yaw, reflection), "yaw " + yaw + (reflection ? ", reflected" : ""));
            }
        }
    }

    private static void everyPointOfABlockStandsInItsBlock(final WindowShape shape, final String which)
    {
        final double[] inside = { 0.05, 0.3, 0.5, 0.95 };
        final Spot block = shape.hereOf(103, 70, -18);
        for (final double across : inside)
        {
            for (final double along : inside)
            {
                final double[] at = shape.hereOf(103 + across, 70.25, -18 + along);
                assertEquals(block, new Spot((int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2])),
                    "a creature at +" + across + ", +" + along + " in its block, " + which
                        + ", should stand in the block drawn for it");
                assertEquals(63.25, at[1], 1.0e-9, "and as high above that block's floor as it is above its own");
            }
        }
    }

    /**
     * A creature walking away from the far opening walks away from the viewer, however the window
     * turns; a reflection swaps its left and right, as it does the blocks.
     */
    @Test
    void aCreatureFacesTheWayTheWindowTurnsOrFlipsIt()
    {
        // The far side faces west: a creature facing west faces away from its opening, so here it
        // faces into the wall, south, away from the viewer.
        assertEquals(0.0f, window(90.0f, false).hereYaw(90.0f), 1.0e-3f, "walking away from the far opening");
        assertEquals(180.0f, Math.abs(window(90.0f, false).hereYaw(-90.0f)), 1.0e-3f, "walking towards it");
        // Not turned: a creature facing east through the window faces east here too, and in a
        // reflection of the same room it faces west.
        assertEquals(-90.0f, window(0.0f, false).hereYaw(-90.0f), 1.0e-3f, "east through a window onto a room facing the same way");
        assertEquals(90.0f, window(0.0f, true).hereYaw(-90.0f), 1.0e-3f, "east, reflected, is west");
    }

    /** A stand-in goes in the viewer's world, where the window shows its creature, facing as it is shown. */
    @Test
    void aStandInStandsAndFacesWhereTheWindowShowsItsCreature()
    {
        final World far = mock(World.class);
        final Location at = FarCreatures.hereOf(here, window(90.0f, false), new Location(far, 100.5, 70.0, -20.5, 90.0f, 12.0f));

        assertEquals(here, at.getWorld(), "in the viewer's world, not the far one");
        assertEquals(0.5, at.getX(), 1.0e-9);
        assertEquals(63.0, at.getY(), 1.0e-9, "on the floor of the layer behind the opening");
        assertEquals(2.5, at.getZ(), 1.0e-9, "in the middle of the block behind the opening's bottom");
        assertEquals(0.0f, at.getYaw(), 1.0e-3f, "facing away from the viewer, as it faces away from the far opening");
        assertEquals(12.0f, at.getPitch(), 1.0e-3f, "looking as far up or down as it does");
    }

    /**
     * The far room looked in is exactly what a view this deep could show: from the arrival block's
     * layer to the depth, and as far each side and up and down.
     */
    @Test
    void theFarBoxIsWhatAViewThisDeepCouldShow()
    {
        // Facing south at the far side: depth runs south from z -21, and right through the
        // opening runs west, down x.
        assertArrayEquals(new int[] { 96, 66, -21, 104, 75, -18 }, window(0.0f, false).farBox(4),
            "four deep from the arrival block, four either side of a one-wide opening, and four over its two rows");
    }

    /** The room is looked in only where the capture has it, and not at all where the two miss. */
    @Test
    void theRoomLookedInIsTheViewInsideTheCapture()
    {
        final WindowShape shape = window(0.0f, false);
        assertArrayEquals(new int[] { 98, 66, -20, 104, 72, -18 },
            FarCreatures.roomBox(shape, 4, new int[] { 98, 0, -20, 200, 72, 0 }), "cut to the capture");
        assertEquals(0, FarCreatures.roomBox(shape, 4, new int[] { 200, 0, 0, 300, 100, 100 }).length,
            "a capture somewhere else gives nothing to look in");
    }

    /**
     * Only chunks the server already has, with their entities, are read; nothing is loaded to
     * find out who is there.
     *
     * <p>Asking a chunk that is not loaded for its entities loads it, and since 1.17 asking a
     * loaded chunk whose entities have not arrived loads those. A mirror nobody on the far side
     * is near must not be what keeps that side resident.
     */
    @Test
    void creaturesAreReadOnlyFromChunksAlreadyLoadedWithTheirEntities()
    {
        final World far = mock(World.class);
        // Chunks (5..6, -3..-2). Only (6, -2) is loaded with its entities; (5, -2) is loaded but
        // its entities are not in yet; the rest are not loaded at all.
        final Chunk ready = mock(Chunk.class);
        when(ready.isEntitiesLoaded()).thenReturn(true);
        final Chunk arriving = mock(Chunk.class);
        when(far.isChunkLoaded(6, -2)).thenReturn(true);
        when(far.isChunkLoaded(5, -2)).thenReturn(true);
        when(far.getChunkAt(6, -2)).thenReturn(ready);
        when(far.getChunkAt(5, -2)).thenReturn(arriving);
        final Entity zombie = creature(Zombie.class, far, 100.5, 70.0, -20.5);
        final Entity early = creature(Cow.class, far, 90.5, 70.0, -20.5);
        when(far.getNearbyEntities(any(BoundingBox.class))).thenReturn(List.of(zombie, early));

        final List<Entity> found = FarCreatures.inRoom(far, new int[] { 90, 66, -40, 104, 75, -18 });

        assertEquals(List.of(zombie), found, "the zombie in the loaded chunk, and not the cow in the one still loading");
        verify(far, never()).loadChunk(anyInt(), anyInt());
        verify(far, never()).loadChunk(anyInt(), anyInt(), any(Boolean.class));
        for (final Invocation call : mockingDetails(far).getInvocations())
        {
            if (call.getMethod().getName().equals("getChunkAt"))
            {
                final int x = call.getArgument(0);
                final int z = call.getArgument(1);
                assertEquals(-2, z, "getChunkAt(" + x + ", " + z + ") would load a chunk that is not loaded");
            }
            assertNotEquals("getEntities", call.getMethod().getName(), "a whole world's entities were read");
        }
    }

    /** With nothing loaded on the far side, the world is not even asked who is there. */
    @Test
    void aFarSideWithNothingLoadedIsNeverAskedForCreatures()
    {
        final World far = mock(World.class);
        final World loaded = mock(World.class);
        final Chunk chunk = mock(Chunk.class);
        when(chunk.isEntitiesLoaded()).thenReturn(true);
        when(loaded.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(loaded.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);

        assertEquals(List.of(), FarCreatures.inRoom(far, new int[] { 90, 66, -40, 104, 75, -18 }));
        assertEquals(List.of(), FarCreatures.inRoom(loaded, new int[] { 90, 66, -40, 104, 75, -18 }));

        verify(far, never()).getNearbyEntities(any(BoundingBox.class));
        verify(far, never()).getChunkAt(anyInt(), anyInt());
        // The same box, loaded: asked once. Without this the never above holds for a method that never asks.
        verify(loaded).getNearbyEntities(any(BoundingBox.class));
    }

    /**
     * Mobs are copied; players, armour stands, the invisible, things that are not creatures, the
     * companion and the stand-ins themselves are not.
     *
     * <p>A stand-in copied would be copied again by the next viewer's view, one more each sweep.
     */
    @Test
    void onlyOrdinaryVisibleMobsAreCopied()
    {
        final World far = mock(World.class);
        assertTrue(FarCreatures.copied(creature(Zombie.class, far, 0, 0, 0)), "a zombie is the case this is for");
        assertFalse(FarCreatures.copied(creature(Player.class, far, 0, 0, 0)), "players are a later step");
        assertFalse(FarCreatures.copied(creature(ArmorStand.class, far, 0, 0, 0)), "an armour stand is scenery");
        final Zombie hidden = creature(Zombie.class, far, 0, 0, 0);
        when(hidden.isInvisible()).thenReturn(true);
        assertFalse(FarCreatures.copied(hidden), "a stand-in of an invisible mob would show what cannot be seen");
        final Zombie dead = creature(Zombie.class, far, 0, 0, 0);
        when(dead.isValid()).thenReturn(false);
        assertFalse(FarCreatures.copied(dead), "nor one that has died or gone");
        final Item item = mock(Item.class);
        when(item.isValid()).thenReturn(true);
        assertFalse(FarCreatures.copied(item), "a dropped item is not a creature");

        final Zombie standIn = creature(Zombie.class, far, 0, 0, 0);
        StandIns.track(standIn);
        try
        {
            assertFalse(FarCreatures.copied(standIn), "a stand-in is never copied again");
        }
        finally
        {
            StandIns.untrack(standIn);
        }
        assertTrue(FarCreatures.copied(standIn), "once it is no stand-in, the same zombie is copied");
    }

    /** The companion is somebody's own, shown only to them; she is never copied for a stranger. */
    @Test
    void theCompanionIsNeverCopied()
    {
        final World world = mock(World.class);
        final Cat freya = creature(Cat.class, world, 0, 64, 0);
        when(world.spawn(any(Location.class), eq(Cat.class))).thenReturn(freya);
        final Player owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));
        assertTrue(FarCreatures.copied(freya), "an ordinary cat is copied");

        FreyaCompanion.spawnFor(owner);

        assertFalse(FarCreatures.copied(freya), "the companion is not");
    }

    /**
     * At most twenty, the nearest the eye, each creature once even when two windows show it.
     *
     * <p>A mob farm on the far side is hundreds of creatures, and each stand-in is a real entity in
     * the viewer's world.
     */
    @Test
    void theNearestTwentyAreShownEachCreatureOnce()
    {
        final Location eye = new Location(here, 0.5, 65.0, -3.0);
        final List<FarCreatures.Wanted> candidates = new ArrayList<>();
        final List<Entity> originals = new ArrayList<>();
        // 25 creatures, given out furthest first, the nearest at 1 block and the furthest at 25.
        for (int i = 25; i >= 1; i--)
        {
            final Entity original = creature(Zombie.class, here, 0, 0, 0);
            originals.add(0, original);
            candidates.add(new FarCreatures.Wanted(original, new Location(here, 0.5, 65.0, -3.0 + i), null));
        }
        // The nearest creature again, through another window, nearer than the second.
        candidates.add(new FarCreatures.Wanted(originals.get(0), new Location(here, 0.5, 65.0, -1.5), null));

        final List<FarCreatures.Wanted> chosen = FarCreatures.nearest(candidates, eye, FarCreatures.MOST_PER_VIEWER);

        assertEquals(20, chosen.size(), "capped at twenty");
        for (int i = 0; i < 20; i++)
        {
            assertEquals(originals.get(i), chosen.get(i).original(), "the " + (i + 1) + "th nearest, in order");
        }
        assertEquals(-2.0, chosen.get(0).here().getZ(), 1.0e-9, "the nearest creature where it is nearest, not where it is also seen");
    }

    /**
     * The costly view test is asked nearest first and not at all once twenty are chosen, and the
     * answer is the one testing every creature would give.
     *
     * <p>A mob farm in the far room is hundreds of creatures, and a view test projects each onto the
     * opening: asked of all of them it cost a redraw that many projections for twenty stand-ins.
     */
    @Test
    void theViewTestStopsAtTheCapWithTheSameAnswerAsTestingEveryCreature()
    {
        final Location eye = new Location(here, 0.5, 65.0, -3.0);
        final List<FarCreatures.Wanted> candidates = new ArrayList<>();
        for (int i = 1; i <= 200; i++)
        {
            candidates.add(new FarCreatures.Wanted(creature(Zombie.class, here, 0, 0, 0), new Location(here, 0.5, 65.0, -3.0 + i), null));
        }
        // Every third out of view.
        final List<FarCreatures.Wanted> asked = new ArrayList<>();
        final List<FarCreatures.Wanted> chosen = FarCreatures.choose(candidates, eye, FarCreatures.MOST_PER_VIEWER, wanted ->
        {
            asked.add(wanted);
            return (candidates.indexOf(wanted) % 3) != 2;
        });

        final List<FarCreatures.Wanted> everyInView = new ArrayList<>();
        for (final FarCreatures.Wanted wanted : candidates)
        {
            if ((candidates.indexOf(wanted) % 3) != 2)
            {
                everyInView.add(wanted);
            }
        }
        assertEquals(FarCreatures.nearest(everyInView, eye, FarCreatures.MOST_PER_VIEWER), chosen,
            "the nearest twenty in view, as testing all two hundred would give");
        assertEquals(29, asked.size(), "asked only of the twenty-nine nearest it took to find twenty in view, not all 200");
    }

    /**
     * A creature already shown is kept by the looser test, and a new one needs the stricter, so one at
     * the edge of the view is not spawned and removed on every step across it.
     */
    @Test
    void aShownCreatureIsKeptByTheLooserTestAndANewOneNeedsTheStricter()
    {
        final Entity shown = creature(Zombie.class, here, 0, 0, 0);
        final Entity fresh = creature(Zombie.class, here, 0, 0, 0);
        final FarCreatures.Wanted shownOne = new FarCreatures.Wanted(shown, new Location(here, 0, 0, 0), null);
        final FarCreatures.Wanted freshOne = new FarCreatures.Wanted(fresh, new Location(here, 0, 0, 0), null);
        final Predicate<FarCreatures.Wanted> test =
            FarCreatures.keepOrShow(Set.of(shown.getUniqueId()), wanted -> true, wanted -> false);

        assertTrue(test.test(shownOne), "kept while still seen at all");
        assertFalse(test.test(freshOne), "not spawned until properly in view");
        assertTrue(FarCreatures.keepOrShow(Set.of(), wanted -> false, wanted -> true).test(shownOne),
            "a creature with no stand-in is judged by the stricter test alone");
    }

    /**
     * Nothing another plugin hides by default is copied, nor a boss whose copy would bring its bar or
     * its parts; the same zombie, visible, is.
     */
    @Test
    void hiddenCreaturesAndBossesAreNotCopied()
    {
        final Zombie hidden = creature(Zombie.class, here, 0, 0, 0);
        assertTrue(FarCreatures.copied(hidden), "visible, it is copied");
        when(hidden.isVisibleByDefault()).thenReturn(false);
        assertFalse(FarCreatures.copied(hidden), "hidden by default, as a preview's or another plugin's creature is");
        assertFalse(FarCreatures.copied(creature(EnderDragon.class, here, 0, 0, 0)), "the dragon has parts and a bar");
        assertFalse(FarCreatures.copied(creature(Wither.class, here, 0, 0, 0)), "the wither has a bar");
    }

    /**
     * A snow golem and a shulker are not copied, though another golem is: with no AI at all, the
     * golem still lays snow in the viewer's world and the shulker still teleports itself into it.
     */
    @Test
    void aSnowGolemAndAShulkerAreNotCopiedThoughAnIronGolemIs()
    {
        assertTrue(FarCreatures.copied(creature(IronGolem.class, here, 0, 0, 0)), "an iron golem is copied");
        assertFalse(FarCreatures.copied(creature(Snowman.class, here, 0, 0, 0)), "a snow golem lays snow");
        assertFalse(FarCreatures.copied(creature(Shulker.class, here, 0, 0, 0)), "a shulker teleports itself");
    }

    /**
     * A creature standing on a block's top face stands on the top face of the block drawn for it,
     * for every way a banner faces, every way the far side faces, a reflection, a gate's opening,
     * and an opening lower or higher than the far side's floor.
     *
     * <p>Asked after stand-ins were seen on air in game (#296): the creature's mapping and the
     * blocks' mapping must agree to the block, and to the fraction.
     */
    @Test
    void aCreatureOnABlocksTopStandsOnTheTopOfTheBlockDrawnForIt()
    {
        final List<WindowShape> shapes = new ArrayList<>();
        for (final BlockFace facing : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST })
        {
            for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, -90.0f })
            {
                for (final int bannerY : new int[] { 40, 64, 90 })
                {
                    for (final boolean reflection : new boolean[] { false, true })
                    {
                        shapes.add(WindowShape.of(new BlockPlace("world", 3, bannerY, -7), facing, far(yaw), reflection, 2));
                    }
                }
            }
        }
        for (final Spot into : new Spot[] { new Spot(0, 0, -1), new Spot(1, 0, 0), new Spot(0, 0, 1), new Spot(-1, 0, 0) })
        {
            for (final float yaw : new float[] { 0.0f, 90.0f, 180.0f, -90.0f })
            {
                // A gate whose arrival is above its pad, as a traveller is set down: 70.6.
                shapes.add(WindowShape.through(new Spot(10, 64, 20), into, new Place("far", 100.5, 70.6, -20.5, yaw, 0.0f), 5, 5));
            }
        }
        for (final WindowShape shape : shapes)
        {
            feetOnTheDrawnBlock(shape);
        }
        assertEquals(112, shapes.size(), "every case was tried");
    }

    private static void feetOnTheDrawnBlock(final WindowShape shape)
    {
        // Standing on (104, 69, -17), its feet at y 70, anywhere over the block, and on a slab's top at 70.5.
        for (final double feet : new double[] { 70.0, 70.5 })
        {
            final int under = (feet == 70.0) ? 69 : 70;
            final Spot floor = shape.hereOf(104, under, -17);
            for (final double across : new double[] { 0.01, 0.5, 0.99 })
            {
                final double[] at = shape.hereOf(104 + across, feet, -17 + (1.0 - across));
                assertEquals(floor.y() + (feet - under), at[1], 1.0e-9, "feet on the drawn floor's top, for " + shape);
                assertEquals(floor.x(), (int) Math.floor(at[0]), "over the drawn floor block, for " + shape);
                assertEquals(floor.z(), (int) Math.floor(at[2]), "over the drawn floor block, for " + shape);
            }
        }
    }

    private static <T extends Entity> T creature(final Class<T> type, final World world, final double x, final double y,
        final double z)
    {
        final T entity = mock(type);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        when(entity.isVisibleByDefault()).thenReturn(true);
        when(entity.getLocation()).thenReturn(new Location(world, x, y, z));
        return entity;
    }
}
