package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tab completion for {@code /wormhole ring edit}, which is the one command here with a
 * genuinely awkward shape.
 *
 * <p>It takes an optional pair id before the field, so the field can be at either of two
 * positions and the value at either of two more. The completer works that out from the word
 * before the one being typed rather than from the argument count, and these check it gets the
 * same answers in both forms.
 *
 * <p>The material fields are the reason any of this exists: there are dozens of slabs and
 * hundreds of blocks, and nobody remembers how polished_deepslate is spelled.
 */
public class RingTabCompletionTest
{
    private static List<String> complete(final String... args)
    {
        return SubCommands.find("ring").completeArgs(args);
    }

    @Test
    public void theVerbsAreOffered()
    {
        assertTrue(complete("ring", "").contains("create"));
        assertTrue(complete("ring", "e").contains("edit"));
        assertFalse(complete("ring", "e").contains("create"));
    }

    @Test
    public void theEditableFieldsAreOffered()
    {
        final List<String> fields = complete("ring", "edit", "");
        assertTrue(fields.contains("ring"));
        assertTrue(fields.contains("light"));
        assertTrue(fields.contains("access"));
        assertTrue(fields.contains("style"));
        assertTrue(fields.contains("name"));
    }

    @Test
    public void theRingFieldOffersOnlySlabs()
    {
        // Anything else is something the command is about to refuse, so offering it would be
        // offering a mistake.
        final List<String> slabs = complete("ring", "edit", "ring", "");
        assertTrue(slabs.contains("stone_slab"));
        assertFalse(slabs.contains("stone"), "a full block is not a ring material");
        assertFalse(slabs.contains("glowstone"));
        for (final String name : slabs)
        {
            assertTrue(name.endsWith("_slab"), name + " is not a slab");
        }
    }

    @Test
    public void theLightFieldOffersBlocksThatAreNotSlabs()
    {
        final List<String> blocks = complete("ring", "edit", "light", "");
        assertTrue(blocks.contains("glowstone"));
        assertTrue(blocks.contains("sea_lantern"));
        assertTrue(blocks.size() > complete("ring", "edit", "ring", "").size(),
            "a light may be any block, so there are more of them");
    }

    @Test
    public void materialsAreFilteredByWhatHasBeenTyped()
    {
        final List<String> typed = complete("ring", "edit", "ring", "deepslate");
        assertFalse(typed.isEmpty());
        for (final String name : typed)
        {
            assertTrue(name.startsWith("deepslate"), name + " does not match what was typed");
        }
    }

    @Test
    public void theSameFieldWorksAfterAPairIdAsWithoutOne()
    {
        // The awkward part: edit takes an optional id, so both of these are valid and the
        // field sits at a different index in each.
        assertEquals(complete("ring", "edit", "ring", "stone"),
            complete("ring", "edit", "7f3a1c2e", "ring", "stone"));
        assertEquals(complete("ring", "edit", "access", ""),
            complete("ring", "edit", "7f3a1c2e", "access", ""));
    }

    @Test
    public void aWordThatIsNotAFieldIsTakenForAnIdAndTheFieldsFollow()
    {
        final List<String> fields = complete("ring", "edit", "7f3a1c2e", "");
        assertTrue(fields.contains("ring"));
        assertTrue(fields.contains("style"));
    }

    @Test
    public void accessAndStyleOfferTheirOwnWords()
    {
        assertEquals(java.util.Arrays.asList("public", "private"),
            complete("ring", "edit", "access", ""));
        final List<String> styles = complete("ring", "edit", "style", "");
        assertTrue(styles.contains("fast"));
        assertTrue(styles.contains("slow"));
        assertTrue(styles.contains("concurrent"), "the real names still work");
    }

    @Test
    public void aNameIsWhateverThePlayerWants()
    {
        assertTrue(complete("ring", "edit", "name", "").isEmpty());
    }

    @Test
    public void nothingIsOfferedForVerbsThatTakeNoCompletableArgument()
    {
        assertTrue(complete("ring", "list", "").isEmpty());
        assertTrue(complete("ring", "create", "").isEmpty());
    }
}
