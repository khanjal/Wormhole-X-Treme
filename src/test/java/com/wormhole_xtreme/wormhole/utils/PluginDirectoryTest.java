package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Every file this plugin owns is found relative to the folder the server names.
 *
 * <p>Half the stores used to build {@code "plugins" + separator + "WormholeXTreme"} instead,
 * which is not the plugin folder -- it is whatever directory the JVM happened to start in.
 * The two agree on a stock install and stop agreeing the moment a start script changes
 * directory or a launcher points its plugins folder somewhere else.
 *
 * <p>What made that worth fixing is that the disagreement is silent. Nothing throws: gates
 * load from the real folder and name the shapes they were built from, shapes load from a
 * folder that is empty, so eleven fresh defaults get written into the wrong tree and every
 * gate built from a custom shape stops being detectable. config.yml splits the same way and
 * a server quietly reverts to default settings with a perfectly good file sitting unread.
 *
 * <p>So these tests are about one thing: when the server says the plugin folder is somewhere,
 * that is where the path goes.
 */
class PluginDirectoryTest
{
    @AfterEach
    void tearDown() throws Exception
    {
        PluginTestSupport.remove();
    }

    /**
     * The bug itself. Before the fix this returned a path under {@code plugins/}, wherever
     * the JVM started, no matter what the server said.
     */
    @Test
    void aPathLandsUnderTheFolderTheServerNamesNotTheWorkingDirectory(@TempDir final File dataFolder)
        throws Exception
    {
        installPluginWithDataFolder(dataFolder);

        assertEquals(new File(dataFolder, "GateShapes"),
            PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "GateShapes"),
            "a store that ignores getDataFolder() reads from a different tree than the "
            + "gates that reference it, and neither half reports a problem");
    }

    /** Several segments descend one directory each, rather than being pasted together. */
    @Test
    void eachSegmentIsOneDirectoryDeeper(@TempDir final File dataFolder) throws Exception
    {
        installPluginWithDataFolder(dataFolder);

        assertEquals(new File(new File(dataFolder, "WormholeXTremeDB"), "gates"),
            PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER, "WormholeXTremeDB", "gates"),
            "segments joined by hand would carry one platform's separator into the other's paths");
    }

    /** No segments at all asks for the plugin folder itself, which is what config.yml wants. */
    @Test
    void noSegmentsIsThePluginFolderItself(@TempDir final File dataFolder) throws Exception
    {
        installPluginWithDataFolder(dataFolder);

        assertEquals(dataFolder, PluginDirectory.resolve(PluginDirectory.PLUGIN_FOLDER),
            "config.yml sits directly in the plugin folder, not in a subdirectory of it");
    }

    /**
     * With no plugin to ask, the relative path is what comes back.
     *
     * <p>This is the branch the tests run on and the branch that fires if anything ever
     * resolves a path before the plugin exists. It is a fallback, not an alternative: a live
     * server never reaches it.
     */
    @Test
    void withNoPluginToAskThePathFallsBackToTheNameItWasGiven() throws Exception
    {
        PluginTestSupport.install(null);

        assertEquals(new File("plugins" + File.separator + "Frobnicate", "GateShapes"),
            PluginDirectory.resolve("Frobnicate", "GateShapes"),
            "the fallback has to honour the name it was handed, or a test pointing somewhere "
            + "harmless would write into the real plugin folder instead");
    }

    /**
     * Installs a plugin whose data folder is a temporary directory.
     *
     * <p>{@code getDataFolder()} is {@code final} on {@code JavaPlugin} and would normally be
     * unstubbable -- this works only because the suite runs on Mockito's inline mock maker.
     */
    private static void installPluginWithDataFolder(final File dataFolder) throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        PluginTestSupport.install(plugin);
    }
}
