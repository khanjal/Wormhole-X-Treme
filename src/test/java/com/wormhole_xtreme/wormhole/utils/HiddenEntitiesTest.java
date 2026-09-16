package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Spawning an entity one player alone sees, on servers with and without {@code createEntity}.
 *
 * <p>{@code RegionAccessor.createEntity} arrived in 1.20.2. On 1.20 and 1.20.1 the call is a
 * {@link NoSuchMethodError} from a jar compiled against 1.20.4, and falling back to a plain spawn
 * is the only way to support them at all.
 */
class HiddenEntitiesTest
{
    private final Plugin plugin = mock(Plugin.class);
    private final Player viewer = mock(Player.class);
    private World world;
    private Location at;

    @BeforeEach
    void setUp()
    {
        world = mock(World.class);
        at = new Location(world, 1, 64, 1);
    }

    @AfterEach
    void tearDown()
    {
        HiddenEntities.reset();
    }

    /** Where it can, it hides the entity before the world has it, and only then shows it to the viewer. */
    @Test
    void anEntityIsHiddenBeforeItIsAddedAndThenShownToItsViewer()
    {
        final BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        when(world.createEntity(at, BlockDisplay.class)).thenReturn(display);
        when(world.addEntity(display)).thenReturn(display);

        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> d.setGlowing(true)));

        final InOrder order = inOrder(display, world, viewer);
        order.verify(display).setPersistent(false);
        order.verify(display).setVisibleByDefault(false);
        order.verify(display).setGlowing(true);
        order.verify(world).addEntity(display);
        order.verify(viewer).showEntity(plugin, display);
        verify(world, never()).spawn(any(Location.class), eq(BlockDisplay.class));
    }

    /**
     * On a server without {@code createEntity} it spawns and hides instead, and stops asking.
     */
    @Test
    void withoutCreateEntityItSpawnsAndHidesAndStopsAsking()
    {
        final BlockDisplay display = mock(BlockDisplay.class);
        when(display.isValid()).thenReturn(true);
        when(world.createEntity(at, BlockDisplay.class)).thenThrow(new NoSuchMethodError("createEntity"));
        when(world.spawn(at, BlockDisplay.class)).thenReturn(display);

        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));
        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));

        verify(world, times(1)).createEntity(at, BlockDisplay.class);
        verify(world, times(2)).spawn(at, BlockDisplay.class);
        verify(display, times(2)).setVisibleByDefault(false);
        verify(display, times(2)).setPersistent(false);
        verify(viewer, times(2)).showEntity(plugin, display);
    }

    /** An entity another plugin refused to let into the world is not shown, and nothing is returned. */
    @Test
    void anEntityTheWorldRefusedIsNotShown()
    {
        final BlockDisplay display = mock(BlockDisplay.class);
        when(world.createEntity(at, BlockDisplay.class)).thenReturn(display);
        when(world.addEntity(display)).thenReturn(display);

        assertNull(HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));

        verify(viewer, never()).showEntity(any(Plugin.class), any(Entity.class));
    }
}
