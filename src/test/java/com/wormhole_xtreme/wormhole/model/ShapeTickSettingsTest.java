package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A shape file's {@code LIGHT_TICKS} and {@code WOOSH_TICKS} reach the gate.
 *
 * <p>Every shipped shape writes them as {@code LIGHT_TICKS = 2;}, and the parser once matched
 * only {@code LIGHT_TICKS=}, so every gate dialled at the built-in defaults whatever its file
 * said (#321).
 */
class ShapeTickSettingsTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/shapes/gate");

    private static int written(final List<String> lines, final String key)
    {
        final Pattern setting = Pattern.compile("^" + key + "\\s*=\\s*(\\d+)\\s*;?\\s*$");
        for (final String line : lines)
        {
            final Matcher m = setting.matcher(line.trim());
            if (m.matches())
            {
                return Integer.parseInt(m.group(1));
            }
        }
        return fail(key + " is not written");
    }

    @Test
    void everyShippedShapeGetsTheTicksItsFileWrites() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        final List<Path> files;
        try (Stream<Path> listing = Files.list(SHAPE_DIR))
        {
            files = listing.filter(p -> p.toString().endsWith(".shape")).sorted().toList();
        }
        assertEquals(9, files.size(), "the shipped gate shapes");

        boolean anyDiffersFromDefault = false;
        for (final Path file : files)
        {
            final List<String> lines = Files.readAllLines(file);
            final Stargate3DShape shape = new Stargate3DShape(lines.toArray(new String[0]));
            final int light = written(lines, "LIGHT_TICKS");
            assertEquals(light, shape.getShapeLightTicks(), file.getFileName() + ": LIGHT_TICKS");
            assertEquals(written(lines, "WOOSH_TICKS"), shape.getShapeWooshTicks(), file.getFileName() + ": WOOSH_TICKS");
            anyDiffersFromDefault |= light != 3;
        }
        // Otherwise a file matching the default would pass without the line ever being read.
        assertTrue(anyDiffersFromDefault, "some shape should write a LIGHT_TICKS other than the default");
    }

    @Test
    void aSettingIsReadWithOrWithoutSpacingAndSemicolon()
    {
        assertEquals("LIGHT_TICKS=2", Stargate3DShape.normaliseSetting("LIGHT_TICKS = 2;"));
        assertEquals("LIGHT_TICKS=2", Stargate3DShape.normaliseSetting("  LIGHT_TICKS=2  "));
        assertEquals("IRIS_MATERIAL=GLASS", Stargate3DShape.normaliseSetting("IRIS_MATERIAL =GLASS ; "));
    }
}
