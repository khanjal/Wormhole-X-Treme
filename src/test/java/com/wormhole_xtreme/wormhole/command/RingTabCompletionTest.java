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
    public void theBuiltFieldOffersTheSameSlabsAsRing()
    {
        // built names the same kind of slab as ring -- what reset restores to rather than
        // what is currently worn -- so it shares the constraint and the list.
        assertEquals(complete("ring", "edit", "ring", ""), complete("ring", "edit", "built", ""));
    }

    @Test
    public void theLightFieldOffersOnlyBlocksThatLookLit()
    {
        // Narrowed twice over. None of these actually light anything — a ring is drawn to
        // clients and the server's light data is untouched — so the choice is purely how it
        // looks. And the pattern is drawn into a floor, where a torch or a lantern would be
        // a block rendered somewhere it cannot hang.
        final List<String> lights = complete("ring", "edit", "light", "");
        assertTrue(lights.contains("glowstone"));
        assertTrue(lights.contains("sea_lantern"));
        assertTrue(lights.contains("shroomlight"));

        assertFalse(lights.contains("torch"), "a torch cannot hang inside a floor");
        assertFalse(lights.contains("lantern"));
        assertFalse(lights.contains("dirt"), "and a plain block is not a light");
        assertTrue(lights.size() < 40, "a list somebody can actually read");
    }

    @Test
    public void lightsAreFilteredByWhatHasBeenTyped()
    {
        final List<String> typed = complete("ring", "edit", "light", "sea");
        assertEquals(java.util.Collections.singletonList("sea_lantern"), typed);
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
    public void theTwoLightsAreOfferedSeparatelyAndBothOfferGlowingBlocks()
    {
        // The pad light and the transport flash are two different moments, so they are two
        // fields — but they draw from the same set, since both want something that looks lit.
        assertTrue(complete("ring", "edit", "").contains("light"));
        assertTrue(complete("ring", "edit", "").contains("flash"));
        assertEquals(complete("ring", "edit", "light", ""), complete("ring", "edit", "flash", ""));
        assertTrue(complete("ring", "edit", "flash", "").contains("sea_lantern"));
    }

    @Test
    public void resetIsOfferedAndTakesNoValue()
    {
        assertTrue(complete("ring", "edit", "").contains("reset"));
        assertTrue(complete("ring", "edit", "reset", "").isEmpty());
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
