package com.wormhole_xtreme.wormhole.model.mirror;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
    /**
     * One fragment of a material name, and what colour it means.
     *
     * @param fragment
     *            the piece of the name to look for, upper-case
     * @param colour
     *            what a block whose name contains it counts as
     */
    private record Rule(String fragment, DyeColor colour)
    {
    }

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
     * ones near the bottom -- STONE, LOG, PLANKS, LEAVES -- are what catch a block added in a
     * version this table has never heard of.
     */
    private static final List<Rule> BY_FRAGMENT = List.of(
        // The deep and the dark.
        rule("SCULK", DyeColor.BLACK), rule("OBSIDIAN", DyeColor.BLACK),
        rule("BLACKSTONE", DyeColor.BLACK), rule("BASALT", DyeColor.BLACK),
        rule("COAL", DyeColor.BLACK),
        // The Nether, which is mostly one colour and reads as it.
        rule("NETHERRACK", DyeColor.RED), rule("NETHER_WART", DyeColor.RED),
        rule("NETHER", DyeColor.RED), rule("CRIMSON", DyeColor.RED),
        rule("MAGMA", DyeColor.ORANGE), rule("LAVA", DyeColor.ORANGE),
        rule("WARPED", DyeColor.CYAN), rule("SOUL", DyeColor.BROWN),
        // Water and ice.
        rule("WATER", DyeColor.BLUE), rule("BLUE_ICE", DyeColor.BLUE),
        rule("ICE", DyeColor.LIGHT_BLUE), rule("SNOW", DyeColor.WHITE),
        rule("PRISMARINE", DyeColor.CYAN), rule("SEA_LANTERN", DyeColor.CYAN),
        rule("KELP", DyeColor.GREEN), rule("SEAGRASS", DyeColor.GREEN),
        rule("CORAL", DyeColor.PINK),
        // The End.
        rule("PURPUR", DyeColor.MAGENTA), rule("CHORUS", DyeColor.PURPLE),
        rule("END_STONE", DyeColor.YELLOW), rule("END_ROD", DyeColor.WHITE),
        // Ores and the metals out of them, before the stone they sit in.
        rule("REDSTONE", DyeColor.RED), rule("GLOWSTONE", DyeColor.YELLOW),
        rule("LAPIS", DyeColor.BLUE), rule("AMETHYST", DyeColor.PURPLE),
        rule("EMERALD", DyeColor.GREEN), rule("DIAMOND", DyeColor.LIGHT_BLUE),
        rule("GOLD", DyeColor.YELLOW), rule("OXIDIZED", DyeColor.CYAN),
        rule("WEATHERED", DyeColor.CYAN), rule("COPPER", DyeColor.ORANGE),
        rule("LIGHTNING_ROD", DyeColor.ORANGE), rule("IRON", DyeColor.LIGHT_GRAY),
        rule("QUARTZ", DyeColor.WHITE),
        // Stone in its many names, and the sand it weathers to.
        rule("SANDSTONE", DyeColor.YELLOW), rule("SAND", DyeColor.YELLOW),
        rule("GRAVEL", DyeColor.LIGHT_GRAY), rule("DEEPSLATE", DyeColor.GRAY),
        rule("COBBLESTONE", DyeColor.GRAY), rule("STONE_BRICK", DyeColor.GRAY),
        rule("ANDESITE", DyeColor.LIGHT_GRAY), rule("DIORITE", DyeColor.WHITE),
        rule("GRANITE", DyeColor.BROWN), rule("CALCITE", DyeColor.WHITE),
        rule("TUFF", DyeColor.GRAY), rule("DRIPSTONE", DyeColor.BROWN),
        rule("BEDROCK", DyeColor.GRAY), rule("STONE", DyeColor.GRAY),
        // Growing things.
        rule("CHERRY", DyeColor.PINK), rule("BAMBOO", DyeColor.LIME),
        rule("AZALEA", DyeColor.GREEN), rule("MOSS", DyeColor.GREEN),
        rule("GRASS", DyeColor.GREEN), rule("LEAVES", DyeColor.GREEN),
        rule("VINE", DyeColor.GREEN), rule("FERN", DyeColor.GREEN),
        rule("CACTUS", DyeColor.GREEN), rule("MELON", DyeColor.LIME),
        rule("PUMPKIN", DyeColor.ORANGE), rule("HAY", DyeColor.YELLOW),
        rule("MUSHROOM", DyeColor.RED), rule("MYCELIUM", DyeColor.PURPLE),
        rule("FLOWER", DyeColor.PINK),
        // Wood, and the furniture made of it -- a library's shelves land here.
        rule("BOOKSHELF", DyeColor.BROWN), rule("LECTERN", DyeColor.BROWN),
        rule("BARREL", DyeColor.BROWN), rule("CHEST", DyeColor.BROWN),
        rule("CRAFTING", DyeColor.BROWN), rule("LOG", DyeColor.BROWN),
        rule("PLANKS", DyeColor.BROWN), rule("STEM", DyeColor.BROWN),
        rule("WOOD", DyeColor.BROWN), rule("SCAFFOLDING", DyeColor.BROWN),
        // The species, which is what a stair or a fence is called -- OAK_STAIRS says neither
        // LOG nor PLANKS nor WOOD, so without these a wooden staircase has no colour at all.
        rule("OAK", DyeColor.BROWN), rule("SPRUCE", DyeColor.BROWN),
        rule("BIRCH", DyeColor.BROWN), rule("JUNGLE", DyeColor.BROWN),
        rule("ACACIA", DyeColor.ORANGE), rule("MANGROVE", DyeColor.RED),
        // Ground.
        rule("PODZOL", DyeColor.BROWN), rule("DIRT", DyeColor.BROWN),
        rule("FARMLAND", DyeColor.BROWN), rule("MUD", DyeColor.BROWN),
        rule("CLAY", DyeColor.LIGHT_GRAY), rule("TERRACOTTA", DyeColor.ORANGE),
        // Built, and lit.
        rule("BRICK", DyeColor.RED), rule("TORCH", DyeColor.YELLOW),
        rule("LANTERN", DyeColor.YELLOW), rule("CAMPFIRE", DyeColor.ORANGE),
        rule("FIRE", DyeColor.ORANGE), rule("BONE", DyeColor.WHITE),
        rule("WOOL", DyeColor.WHITE), rule("CANDLE", DyeColor.WHITE),
        rule("ORE", DyeColor.GRAY));

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
        for (final Rule entry : BY_FRAGMENT)
        {
            if (name.contains(entry.fragment()))
            {
                return entry.colour();
            }
        }
        return null;
    }

    /** Reads as a table row, and keeps the colour an enum constant rather than a string. */
    private static Rule rule(final String fragment, final DyeColor colour)
    {
        return new Rule(fragment, colour);
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
