package com.wormhole_xtreme.wormhole.plugin.map;

import de.bluecolored.bluemap.api.BlueMapAPI;

/**
 * Reaches BlueMap's protected lifecycle, as BlueMap itself does when it loads, reloads and stops,
 * so a test can bring a mocked BlueMap up and down. Never instantiated.
 */
abstract class BlueMapLifecycle extends BlueMapAPI
{
    /**
     * BlueMap comes up: every registered {@code onEnable} listener is told.
     *
     * @param api
     *            the mocked BlueMap
     * @throws Exception
     *             if BlueMap's own listeners throw
     */
    static void up(final BlueMapAPI api) throws Exception
    {
        registerInstance(api);
    }

    /**
     * BlueMap goes down: every registered {@code onDisable} listener is told.
     *
     * @param api
     *            the mocked BlueMap
     * @throws Exception
     *             if BlueMap's own listeners throw
     */
    static void down(final BlueMapAPI api) throws Exception
    {
        unregisterInstance(api);
    }
}
