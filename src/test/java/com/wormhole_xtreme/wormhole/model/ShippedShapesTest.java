package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A server's copy of a bundled shape is brought up to date only if nobody edited it.
 *
 * <p>Shapes were written out once and never touched again, so a server that upgraded kept the
 * old geometry and light order however many releases went by.
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

    private static String oldLarge() throws Exception
    {
        try (InputStream is = ShippedShapesTest.class.getResourceAsStream("/shapes/old/Large-1.6.0.shape"))
        {
            assertNotNull(is, "the 1.6.0 Large fixture should be on the test classpath");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String read(final String name) throws Exception
    {
        return Files.readString(new File(dir, name).toPath(), StandardCharsets.UTF_8);
    }

    /**
     * Every bundled shape as it ships now is on the list, so the version after this one still
     * recognises it as untouched. Changing a shape without regenerating the list fails here.
     */
    @Test
    void everyBundledShapeIsOnTheShippedList()
    {
        final Set<String> shipped = ShippedShapes.shippedVersions();
        for (final String name : ShippedShapes.NAMES)
        {
            final String bundled = ShippedShapes.bundled(name);
            assertNotNull(bundled, name + " should be in the jar");
            assertTrue(shipped.contains(name + " " + ShippedShapes.hash(bundled)),
                name + " as it ships now is missing from shipped-shapes.txt");
        }
    }

    /** 1.6.0's Large, saved with Windows line endings, is replaced on load and kept beside it. */
    @Test
    void anUntouchedOlderShapeIsReplacedAndKept() throws Exception
    {
        final String old = oldLarge().replace("\r\n", "\n").replace("\n", "\r\n");
        dir.mkdirs();
        Files.writeString(new File(dir, "Large.shape").toPath(), old, StandardCharsets.UTF_8);

        StargateShapeRegistry.loadShapes(dir);

        assertEquals(ShippedShapes.bundled("Large.shape"), read("Large.shape"));
        assertEquals(old, read("Large.shape" + ShippedShapes.BACKUP_SUFFIX));
    }

    /** A line of notes makes it somebody's shape, and it is left alone. */
    @Test
    void anEditedShapeIsLeftAsItIs() throws Exception
    {
        final String edited = oldLarge() + "# my notes\n";
        Files.writeString(new File(dir, "Large.shape").toPath(), edited, StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        assertEquals(edited, read("Large.shape"));
        assertFalse(new File(dir, "Large.shape" + ShippedShapes.BACKUP_SUFFIX).exists());
    }

    /** A copy already matching this version is not rewritten, and gets no backup. */
    @Test
    void aCurrentShapeIsNotTouched() throws Exception
    {
        final String current = ShippedShapes.bundled("Standard.shape");
        Files.writeString(new File(dir, "Standard.shape").toPath(), current, StandardCharsets.UTF_8);

        ShippedShapes.updateUntouched(dir);

        assertEquals(current, read("Standard.shape"));
        assertFalse(new File(dir, "Standard.shape" + ShippedShapes.BACKUP_SUFFIX).exists());
    }
}
