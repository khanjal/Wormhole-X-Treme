package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A server's copy of a bundled shape that differs from this version's is named in the log, and
 * left as it is: the plugin cannot tell an old release's file from one an admin edited.
 */
class ShippedShapesTest
{
    @TempDir
    File dir;

    private Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        savedShapes = new HashMap<>(StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
        PluginTestSupport.remove();
    }

    private void write(final String name, final String text) throws Exception
    {
        Files.writeString(new File(dir, name).toPath(), text, StandardCharsets.UTF_8);
    }

    private String read(final String name) throws Exception
    {
        return Files.readString(new File(dir, name).toPath(), StandardCharsets.UTF_8);
    }

    /** Every name on the list is in the jar, or a server would be told nothing about it. */
    @Test
    void everyBundledShapeIsInTheJar()
    {
        for (final String name : ShippedShapes.NAMES)
        {
            assertNotNull(ShippedShapes.bundled(name), name + " should be in the jar");
        }
    }

    /** A differing copy is named when shapes load, and kept exactly as it was. */
    @Test
    void aDifferingShapeIsNamedAndKept() throws Exception
    {
        dir.mkdirs();
        final String older = ShippedShapes.bundled("Large.shape") + "# my notes\n";
        write("Large.shape", older);

        StargateShapeRegistry.loadShapes(dir);

        assertEquals(older, read("Large.shape"));
        verify(WormholeXTreme.getThisPlugin()).prettyLog(eq(Level.INFO), contains("Large.shape differs"));
    }

    /** This version's copy, saved with Windows line endings, is not reported. */
    @Test
    void aCurrentShapeIsNotReported() throws Exception
    {
        write("Standard.shape", ShippedShapes.bundled("Standard.shape").replace("\n", "\r\n"));

        assertEquals(0, ShippedShapes.reportDiffering(dir));
        verify(WormholeXTreme.getThisPlugin(), never()).prettyLog(eq(Level.INFO), anyString());
    }
}
