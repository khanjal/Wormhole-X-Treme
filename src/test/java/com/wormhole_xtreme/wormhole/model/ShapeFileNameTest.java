package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.ShapeFileValidator;

/**
 * {@code gate shapes validate|reload <name>} read a file from the shapes folder and nowhere else.
 *
 * <p>A valid shape sits one level up, in {@code shapes/}, so a name that climbs out of the folder
 * would load it if nothing stopped it; the control is the same shape inside the folder, which loads.
 */
class ShapeFileNameTest
{
    private static final String SHAPE = String.join("\n", List.of(
        "Name=NameTestShape", "Version=2", "GateShape=", "", "Layer#1=",
        "[S][S][S]", "[S:A][P][S]", "[S][S:EP][S]", "", "REDSTONE_ACTIVATED=FALSE"));

    @TempDir
    File tempDir;

    private File inside;
    private File outside;

    @BeforeEach
    void setUp() throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        PluginTestSupport.install(plugin);
        final File gate = new File(tempDir, "shapes" + File.separator + "gate");
        assertTrue(gate.mkdirs());
        inside = new File(gate, "Inside.shape");
        outside = new File(gate.getParentFile(), "Outside.shape");
        Files.writeString(inside.toPath(), SHAPE, StandardCharsets.UTF_8);
        Files.writeString(outside.toPath(), SHAPE, StandardCharsets.UTF_8);
        StargateShapeRegistry.getStargateShapes().remove("NameTestShape");
    }

    @AfterEach
    void tearDown() throws Exception
    {
        StargateShapeRegistry.getStargateShapes().remove("NameTestShape");
        PluginTestSupport.remove();
    }

    @Test
    void aShapeInsideTheFolderIsStillRead()
    {
        assertTrue(StargateShapeRegistry.validateShapeFile("Inside.shape").isValid());
        assertTrue(StargateShapeRegistry.reloadShapeFile("Inside.shape").isValid());
    }

    @Test
    void aNameThatLeavesTheFolderIsRefusedWithoutReadingThere()
    {
        final String[] escapes = { "../Outside.shape", "..\\Outside.shape", outside.getAbsolutePath(),
            "sub/Inside.shape", "C:Inside.shape", "" };
        for (final String name : escapes)
        {
            final ShapeFileValidator.Result checked = StargateShapeRegistry.validateShapeFile(name);
            final ShapeFileValidator.Result reloaded = StargateShapeRegistry.reloadShapeFile(name);

            assertFalse(checked.isValid(), "validate " + name);
            assertFalse(reloaded.isValid(), "reload " + name);
            assertTrue(checked.getProblems().get(0).startsWith("Not a file in the shapes folder"), checked.getProblems().toString());
        }
        assertNull(StargateShapeRegistry.getStargateShape("NameTestShape"),
            "nothing was loaded, whichever way the names were tried");
    }
}
