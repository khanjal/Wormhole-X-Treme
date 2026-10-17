package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The colour table that replaced Bukkit's deprecated {@code ChatColor} (#476) writes the same
 * codes it did.
 *
 * <p>A sign colour is configured by name and written as a code, so a constant with the wrong
 * code would draw every gate's sign in the wrong colour, and a reordered table would change the
 * list an admin is shown when a colour setting is refused.
 */
class ChatColourTest
{
    @Test
    void theSixteenColoursAreInMinecraftsOwnOrder()
    {
        assertEquals(List.of("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE", "GOLD",
            "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE"),
            Arrays.stream(ChatColour.values()).map(ChatColour::name).toList(),
            "the refusal for a sign colour lists these names in this order, as it did from ChatColor");
    }

    @Test
    void eachColourWritesItsOwnCode()
    {
        final String codes = "0123456789abcdef";
        final ChatColour[] colours = ChatColour.values();
        for (int i = 0; i < colours.length; i++)
        {
            assertEquals("§" + codes.charAt(i), colours[i].toString(),
                colours[i] + " should write the code the game draws as " + colours[i].name());
        }
    }

    @Test
    void italicIsTheItalicFormatCode()
    {
        assertEquals("§o", ChatColour.ITALIC);
        assertEquals('§', ChatColour.COLOR_CHAR);
    }

    @Test
    void strippingRemovesEveryCodeAndLeavesTheText()
    {
        assertEquals("Ada the Brave", ChatColour.stripColor("§bAda §othe §lBrave§r"),
            "a nickname's colours and formats should not reach a stand-in's name tag");
        assertNull(ChatColour.stripColor(null));
    }
}
