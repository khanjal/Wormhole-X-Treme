package com.wormhole_xtreme.wormhole.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * {@link ShapeFileValidator} catches the two bug classes that actually shipped in this
 * project's own gate shapes before being caught by hand: a row one cell short of the shape's
 * declared width (shifts every column after the gap, does not throw), and a skipped
 * {@code Layer#N=} (leaves a silent dead gap in the array, does not throw). Every test here
 * reproduces one of those mistakes from a minimal shape rather than pointing at a real shipped
 * file, so a fix to the file does not quietly stop testing the bug.
 */
class ShapeFileValidatorTest
{
    private static final Path SHAPE_DIR = Paths.get("src/main/resources/GateShapes");

    /**
     * The smallest possible legal 3x3, single-layer shape: a frame ring around one portal
     * block, an entry point, and an activation switch -- just enough to satisfy every
     * requirement the real parser enforces, so tests can mutate one thing at a time from a
     * known-good baseline instead of a large real shape where an unrelated line could be the
     * one that actually matters.
     */
    private static String[] minimalValidShape()
    {
        return new String[] {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S]",
            "",
            "REDSTONE_ACTIVATED=FALSE",
        };
    }

    private static ShapeFileValidator.Result validate(final String[] lines) throws Exception
    {
        final WormholeXTreme plugin = mock(WormholeXTreme.class);
        final java.lang.reflect.Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, plugin);
        return ShapeFileValidator.validate(lines);
    }

    @Test
    void aMinimalWellFormedShapeHasNoProblems() throws Exception
    {
        final ShapeFileValidator.Result result = validate(minimalValidShape());
        assertTrue(result.isValid(), "unexpected problems: " + result.getProblems());
        assertEquals("Test", result.getShapeName());
    }

    @Test
    void everyShippedShapeIsStillValid() throws Exception
    {
        // The validator has to agree with the parser and the rest of the test suite about
        // what "fine" looks like -- a false positive here would make the command useless the
        // first time someone ran it against a shape nobody had touched.
        try (java.util.stream.Stream<Path> listing = Files.list(SHAPE_DIR))
        {
            for (final Path shape : listing.toList())
            {
                if (!shape.getFileName().toString().endsWith(".shape"))
                {
                    continue;
                }
                final List<String> lines = Files.readAllLines(shape);
                final ShapeFileValidator.Result result = validate(lines.toArray(new String[0]));
                assertTrue(result.isValid(),
                    shape.getFileName() + ": " + result.getProblems());
            }
        }
    }

    @Test
    void aRowOneCellShortOfTheDeclaredWidthIsCaughtRatherThanSilentlyMisaligningColumns() throws Exception
    {
        final String[] lines = minimalValidShape();
        for (int i = 0; i < lines.length; i++)
        {
            if (lines[i].equals("[S:A][P][S]"))
            {
                lines[i] = "[S:A][P]"; // one cell dropped
            }
        }
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("2 cells, not 3")),
            "expected a row-width mismatch to be reported, got: " + result.getProblems());
    }

    @Test
    void aSkippedLayerNumberIsCaughtRatherThanLeavingASilentGap() throws Exception
    {
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S]",
            "",
            // Layer#2 is skipped entirely; Layer#3 follows directly.
            "Layer#3=",
            "[I][I][I]",
            "[I][I][I]",
            "[I][I][I]",
            "",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("Layer#2 is missing")),
            "expected the layer gap to be reported, got: " + result.getProblems());
    }

    @Test
    void aSecondEntryPointIsCaughtRatherThanSilentlyOverwritingTheFirst() throws Exception
    {
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S:EP][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S]",
            "",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("2 :EP blocks")),
            "expected the duplicate :EP to be reported, got: " + result.getProblems());
    }

    @Test
    void aGapInLightOrderNumbersIsCaught() throws Exception
    {
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S:L#1][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S:L#3]", // #2 never used
            "",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains(":L #2 is never used, between #1 and #3")),
            "expected the light-order gap to be reported, got: " + result.getProblems());
    }

    @Test
    void aMaterialThatDoesNotExistIsCaught() throws Exception
    {
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S]",
            "",
            "PORTAL_MATERIAL=NOT_A_REAL_MATERIAL",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("NOT_A_REAL_MATERIAL")),
            "expected the unresolved material to be reported, got: " + result.getProblems());
    }

    @Test
    void theLegacyStationaryWaterAliasIsAcceptedTheSameWayTheRealParserAcceptsIt() throws Exception
    {
        // Not a made-up edge case: this is the exact false positive a real review caught.
        // Stargate3DShape resolves material names through parseMaterialName, which maps the
        // pre-1.13 STATIONARY_WATER/STATIONARY_LAVA names to WATER/LAVA before falling back to
        // Material.valueOf -- a shape using that legacy name loads and runs fine. Checking
        // against Material.matchMaterial directly instead would reject a shape the game
        // accepts, which is exactly backwards for something meant to double-check the parser.
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[S:A][P][S]",
            "[S][S:EP][S]",
            "",
            "PORTAL_MATERIAL=STATIONARY_WATER",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertTrue(result.isValid(), "unexpected problems: " + result.getProblems());
    }

    @Test
    void aVersion2FileWithNoEntryPointFailsToParseWithAReadableMessageInstead() throws Exception
    {
        // Stargate3DShape's own constructor refuses to finish without an :EP, throwing
        // IllegalArgumentException rather than returning a half-built shape -- this is what
        // that looks like through the validator instead of a bare stack trace.
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[S:A][P][S]",
            "[S][S][S]",
            "",
            "REDSTONE_ACTIVATED=FALSE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isParsed());
        assertFalse(result.isValid());
        assertFalse(result.getProblems().isEmpty());
    }

    @Test
    void aFileWithNoRecognisableGateShapeAtAllStillParsesAsAnEmptyLegacyShape() throws Exception
    {
        // Surprising, but real: a line that matches nothing StargateShapeFactory looks for
        // does not throw. With no "Version=2" it falls through to the legacy 2D constructor,
        // which does not require a "GateShape=" section to exist at all -- it just leaves
        // every position empty rather than refusing to parse. The generic :EP check this
        // validator adds is what actually catches it, not the parser.
        final String[] lines = { "not a shape file at all" };
        final ShapeFileValidator.Result result = validate(lines);
        assertTrue(result.isParsed());
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("no :EP block")),
            "expected the missing :EP to be reported, got: " + result.getProblems());
    }

    @Test
    void aRedstoneMarkerResolvingOntoTheFrameIsCaught() throws Exception
    {
        // [S:RA] is the frame-attached form: the marker is the frame block itself, so the
        // redstone belongs one above it (StargateHelper.redstoneComponentY). Stacking a plain
        // [S] directly above it means that resolved cell is frame too -- redstone placed there
        // can never actually be placed, since the frame already occupies it.
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S:A][S]",
            "[S:RA][P][S]",
            "[S][S:EP][S]",
            "",
            "REDSTONE_ACTIVATED=TRUE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("[RA]") && p.contains("frame")),
            "expected the frame-collision to be reported, got: " + result.getProblems());
    }

    @Test
    void aRedstoneDialTriggerWithNoDialSignToReadIsCaught() throws Exception
    {
        // [RD] dials whatever the :D sign currently shows -- a shape offering redstone
        // dialling with no :D block for it to read is offering a control that can never do
        // anything.
        final String[] lines = {
            "Name=Test",
            "Version=2",
            "GateShape=",
            "",
            "Layer#1=",
            "[S][S][S]",
            "[RD][S:A][P]",
            "[S][S:EP][S]",
            "",
            "REDSTONE_ACTIVATED=TRUE",
        };
        final ShapeFileValidator.Result result = validate(lines);
        assertFalse(result.isValid());
        assertTrue(result.getProblems().stream().anyMatch(p -> p.contains("[RD]") && p.contains(":D")),
            "expected the missing dial sign to be reported, got: " + result.getProblems());
    }
}
