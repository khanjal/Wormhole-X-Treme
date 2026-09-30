package com.wormhole_xtreme.wormhole.plugin.map;

/**
 * A web map that can show a {@link MapSnapshot}.
 *
 * <p>The seam between working out what to show, which is the same for every map, and drawing
 * it, which is not. Dynmap is the first; another map plugin is another implementation, with no
 * change to {@link MapScanner}.
 *
 * <p>Called from one background thread at a time, never the main thread, except
 * {@link #clear()} while the plugin disables.
 */
public interface MapProvider
{
    /**
     * What this map is called, for the log.
     *
     * @return its name
     */
    String name();

    /**
     * Makes the map show this picture, and nothing else of this plugin's.
     *
     * <p>Must be safe to call again with the same picture, and cheap when nothing changed.
     *
     * @param snapshot
     *            what to show
     */
    void apply(MapSnapshot snapshot);

    /** Removes everything this plugin has drawn. */
    void clear();
}
