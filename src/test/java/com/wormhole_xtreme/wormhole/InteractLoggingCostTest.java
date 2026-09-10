package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * What a click costs a server that is not listening.
 *
 * <p>{@code onPlayerInteract} logs what was clicked at FINE. The line was built at the call
 * site -- five method calls and nine joins, including {@code Block.toString()} and
 * {@code World.toString()} -- and handed to {@code prettyLog}, which then decided whether to
 * print it. On a server logging at INFO, which is all of them, every one of those was built and
 * thrown away.
 *
 * <p>{@code PlayerInteractEvent} fires for both buttons on both blocks and air, per player, and
 * left-clicking air repeats for as long as somebody holds the button down. This is the third
 * time this exact fault has turned up in this plugin; {@code logCrossing} and
 * {@code logVehicleEntry} were the first two.
 */
class InteractLoggingCostTest
{
    private WormholeXTreme plugin;
    private World world;
    private Block signBlock;
    private Block clicked;
    private Player clicker;

    @BeforeEach
    void setUp() throws Exception
    {
        plugin = mock(WormholeXTreme.class);
        set("thisPlugin", plugin);

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        clicked = mock(Block.class);
        // Nothing this plugin cares about: not a button, a lever, or a sign, so the handler
        // below has nothing to do and anything touched was touched by the logging.
        when(clicked.getType()).thenReturn(Material.STONE);
        when(clicked.getWorld()).thenReturn(world);
        when(clicked.getLocation()).thenReturn(new Location(world, 1, 64, 1));
        when(clicked.getX()).thenReturn(Integer.valueOf(1));
        when(clicked.getY()).thenReturn(Integer.valueOf(64));
        when(clicked.getZ()).thenReturn(Integer.valueOf(1));

        clicker = mock(Player.class);
        when(clicker.getName()).thenReturn("clicker");
        when(clicker.getUniqueId()).thenReturn(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        if (signBlock != null)
        {
            StargateManager.removeBlockIndex(signBlock);
            signBlock = null;
        }
        set("thisPlugin", null);
    }

    /** Every FINE line the plugin produced, in order. */
    private java.util.List<String> linesLogged()
    {
        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(plugin, org.mockito.Mockito.atLeast(0)).prettyLog(eq(Level.FINE), said.capture());
        return said.getAllValues();
    }

    /**
     * A wall sign belonging to a gate somebody else owns.
     *
     * <p>Owned, and the clicker is not op, so the handler refuses on permission -- which still
     * cancels the click, and reaches that answer without needing a live sign to read.
     */
    private Block aGateSignSomebodyElseOwns()
    {
        signBlock = mock(Block.class);
        when(signBlock.getType()).thenReturn(Material.OAK_WALL_SIGN);
        when(signBlock.getWorld()).thenReturn(world);
        when(signBlock.getLocation()).thenReturn(new Location(world, 2, 64, 2));
        when(signBlock.getX()).thenReturn(Integer.valueOf(2));
        when(signBlock.getY()).thenReturn(Integer.valueOf(64));
        when(signBlock.getZ()).thenReturn(Integer.valueOf(2));

        final Stargate gate = new Stargate();
        gate.setGateName("someone-elses");
        gate.setGateOwner(UUID.randomUUID().toString());
        gate.setGateOwnerName("Somebody");
        StargateManager.addBlockIndex(signBlock, gate);
        return signBlock;
    }

    private static void set(final String name, final Object value) throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private void click(final Block block)
    {
        new WormholeXTremePlayerListener().onPlayerInteract(new PlayerInteractEvent(
            clicker, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP));
    }

    /**
     * A click on a server that is not logging at FINE builds no line.
     *
     * <p>Checked by what the line would have had to read to be built. Neither the player's
     * name nor the block's world is wanted by anything else on this path, so touching either
     * means the message was assembled for a level that was going to discard it.
     */
    @Test
    void aClickCostsNothingWhenNobodyIsListening()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.FALSE);

        click(clicked);

        verify(clicker, never()).getName();
        verify(clicked, never()).getWorld();
    }

    /** Nor does clicking the air, which repeats for as long as the button is held. */
    @Test
    void clickingTheAirCostsNothingEither()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.FALSE);

        new WormholeXTremePlayerListener().onPlayerInteract(new PlayerInteractEvent(
            clicker, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF));

        verify(clicker, never()).getName();
    }

    /**
     * With FINE on, the line still says who clicked what, where.
     *
     * <p>The guard is meant to stop the line being built, not to stop it being useful. Every
     * part of it that was there before is still there.
     */
    @Test
    void withFineOnTheLineStillNamesTheClickerTheBlockAndTheWorld()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.TRUE);

        click(clicked);

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(plugin, atLeastOnce()).prettyLog(eq(Level.FINE), said.capture());
        assertTrue(said.getAllValues().stream().anyMatch(line -> line.contains("clicker")
            && line.contains("STONE") && line.contains("RIGHT_CLICK_BLOCK")
            && line.contains(String.valueOf(world))),
            "the line names who, what, how and where: " + said.getAllValues());
    }

    /**
     * A click on a gate's sign is cancelled, so the sign editor never opens.
     *
     * <p>The one thing this method does beyond logging. Without the cancel, right-clicking a
     * dial sign puts the player into Minecraft's own sign editor on top of whatever the plugin
     * did -- and anything they type there overwrites the destination.
     */
    @Test
    void aClickOnAGateSignIsCancelledSoTheSignEditorNeverOpens()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.FALSE);
        final PlayerInteractEvent event = new PlayerInteractEvent(
            clicker, Action.RIGHT_CLICK_BLOCK, null, aGateSignSomebodyElseOwns(), BlockFace.NORTH);

        new WormholeXTremePlayerListener().onPlayerInteract(event);

        assertTrue(event.isCancelled(), "a registered gate sign swallows the click");
    }

    /** A click on something the plugin does not own is left for everybody else. */
    @Test
    void aClickOnAnOrdinaryBlockIsNotCancelled()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.FALSE);
        final PlayerInteractEvent event = new PlayerInteractEvent(
            clicker, Action.RIGHT_CLICK_BLOCK, null, clicked, BlockFace.UP);

        new WormholeXTremePlayerListener().onPlayerInteract(event);

        assertFalse(event.isCancelled(), "a plain stone block is nothing to do with this plugin");
    }

    /**
     * The three lines are told apart by how they open.
     *
     * <p>A click that was caught and one that was acted on are different events to whoever is
     * reading the log, and a cancelled click is the only evidence the plugin did anything.
     */
    @Test
    void aCancelledClickIsReportedSeparatelyFromCatchingIt()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.TRUE);

        new WormholeXTremePlayerListener().onPlayerInteract(new PlayerInteractEvent(
            clicker, Action.RIGHT_CLICK_BLOCK, null, aGateSignSomebodyElseOwns(), BlockFace.NORTH));

        final java.util.List<String> lines = linesLogged();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Caught Player:")),
            "the click is reported when it arrives: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Cancelled Player:")),
            "and again when the plugin takes it: " + lines);
    }

    /** An air click is never handed to the gate handler, so it is reported once. */
    @Test
    void anAirClickIsReportedOnceAndGoesNoFurther()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.TRUE);

        new WormholeXTremePlayerListener().onPlayerInteract(new PlayerInteractEvent(
            clicker, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF));

        final java.util.List<String> lines = linesLogged();
        assertEquals(1, lines.size(),
            "one line, because there is no block for the gate handler to look at");
        // The opening, not just the count: a click that fell through to the block path would
        // still produce one line, and would say it had been caught rather than ignored.
        assertTrue(lines.get(0).startsWith("Caught and ignored Player:"),
            "an air click is reported as ignored: " + lines);
    }

    /** A click before the plugin is up is ignored rather than thrown. */
    @Test
    void aClickBeforeThePluginIsUpDoesNotThrow() throws Exception
    {
        set("thisPlugin", null);

        assertDoesNotThrow(() -> click(clicked));
    }

    /** And an air click still says who did it and what they did. */
    @Test
    void withFineOnAnAirClickIsStillReported()
    {
        when(plugin.isLoggable(Level.FINE)).thenReturn(Boolean.TRUE);

        new WormholeXTremePlayerListener().onPlayerInteract(new PlayerInteractEvent(
            clicker, Action.LEFT_CLICK_AIR, null, null, BlockFace.SELF));

        final ArgumentCaptor<String> said = ArgumentCaptor.forClass(String.class);
        verify(plugin, atLeastOnce()).prettyLog(eq(Level.FINE), said.capture());
        assertTrue(said.getAllValues().stream().anyMatch(line -> line.contains("clicker")
            && line.contains("LEFT_CLICK_AIR")),
            "an ignored click still says who and what: " + said.getAllValues());
    }
}
