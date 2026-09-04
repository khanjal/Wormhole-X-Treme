package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies every row in a {@code .shape} file's grid has as many {@code [cell]}s as the width
 * its first row established -- the same lazy-bracket count {@link Stargate3DShape}'s own parser
 * uses, so a row that parser would silently misalign instead of rejecting is still caught here.
 *
 * <p>{@link Stargate3DShape} derives one width/height from {@code Layer#1} and trusts every
 * later row to match it; a row one cell short does not throw, it just shifts every column after
 * the gap. Reaching an assertion after loading a shape proves it parsed, not that its rows are
 * actually uniform -- this is what a test needs instead, and it is why this reads the file's
 * raw lines rather than the already-parsed shape object, which has already lost the information
 * needed to tell a well-formed row from a short one.
 */
final class ShapeFileRowWidths
{
    private static final Pattern CELL = Pattern.compile("\\[(.+?)\\]");

    private ShapeFileRowWidths() {}

    /**
     * @param shapeFile the {@code .shape} file to check
     * @throws IOException if the file cannot be read
     */
    static void assertConsistent(final Path shapeFile) throws IOException
    {
        final List<String> fileLines = Files.readAllLines(shapeFile);
        Integer width = null;
        String currentLayer = null;
        int rowInLayer = 0;

        for (final String rawLine : fileLines)
        {
            final String line = rawLine.trim();
            if (line.startsWith("#"))
            {
                continue;
            }
            if (line.startsWith("Layer#") && line.endsWith("="))
            {
                currentLayer = line;
                rowInLayer = 0;
                continue;
            }
            if (!line.startsWith("["))
            {
                continue;
            }
            final Matcher m = CELL.matcher(line);
            int count = 0;
            while (m.find())
            {
                count++;
            }
            if (width == null)
            {
                width = count;
            }
            else if (count != width)
            {
                fail(shapeFile.getFileName() + " " + currentLayer + " row " + rowInLayer + " has "
                    + count + " cells, not " + width + " -- a block was likely dropped or added "
                    + "while editing, and every column after the gap is shifted for the rest of "
                    + "that row: " + rawLine);
            }
            rowInLayer++;
        }
    }
}
