package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

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
 * <p>{@code RegionAccessor.createEntity} arrived in 1.20.2, so it is reached by reflection and
 * these tests never name it: CI compiles them against 1.20 as well.
 */
class HiddenEntitiesTest
{
    private final Plugin plugin = mock(Plugin.class);
    private final Player viewer = mock(Player.class);
    private final BlockDisplay display = mock(BlockDisplay.class);
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
        HiddenEntities.creationWith(null);
    }

    /** Where it can, it hides the entity before the world has it, and only then shows it to the viewer. */
    @Test
    void anEntityIsHiddenBeforeItIsAddedAndThenShownToItsViewer()
    {
        when(display.isValid()).thenReturn(true);
        final RecordingCreation creation = new RecordingCreation(type -> display);
        HiddenEntities.creationWith(creation);

        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> d.setGlowing(true)));

        assertEquals(List.of(true), creation.hiddenWhenAdded);
        final InOrder order = inOrder(display, viewer);
        order.verify(display).setGlowing(true);
        order.verify(viewer).showEntity(plugin, display);
        verify(world, never()).spawn(any(Location.class), eq(BlockDisplay.class));
    }

    /** Where it cannot make one first, it spawns the entity and hides it straight after. */
    @Test
    void withoutMakingOneFirstItSpawnsAndHides()
    {
        when(display.isValid()).thenReturn(true);
        HiddenEntities.creationWith(new RecordingCreation(type -> null));
        when(world.spawn(at, BlockDisplay.class)).thenReturn(display);

        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));

        verify(display).setVisibleByDefault(false);
        verify(display).setPersistent(false);
        verify(viewer).showEntity(plugin, display);
    }

    /** An entity another plugin refused to let into the world is not shown, and nothing is returned. */
    @Test
    void anEntityTheWorldRefusedIsNotShown()
    {
        HiddenEntities.creationWith(new RecordingCreation(type -> display));

        assertNull(HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));

        verify(viewer, never()).showEntity(any(Plugin.class), any(Entity.class));
    }

    /**
     * The lookup finds the server's methods exactly where the API has them, so a wrong parameter
     * list does not quietly send every server down the older path.
     */
    @Test
    void theLookupFindsCreateEntityWhereverTheApiHasIt()
    {
        HiddenEntities.creationWith(null);

        assertEquals(Arrays.stream(World.class.getMethods()).anyMatch(m -> "createEntity".equals(m.getName())),
            HiddenEntities.canCreateBeforeAdding());
    }

    /** And where it has them, it makes the entity with one and adds it with the other. */
    @Test
    void theServersOwnMethodsAreCalledWhereTheyExist() throws Exception
    {
        assumeTrue(HiddenEntities.canCreateBeforeAdding(), "createEntity arrived in 1.20.2");
        final Method create = World.class.getMethod("createEntity", Location.class, Class.class);
        final Method add = World.class.getMethod("addEntity", Entity.class);
        when(display.isValid()).thenReturn(true);
        when(create.invoke(world, at, BlockDisplay.class)).thenReturn(display);

        assertSame(display, HiddenEntities.spawnFor(plugin, viewer, at, BlockDisplay.class, d -> { }));

        add.invoke(verify(world), display);
        verify(world, never()).spawn(any(Location.class), eq(BlockDisplay.class));
    }
}
