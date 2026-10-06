package com.wormhole_xtreme.wormhole.plugin.map;

/**
 * A web map that can show a {@link MapSnapshot}.
 *
 * <p>The seam between working out what to show, which is the same for every map, and drawing
 * it, which is not. Each map plugin is one implementation, with no change to {@link MapScanner}.
 *
 * <p>{@link #apply} is called from one background thread at a time, never the main thread.
 * {@link #register()}, {@link #unregister()} and {@link #clear()} are called on the main thread,
 * the last while the plugin disables; {@link #name()} and {@link #ready()} from either, so they
 * only read.
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
     * Whether the map is up and can be drawn on. While it is not, nothing is looked at or drawn.
     *
     * @return true once the map plugin has handed over what drawing needs
     */
    boolean ready();

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

    /**
     * Starts listening to the map plugin, once the provider is in place. May call back at once
     * when the map is already up.
     */
    default void register()
    {
        // A map with nothing to listen to needs nothing here.
    }

    /** Stops listening to the map plugin. Must be safe after a failed or missing register. */
    default void unregister()
    {
        // Nothing was registered by default.
    }
}
