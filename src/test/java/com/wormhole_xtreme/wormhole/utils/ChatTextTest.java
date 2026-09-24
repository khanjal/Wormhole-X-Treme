package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The shared chat palette: each fragment coloured, then back to the body grey. */
class ChatTextTest
{
    @Test
    void eachFragmentReturnsToTheBodyColour()
    {
        assertEquals("§bIsisium§7", ChatText.name("Isisium"));
        assertEquals("§f413 185 773§7", ChatText.value("413 185 773"));
        assertEquals("§eGLASS§7", ChatText.material("GLASS"));
        assertEquals("§a460 of 464§7", ChatText.good("460 of 464"));
        assertEquals("§cMissing§7", ChatText.bad("Missing"));
        assertEquals("§f/dial§7", ChatText.command("/dial"));
    }

    @Test
    void alternativesBetweenBlocksKeepTheirOrInTheBodyColour()
    {
        assertEquals("§eOBSIDIAN§7 or §eGLASS§7", ChatText.material("OBSIDIAN or GLASS"));
    }

    @Test
    void plainIsWhatAPlayerReads()
    {
        assertEquals("Isisium is now Grand", ChatText.plain("§3:: §7" + ChatText.name("Isisium") + " is now "
            + ChatText.name("Grand")).replace(":: ", ""));
        assertEquals("", ChatText.plain(null));
    }

    /** A usage line: words to type in white, required values in aqua, optional parts left grey (#325). */
    @Test
    void aUsageLineColoursWhatToTypeAndWhatToFillIn()
    {
        assertEquals("Usage: §f/wormhole§7 §fgate§7 §fedit§7 §b<gate>§7 §b<field>§7 [value]",
            ChatText.usage("/wormhole gate edit <gate> <field> [value]"));
    }

    /** A required value inside an optional part stays grey with the rest of it, brackets and all. */
    @Test
    void everythingInsideAnOptionalPartStaysGrey()
    {
        assertEquals("Usage: §f/wormhole§7 §fgate§7 §fregen§7 §b<gate>§7 [-shape <shape>] [-fill]",
            ChatText.usage("/wormhole gate regen <gate> [-shape <shape>] [-fill]"));
    }
}
