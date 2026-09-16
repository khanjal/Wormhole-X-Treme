package com.wormhole_xtreme.wormhole.model.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;
import com.wormhole_xtreme.wormhole.utils.RecordingCreation;

/**
 * What a gate build preview puts in the world, who sees it, and when it goes.
 *
 * <p>Every display here is a real entity on the server. One that is shown to somebody else, saved
 * with the chunk, or left behind when its owner leaves is a block only one player can see the
 * reason for, so each way a preview ends is checked to take its displays with it.
 */
class GatePreviewsTest
{
    /** Standard's frame and chevrons, and the button on its DHD. */
    private static final int STANDARD_BLOCKS = 19;

    /** And its opening, which the server's block limit counts too. */
    private static final int STANDARD_OPENING = 21;

    private WormholeXTreme plugin;
    private World world;
    private Player owner;
    private final List<BlockDisplay> spawned = new ArrayList<>();
    private final List<Interaction> buttons = new ArrayList<>();
    private final Map<Material, BlockData> data = new HashMap<>();
    private Runnable dialStep;
    private BukkitTask dialTask;
    private final long[] now = { 1_000_000L };
    private Stargate3DShape standard;
    private final RecordingCreation creation = new RecordingCreation(type ->
    {
        if (type == Interaction.class)
        {
            final Interaction button = mock(Interaction.class);
            when(button.isValid()).thenReturn(true);
            buttons.add(button);
            return button;
        }
        final BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        spawned.add(display);
        return display;
    });

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        PluginTestSupport.install(plugin);
        ConfigTestSupport.clear();
        standard = new Stargate3DShape(Files.readAllLines(
            Paths.get("src/main/resources/shapes/gate/Standard.shape")).toArray(new String[0]));

        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        final org.bukkit.block.Block air = mock(org.bukkit.block.Block.class);
        when(air.getBlockData()).thenAnswer(inv -> data.computeIfAbsent(Material.AIR, m -> mock(BlockData.class)));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        HiddenEntities.creationWith(creation);

        owner = mock(Player.class);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(owner.getWorld()).thenReturn(world);
        standAt(0.5, 0.5, 180f);

        GatePreviews.clock = () -> now[0];
        GatePreviews.online = id -> owner;
        GatePreviews.blockData = material -> data.computeIfAbsent(material,
            m -> (m == Material.STONE_BUTTON) ? buttonData() : mock(BlockData.class));
        GatePreviews.repeater = (ticks, step) ->
        {
            dialStep = step;
            dialTask = mock(BukkitTask.class);
            return dialTask;
        };
    }

    @AfterEach
    void tearDown() throws Exception
    {
        GatePreviews.clear();
        HiddenEntities.creationWith(null);
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    private static Directional buttonData()
    {
        final Directional data = mock(Directional.class);
        when(data.getFaces()).thenReturn(Set.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST));
        return data;
    }

    /** Puts the owner's feet at a spot at y 64, looking along a yaw, level. */
    private void standAt(final double x, final double z, final float yaw)
    {
        when(owner.getLocation()).thenReturn(new Location(world, x, 64.0, z, yaw, 0f));
        when(owner.getEyeLocation()).thenReturn(new Location(world, x, 65.62, z, yaw, 0f));
    }

    /** Standard, north of whoever looks north from the origin: its DHD in the block in front. */
    private List<Cell> standardLookingNorth()
    {
        return GateBlueprint.of(standard, GateBlueprint.inFrontOf(standard, 0, 64, 0, BlockFace.NORTH));
    }

    /**
     * A preview is one display a block, not saved, hidden from everybody, and shown to its owner.
     *
     * <p>The hiding has to happen before the display is added to the world, or every player in
     * range is sent it first.
     */
    @Test
    void aPreviewIsOneHiddenUnsavedDisplayABlockShownOnlyToItsOwner()
    {
        final Player bystander = mock(Player.class);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        assertEquals(1, buttons.size(), "and the box a click on its button lands on");
        assertEquals(Collections.nCopies(STANDARD_BLOCKS + 1, true), creation.hiddenWhenAdded,
            "each unsaved and hidden by the time it was added");
        for (final BlockDisplay display : spawned)
        {
            final InOrder order = inOrder(display, owner);
            order.verify(display).setBlock(any(BlockData.class));
            order.verify(owner).showEntity(plugin, display);
        }
        verifyNoInteractions(bystander);
    }

    /** Each display stands on a cell of the blueprint, and the button is turned to face the builder. */
    @Test
    void theDisplaysStandOnTheBlueprintWithTheButtonFacingTheBuilder()
    {
        final List<Location> places = creation.places;
        final Directional button = buttonData();
        GatePreviews.blockData = material -> (material == Material.STONE_BUTTON) ? button : mock(BlockData.class);

        GatePreviews.show(owner, standard, null);

        final List<Cell> cells = standardLookingNorth();
        assertEquals(cells.size() + 1, places.size(), "the displays, and the button's box last");
        for (int i = 0; i < cells.size(); i++)
        {
            assertEquals(new Location(world, cells.get(i).x(), cells.get(i).y(), cells.get(i).z()), places.get(i));
        }
        verify(button).setFacing(BlockFace.SOUTH);
    }

    /** A second preview stands beside the first, so every shape can be shown side by side. */
    @Test
    void aSecondPreviewStandsBesideTheFirstRatherThanReplacingIt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.countOf(owner.getUniqueId()));
        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display, never()).remove());
    }

    /** Clearing takes away the preview looked at, and leaves the others standing. */
    @Test
    void clearingTakesAwayOnlyThePreviewLookedAt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> first = new ArrayList<>(spawned.subList(0, STANDARD_BLOCKS));
        final List<BlockDisplay> second = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));

        standAt(40.5, 1.5, 0f); // a step back, out of the preview, looking away
        assertFalse(GatePreviews.clearLookedAt(owner), "looking south, away from both");
        standAt(40.5, 0.5, 180f);
        assertTrue(GatePreviews.clearLookedAt(owner));

        second.forEach(display -> verify(display).remove());
        first.forEach(display -> verify(display, never()).remove());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** clear all takes every preview a player has. */
    @Test
    void clearAllTakesEveryPreview()
    {
        GatePreviews.show(owner, standard, null);
        standAt(40.5, 0.5, 180f);
        GatePreviews.show(owner, standard, null);

        assertEquals(2, GatePreviews.clearAll(owner));

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** Quitting or changing world takes a player's previews with them. */
    @Test
    void forgettingAPlayerTakesTheirPreviews()
    {
        GatePreviews.show(owner, standard, null);

        GatePreviews.forget(owner.getUniqueId());

        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }

    /**
     * A preview times out once its owner stops using build commands, and not while they keep
     * using them.
     */
    @Test
    void aPreviewTimesOutUnlessItsOwnerKeepsUsingBuildCommands()
    {
        GatePreviews.show(owner, standard, null);

        now[0] += 9 * 60_000L;
        standAt(0.5, 1.5, 0f); // a step back, out of the preview, looking away
        GatePreviews.clearLookedAt(owner);
        now[0] += 6 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display, never()).remove());

        now[0] += 5 * 60_000L;
        GatePreviews.tick();
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
    }

    /** A gate found where a preview's button stood takes the preview away; one found elsewhere does not. */
    @Test
    void aGateBuiltWhereThePreviewStoodTakesThePreviewAway()
    {
        GatePreviews.show(owner, standard, null);
        final Cell button = standardLookingNorth().stream().filter(c -> c.part() == Part.BUTTON).findFirst()
            .orElseThrow();

        GatePreviews.builtAt(world, button.x() + 1, button.y(), button.z());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));

        GatePreviews.builtAt(world, button.x(), button.y(), button.z());
        assertEquals(0, GatePreviews.countOf(owner.getUniqueId()));
        spawned.forEach(display -> verify(display).remove());
    }

    /** A preview that would take the server past its block limit is refused, and nothing is spawned. */
    @Test
    void aPreviewPastTheServersBlockLimitIsRefused()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, STANDARD_BLOCKS + STANDARD_OPENING);

        assertEquals(GatePreviews.Shown.SHOWN, GatePreviews.show(owner, standard, null));
        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));

        assertEquals(STANDARD_BLOCKS, spawned.size());
        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()));
    }

    /**
     * Asking for a preview the limit refuses still counts as using build commands, so the ones
     * already standing do not time out on a player who is busy trying.
     */
    @Test
    void aRefusedPreviewStillKeepsTheOthersFromTimingOut()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, STANDARD_BLOCKS + STANDARD_OPENING);
        GatePreviews.show(owner, standard, null);

        now[0] += 9 * 60_000L;
        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));
        now[0] += 9 * 60_000L;
        GatePreviews.tick();

        assertEquals(1, GatePreviews.countOf(owner.getUniqueId()), "renewed nine minutes in, so still up at eighteen");
        spawned.forEach(display -> verify(display, never()).remove());
    }

    /**
     * A display the server dropped with its chunk comes back once the chunk is loaded again, and
     * not before: a preview never loads a chunk.
     */
    @Test
    void aDisplayDroppedWithItsChunkComesBackOnlyOnceTheChunkIsLoaded()
    {
        GatePreviews.show(owner, standard, null);
        final BlockDisplay dropped = spawned.get(0);
        when(dropped.isValid()).thenReturn(false);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS, spawned.size(), "nothing spawned into an unloaded chunk");

        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        GatePreviews.tick();
        assertEquals(STANDARD_BLOCKS + 1, spawned.size(), "the one dropped display, and only that one");
    }

    /** Disabling the plugin takes every preview on the server. */
    @Test
    void disablingTakesEveryPreviewOnTheServer()
    {
        final Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getLocation()).thenReturn(new Location(world, 80.5, 64.0, 0.5, 180f, 0f));
        GatePreviews.show(owner, standard, null);
        GatePreviews.show(other, standard, null);

        GatePreviews.restoreAll();

        assertEquals(2 * STANDARD_BLOCKS, spawned.size());
        spawned.forEach(display -> verify(display).remove());
        assertEquals(0, GatePreviews.blocksShown());
    }

    private List<BlockDisplay> ringDisplaysOfWave(final int wave)
    {
        final List<Cell> cells = standardLookingNorth();
        final List<BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).wave() == wave)
            {
                out.add(spawned.get(i));
            }
        }
        return out;
    }

    private List<BlockDisplay> dhdDisplays()
    {
        final List<Cell> cells = standardLookingNorth();
        final List<BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++)
        {
            if (cells.get(i).dhd())
            {
                out.add(spawned.get(i));
            }
        }
        return out;
    }

    /**
     * -activate lights the chevrons a wave at a time, sends the kawoosh out and back, and leaves the
     * opening filled, as a gate dials.
     *
     * <p>Standard has no chevron material, so a lit chevron is its light material, glowstone. The
     * wormhole is water, which no block display draws, so it is sent to the owner as fake blocks
     * the way a real gate draws it: three woosh steps of 21, 13 and 5 cells out, the same back, then
     * the 21-cell opening.
     */
    @Test
    void activatingLightsTheChevronsThenSendsTheKawooshOutAndBackAndFillsTheOpening()
    {
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Control.DIALLING, GatePreviews.activate(owner));
        assertNotNull(dialStep, "a dial runs on a timer");

        dialStep.run();
        ringDisplaysOfWave(1).forEach(d -> verify(d).setBlock(data.get(Material.GLOWSTONE)));
        ringDisplaysOfWave(2).forEach(d -> verify(d, never()).setBlock(data.get(Material.GLOWSTONE)));
        for (int wave = 2; wave <= 7; wave++)
        {
            dialStep.run();
        }
        ringDisplaysOfWave(7).forEach(d -> verify(d).setBlock(data.get(Material.GLOWSTONE)));
        verify(owner, never()).sendBlockChange(any(Location.class), any(BlockData.class));

        for (int step = 1; step <= 3; step++)
        {
            dialStep.run();
        }
        verify(owner, times(21 + 13 + 5)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        dialStep.run();
        dialStep.run();
        verify(dialTask, never()).cancel();
        dialStep.run();

        verify(owner, times(21 + 13 + 5)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        verify(owner, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));
        verify(dialTask).cancel();
        assertEquals(STANDARD_BLOCKS, spawned.size(), "the wormhole is fake blocks, not displays");
    }

    /** -activate on a gate that is open shuts it down: the chevrons go out and the opening is taken back. */
    @Test
    void activatingAnOpenGateShutsItDown()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }

        assertEquals(GatePreviews.Control.SHUT_DOWN, GatePreviews.activate(owner));

        verify(owner, times((21 + 13 + 5) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
        ringDisplaysOfWave(1).forEach(d -> verify(d, org.mockito.Mockito.atLeast(2)).setBlock(data.get(Material.OBSIDIAN)));
    }

    /** Right-clicking the preview's button dials it; a second click inside the same moment is the same click. */
    @Test
    void rightClickingThePreviewsButtonDialsItOnce()
    {
        GatePreviews.show(owner, standard, null);
        final Interaction button = buttons.get(0);

        assertTrue(GatePreviews.pressed(owner, button));
        assertNotNull(dialStep, "the click dialled it");
        final Runnable first = dialStep;

        now[0] += 100L;
        assertTrue(GatePreviews.pressed(owner, button));
        verify(dialTask, never()).cancel();

        now[0] += 1_000L;
        assertTrue(GatePreviews.pressed(owner, button));
        verify(dialTask).cancel();
        assertEquals(first, dialStep, "shut down, not dialled again");

        assertFalse(GatePreviews.pressed(owner, mock(Interaction.class)), "somebody else's entity is not a button");
    }

    /** -iris closes an iris of the iris material over the opening, and opens it again. */
    @Test
    void theIrisClosesOverTheOpeningAndOpensAgain()
    {
        GatePreviews.show(owner, standard, null);

        assertEquals(GatePreviews.Control.IRIS_CLOSED, GatePreviews.iris(owner));
        final List<BlockDisplay> iris = new ArrayList<>(spawned.subList(STANDARD_BLOCKS, spawned.size()));
        assertEquals(STANDARD_OPENING, iris.size());
        iris.forEach(d -> verify(d).setBlock(data.get(Material.STONE)));

        assertEquals(GatePreviews.Control.IRIS_OPENED, GatePreviews.iris(owner));
        iris.forEach(d -> verify(d).remove());
    }

    /** -material redresses the preview in a group, or changes one role; a non-block changes nothing. */
    @Test
    void materialsChangeByGroupOrByRole()
    {
        GatePreviews.show(owner, standard, null);
        final BlockDisplay frame = spawned.get(0);
        GatePreviews.blockData = material ->
        {
            if (material == Material.DIAMOND)
            {
                throw new IllegalArgumentException("not a block");
            }
            return data.computeIfAbsent(material, m -> mock(BlockData.class));
        };

        assertEquals(GatePreviews.Control.CHANGED, GatePreviews.material(owner,
            new com.wormhole_xtreme.wormhole.model.MaterialGroup("Atlantis", Material.LAPIS_BLOCK, Material.WATER,
                Material.STONE, Material.SEA_LANTERN, Material.OAK_WALL_SIGN)));
        verify(frame).setBlock(data.get(Material.LAPIS_BLOCK));

        assertEquals(GatePreviews.Control.CHANGED,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.GOLD_BLOCK));
        verify(frame).setBlock(data.get(Material.GOLD_BLOCK));

        assertEquals(GatePreviews.Control.NOT_A_BLOCK,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.DIAMOND));
        assertEquals(Material.GOLD_BLOCK, GatePreviews.of(owner.getUniqueId()).get(0).palette().structure());
    }

    /** -dhd hides the DHD and its button for a picture of the ring alone, and shows them again. */
    @Test
    void theDhdHidesForAPictureOfTheRingAndComesBack()
    {
        GatePreviews.show(owner, standard, null);
        final List<BlockDisplay> dhd = dhdDisplays();
        final List<BlockDisplay> ring = ringDisplaysOfWave(1);
        assertEquals(3, dhd.size(), "Standard's DHD: its block, the iris lever's block, and the button");

        assertEquals(GatePreviews.Control.DHD_HIDDEN, GatePreviews.toggleDhd(owner));
        dhd.forEach(d -> verify(d).remove());
        ring.forEach(d -> verify(d, never()).remove());
        verify(buttons.get(0)).remove();

        assertEquals(GatePreviews.Control.DHD_SHOWN, GatePreviews.toggleDhd(owner));
        assertEquals(STANDARD_BLOCKS + dhd.size(), spawned.size(), "the DHD's displays back");
        assertEquals(2, buttons.size(), "and its button's box");
    }

    /** Every control answers that nothing is looked at when the player looks away from their previews. */
    @Test
    void theControlsNeedAPreviewLookedAt()
    {
        GatePreviews.show(owner, standard, null);
        standAt(0.5, 1.5, 0f); // a step back, out of the preview, looking away

        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.activate(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.iris(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING, GatePreviews.toggleDhd(owner));
        assertEquals(GatePreviews.Control.NOT_LOOKING,
            GatePreviews.material(owner, GateBlueprint.Role.FRAME, Material.GOLD_BLOCK));
        assertEquals(STANDARD_BLOCKS, spawned.size());
    }

    /** Clearing a preview part way through dialling stops the dial and takes the button's box too. */
    @Test
    void clearingADiallingPreviewStopsTheDial()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        dialStep.run();

        assertEquals(1, GatePreviews.clearAll(owner));

        verify(dialTask).cancel();
        verify(buttons.get(0)).remove();
    }

    /**
     * -chevrons draws a group's chevron blocks as frame, since they are optional, and lit chevrons
     * then show the light material as a gate built without them does.
     */
    @Test
    void chevronsCanBeDrawnAsTheFrameTheyMayBeBuiltFrom()
    {
        final com.wormhole_xtreme.wormhole.model.MaterialGroup lamps = new com.wormhole_xtreme.wormhole.model.MaterialGroup(
            "Standard", Material.OBSIDIAN, Material.WATER, Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN,
            Material.REDSTONE_LAMP);
        GatePreviews.show(owner, standard, lamps);
        final BlockDisplay chevron = ringDisplaysOfWave(1).get(0);
        verify(chevron).setBlock(data.get(Material.REDSTONE_LAMP));

        assertEquals(GatePreviews.Control.CHEVRONS_PLAIN, GatePreviews.toggleChevrons(owner));
        verify(chevron).setBlock(data.get(Material.OBSIDIAN));

        GatePreviews.activate(owner);
        dialStep.run();
        verify(chevron).setBlock(data.get(Material.GLOWSTONE));

        assertEquals(GatePreviews.Control.CHEVRONS_SHOWN, GatePreviews.toggleChevrons(owner));
        assertEquals(Material.REDSTONE_LAMP, GatePreviews.of(owner.getUniqueId()).get(0).drawnPalette().chevron());
    }

    /** The limit counts a preview's opening too, since dialling or closing the iris fills it. */
    @Test
    void theBlockLimitCountsTheOpening()
    {
        ConfigTestSupport.set(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, (STANDARD_BLOCKS + STANDARD_OPENING) - 1);

        assertEquals(GatePreviews.Shown.OVER_LIMIT, GatePreviews.show(owner, standard, null));
        assertTrue(spawned.isEmpty());
    }

    /** Closing the iris over an open wormhole takes the wormhole back, and clearing the preview takes back the rest. */
    @Test
    void theIrisAndClearingTakeTheWormholeBack()
    {
        GatePreviews.show(owner, standard, null);
        GatePreviews.activate(owner);
        for (int step = 0; step < 13; step++)
        {
            dialStep.run();
        }
        final int takenBackByTheWoosh = 21 + 13 + 5;

        GatePreviews.iris(owner);
        verify(owner, times(takenBackByTheWoosh + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));

        GatePreviews.iris(owner);
        verify(owner, times((takenBackByTheWoosh + 21) + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.WATER)));

        assertEquals(1, GatePreviews.clearAll(owner));
        verify(owner, times(takenBackByTheWoosh + 21 + 21)).sendBlockChange(any(Location.class), eq(data.get(Material.AIR)));
    }
}
