package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.block.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * {@link Windows#clear()} puts back everything {@link WindowStore} holds (#430).
 *
 * <p>The state used to sit in {@code Windows} beside the method that resets it. With the clusters
 * of that class moving out one by one, a field added to the store and forgotten in {@code clear()}
 * would carry one test's windows into the next -- and show up as a failure somewhere else, the
 * next time the suite ran in a different order.
 */
class WindowStoreTest
{
    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        Windows.clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        Windows.clear();
        PluginTestSupport.remove();
    }

    /** Every table and counter in the store is empty or zero again after a clear. */
    @Test
    void clearPutsBackEverythingTheStoreHolds()
    {
        final UUID player = UUID.randomUUID();
        WindowStore.changes = 7L;
        WindowStore.CHANGED.put("museum", 7L);
        WindowStore.FIRST_CHANGED.put("museum", 3L);
        WindowStore.workSecond = 12L;
        WindowStore.workSpent = 99;
        WindowStore.sweepRoomBlocks = 5L;
        WindowStore.sweeps = 4L;
        WindowStore.ACTIVE.put("museum", mock(WindowState.class));
        WindowStore.OFFERED.put("museum", mock(WindowState.class));
        WindowStore.VIEWS.put(player, mock(ViewerDrawing.class));
        WindowStore.STATES.put("world", new HashMap<Long, BlockState>());
        WindowStore.BLIND.add(player);
        WindowStore.FULL.put(player, "museum");

        Windows.clear();

        assertEquals(0L, WindowStore.changes);
        assertTrue(WindowStore.CHANGED.isEmpty(), "changed");
        assertTrue(WindowStore.FIRST_CHANGED.isEmpty(), "first changed");
        assertEquals(0L, WindowStore.workSecond);
        assertEquals(0, WindowStore.workSpent);
        assertEquals(0L, WindowStore.sweepRoomBlocks);
        assertEquals(0L, WindowStore.sweeps);
        assertTrue(WindowStore.ACTIVE.isEmpty(), "active");
        assertTrue(WindowStore.OFFERED.isEmpty(), "offered");
        assertTrue(WindowStore.VIEWS.isEmpty(), "views");
        assertTrue(WindowStore.STATES.isEmpty(), "states");
        assertTrue(WindowStore.BLIND.isEmpty(), "blind");
        assertTrue(WindowStore.FULL.isEmpty(), "full");
    }
}
