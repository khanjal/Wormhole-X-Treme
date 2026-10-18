package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * The switch that turns a gate shape off for new gates.
 *
 * <p>It lives in {@code disabled-shapes.txt} beside the shape files, not in them: an update
 * replaces an unedited shipped shape with the new version, and a flag written into the file would
 * either be lost there or make the file count as edited and never update again.
 */
class ShapeEnabledFlagTest
{
    @TempDir
    File tempDir;

    private File shapes;
    private Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        PluginTestSupport.install(plugin);
        shapes = new File(tempDir, "shapes" + File.separator + "gate");
        assertTrue(shapes.mkdirs());
        savedShapes = new HashMap<>(StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().clear();
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateShapeRegistry.readDisabled(new File(tempDir, "none"));
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
        PluginTestSupport.remove();
    }

    @Test
    void everyShapeIsOnUntilSomeoneTurnsItOff()
    {
        assertTrue(StargateShapeRegistry.isEnabled("Massive"));
    }

    @Test
    void aShapeTurnedOffIsOffWhateverTheCapitalsAndOnAgainWhenEnabled()
    {
        assertTrue(StargateShapeRegistry.setEnabled("Massive", false));

        assertFalse(StargateShapeRegistry.isEnabled("massive"));
        assertFalse(StargateShapeRegistry.isEnabled("MASSIVE"));
        assertTrue(StargateShapeRegistry.isEnabled("Standard"));

        assertTrue(StargateShapeRegistry.setEnabled("Massive", true));
        assertTrue(StargateShapeRegistry.isEnabled("Massive"));
    }

    @Test
    void theChoiceIsWrittenBesideTheShapeFilesNotIntoThem() throws Exception
    {
        StargateShapeRegistry.setEnabled("Massive", false);

        final List<String> lines = Files.readAllLines(new File(shapes, "disabled-shapes.txt").toPath(),
            StandardCharsets.UTF_8);
        assertTrue(lines.contains("massive"), "got " + lines);
    }

    /** A reload clears the registry and reads the folder again; the switch has to be read with it. */
    @Test
    void aShapeStaysOffAfterShapesReloadAndStillLoads()
    {
        StargateShapeRegistry.loadShapes(shapes);
        StargateShapeRegistry.setEnabled("Massive", false);

        StargateShapeRegistry.reloadAllShapes();

        assertFalse(StargateShapeRegistry.isEnabled("Massive"));
        assertNotNull(StargateShapeRegistry.getStargateShape("Massive"),
            "a shape that is off is still loaded, so gates standing on it keep working");
    }

    /** Editing the file, or the plugin resaving it, must not turn the shape back on. */
    @Test
    void editingTheShapeFileKeepsItOff() throws Exception
    {
        StargateShapeRegistry.loadShapes(shapes);
        StargateShapeRegistry.setEnabled("Massive", false);
        final File massive = new File(shapes, "Massive.shape");
        Files.writeString(massive.toPath(), "# edited by hand\n" + Files.readString(massive.toPath()),
            StandardCharsets.UTF_8);

        StargateShapeRegistry.reloadAllShapes();

        assertFalse(StargateShapeRegistry.isEnabled("Massive"));
    }

    @Test
    void aHandWrittenListWithCommentsAndBlankLinesIsRead() throws Exception
    {
        final File list = new File(shapes, "disabled-shapes.txt");
        Files.writeString(list.toPath(), "# off\n\n  Massive  \nGrand\n", StandardCharsets.UTF_8);

        StargateShapeRegistry.readDisabled(list);

        assertFalse(StargateShapeRegistry.isEnabled("Massive"));
        assertFalse(StargateShapeRegistry.isEnabled("grand"));
        assertTrue(StargateShapeRegistry.isEnabled("# off"));
    }

    @Test
    void aChoiceThatCannotBeSavedStillHoldsUntilARestart()
    {
        assertTrue(shapes.delete());

        assertFalse(StargateShapeRegistry.setEnabled("Massive", false));
        assertFalse(StargateShapeRegistry.isEnabled("Massive"));
    }

    @Test
    void aMissingListMeansNothingIsOff()
    {
        StargateShapeRegistry.setEnabled("Massive", false);

        StargateShapeRegistry.readDisabled(new File(shapes, "absent.txt"));

        assertTrue(StargateShapeRegistry.isEnabled("Massive"));
    }
}
