package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import org.bukkit.DyeColor;

/**
 * What colour a block counts as, for the purpose of showing it on a banner.
 *
 * <p>Matched on the material's <em>name</em> rather than a {@code Material} constant, which is
 * the only approach that survives this plugin's version range without a table per version.
 * Bukkit has no method that gives a block's colour -- there is nothing to ask -- so it has to
 * be a judgement either way, and a judgement keyed on names keeps working when a new wood or a
 * new stone is added, because the new block is called what its family is called.
 *
 * <p>Two passes. Anything prefixed with a dye colour is that colour, which covers wool,
 * concrete, terracotta, glass, carpets, beds and everything else that comes in sixteen. What
 * is left is natural or built, and goes through an ordered table of name fragments: first
 * match wins, so the specific entries sit above the general ones -- {@code GLOWSTONE} before
 * {@code STONE}, {@code SOUL_SAND} before {@code SAND}, {@code CHERRY} before {@code LEAVES}.
 */
final class MirrorPalette
{
    /** Dye colour names longest first, so LIGHT_BLUE is tested before BLUE. */
    private static final DyeColor[] BY_PREFIX = byLongestName();

    /**
     * Blocks with no colour worth reporting, by exact name.
     *
     * <p>Exact, not by fragment, because these names turn up inside other ones. {@code AIR} is
     * a substring of {@code OAK_STAIRS} and {@code LIGHT} of {@code LIGHTNING_ROD}; matching
     * loosely would drop every staircase in a castle out of the sample.
     */
    private static final String[] IGNORED_EXACT = { "AIR", "CAVE_AIR", "VOID_AIR", "LIGHT",
        "BARRIER", "JIGSAW", "STRUCTURE_BLOCK", "STRUCTURE_VOID" };

    /**
     * Plain glass has no colour of its own, so a greenhouse reads as what is growing in it.
     * Stained glass is caught by the dye-colour pass before this and keeps its colour.
     */
    private static final String IGNORED_GLASS = "GLASS";

    /**
     * Name fragment to colour, in order. First match wins.
     *
     * <p>Read it top to bottom as a list of special cases before general ones. The general
     * ones at the bottom -- STONE, LOG, PLANKS, LEAVES -- are what catch a block added in a
     * version this table has never heard of.
     */
    private static final String[][] BY_FRAGMENT =
    {
        // The deep and the dark.
        { "SCULK", "BLACK" }, { "OBSIDIAN", "BLACK" }, { "BLACKSTONE", "BLACK" },
        { "BASALT", "BLACK" }, { "COAL", "BLACK" },
        // The Nether, which is mostly one colour and reads as it.
        { "NETHERRACK", "RED" }, { "NETHER_WART", "RED" }, { "NETHER", "RED" },
        { "CRIMSON", "RED" }, { "MAGMA", "ORANGE" }, { "LAVA", "ORANGE" },
        { "WARPED", "CYAN" }, { "SOUL", "BROWN" },
        // Water and ice.
        { "WATER", "BLUE" }, { "BLUE_ICE", "BLUE" }, { "ICE", "LIGHT_BLUE" },
        { "SNOW", "WHITE" }, { "PRISMARINE", "CYAN" }, { "SEA_LANTERN", "CYAN" },
        { "KELP", "GREEN" }, { "SEAGRASS", "GREEN" }, { "CORAL", "PINK" },
        // The End.
        { "PURPUR", "MAGENTA" }, { "CHORUS", "PURPLE" }, { "END_STONE", "YELLOW" },
        { "END_ROD", "WHITE" },
        // Ores and the metals out of them, before the stone they sit in.
        { "REDSTONE", "RED" }, { "GLOWSTONE", "YELLOW" }, { "LAPIS", "BLUE" },
        { "AMETHYST", "PURPLE" }, { "EMERALD", "GREEN" }, { "DIAMOND", "LIGHT_BLUE" },
        { "GOLD", "YELLOW" }, { "OXIDIZED", "CYAN" }, { "WEATHERED", "CYAN" },
        { "COPPER", "ORANGE" }, { "LIGHTNING_ROD", "ORANGE" }, { "IRON", "LIGHT_GRAY" },
        { "QUARTZ", "WHITE" },
        // Stone in its many names, and the sand it weathers to.
        { "SANDSTONE", "YELLOW" }, { "SAND", "YELLOW" }, { "GRAVEL", "LIGHT_GRAY" },
        { "DEEPSLATE", "GRAY" }, { "COBBLESTONE", "GRAY" }, { "STONE_BRICK", "GRAY" },
        { "ANDESITE", "LIGHT_GRAY" }, { "DIORITE", "WHITE" }, { "GRANITE", "BROWN" },
        { "CALCITE", "WHITE" }, { "TUFF", "GRAY" }, { "DRIPSTONE", "BROWN" },
        { "BEDROCK", "GRAY" }, { "STONE", "GRAY" },
        // Growing things.
        { "CHERRY", "PINK" }, { "BAMBOO", "LIME" }, { "AZALEA", "GREEN" },
        { "MOSS", "GREEN" }, { "GRASS", "GREEN" }, { "LEAVES", "GREEN" },
        { "VINE", "GREEN" }, { "FERN", "GREEN" }, { "CACTUS", "GREEN" },
        { "MELON", "LIME" }, { "PUMPKIN", "ORANGE" }, { "HAY", "YELLOW" },
        { "MUSHROOM", "RED" }, { "MYCELIUM", "PURPLE" }, { "FLOWER", "PINK" },
        // Wood, and the furniture made of it -- a library's shelves land here.
        { "BOOKSHELF", "BROWN" }, { "LECTERN", "BROWN" }, { "BARREL", "BROWN" },
        { "CHEST", "BROWN" }, { "CRAFTING", "BROWN" }, { "LOG", "BROWN" },
        { "PLANKS", "BROWN" }, { "STEM", "BROWN" }, { "WOOD", "BROWN" },
        { "SCAFFOLDING", "BROWN" },
        // The species, which is what a stair or a fence is called -- OAK_STAIRS says neither
        // LOG nor PLANKS nor WOOD, so without these a wooden staircase has no colour at all.
        { "OAK", "BROWN" }, { "SPRUCE", "BROWN" }, { "BIRCH", "BROWN" },
        { "JUNGLE", "BROWN" }, { "ACACIA", "ORANGE" }, { "MANGROVE", "RED" },
        // Ground.
        { "PODZOL", "BROWN" }, { "DIRT", "BROWN" }, { "FARMLAND", "BROWN" },
        { "MUD", "BROWN" }, { "CLAY", "LIGHT_GRAY" }, { "TERRACOTTA", "ORANGE" },
        // Built, and lit.
        { "BRICK", "RED" }, { "TORCH", "YELLOW" }, { "LANTERN", "YELLOW" },
        { "CAMPFIRE", "ORANGE" }, { "FIRE", "ORANGE" }, { "BONE", "WHITE" },
        { "WOOL", "WHITE" }, { "CANDLE", "WHITE" }, { "ORE", "GRAY" }
    };

    /** Static helpers only. */
    private MirrorPalette()
    {
    }

    /**
     * What colour to call a block.
     *
     * @param materialName
     *            the material's name, as Bukkit spells it
     * @return the colour, or null if this block has none worth showing
     */
    static DyeColor of(final String materialName)
    {
        if ((materialName == null) || materialName.isEmpty())
        {
            return null;
        }
        final String name = materialName.toUpperCase(Locale.ROOT);
        // Colour first: stained glass is a colour, and LIGHT_GRAY_ must not be read as LIGHT.
        for (final DyeColor colour : BY_PREFIX)
        {
            if (name.startsWith(colour.name() + "_"))
            {
                return colour;
            }
        }
        if (name.contains(IGNORED_GLASS) || isIgnored(name))
        {
            return null;
        }
        for (final String[] entry : BY_FRAGMENT)
        {
            if (name.contains(entry[0]))
            {
                return DyeColor.valueOf(entry[1]);
            }
        }
        return null;
    }

    /** @return true if this is one of the blocks with nothing to show */
    private static boolean isIgnored(final String name)
    {
        for (final String ignored : IGNORED_EXACT)
        {
            if (name.equals(ignored))
            {
                return true;
            }
        }
        return false;
    }

    /** Dye colours ordered longest name first, so no prefix hides a longer one. */
    private static DyeColor[] byLongestName()
    {
        final DyeColor[] colours = DyeColor.values();
        Arrays.sort(colours, Comparator.comparingInt((DyeColor c) -> c.name().length()).reversed());
        return colours;
    }
}
