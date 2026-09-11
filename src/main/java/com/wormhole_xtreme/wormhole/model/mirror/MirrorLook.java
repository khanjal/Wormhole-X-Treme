package com.wormhole_xtreme.wormhole.model.mirror;

/**
 * What a mirror looks like, remembered rather than left in the block.
 *
 * <p>The banner in the world carries the look as well, and that is the copy that matters most:
 * banner patterns are vanilla data, so a server that removes this plugin keeps its corridor of
 * stamped banners instead of finding a row of plain cloth. Nothing here is the only record of
 * what an operator built.
 *
 * <p>This copy exists because the block alone cannot answer two questions. A
 * {@link MirrorDisplay#PROXIMITY} mirror has to be dressed again after a player has been shown
 * the blank, and a {@link MirrorMode#DYNAMIC} one has to know what it last saw in order to
 * replace it. Both need the look as data rather than as a block somebody may have re-dyed.
 *
 * <p>Two ways to be stamped, and the fields say which:
 *
 * <ul>
 * <li>By name -- {@code stamp <mirror> cavern} -- sets {@link #presetName()} and no view. The
 * operator chose, and nothing about the far side gets to argue.</li>
 * <li>By looking -- {@code stamp <mirror>} -- sets {@link #view()}, from which the preset is
 * worked out. A dynamic mirror replaces this view and keeps the same shape.</li>
 * </ul>
 *
 * @param presetName
 *            the look an operator named, or null if it was worked out from the far side
 * @param view
 *            what the far side looked like when it was sampled, or null if never sampled
 */
public record MirrorLook(String presetName, MirrorView view)
{
    /**
     * A look an operator chose by name.
     *
     * @param name
     *            the preset's name
     * @return the look
     */
    public static MirrorLook named(final String name)
    {
        return new MirrorLook(name, null);
    }

    /**
     * A look read off the far side.
     *
     * @param view
     *            what was found there
     * @return the look
     */
    public static MirrorLook seen(final MirrorView view)
    {
        return new MirrorLook(null, view);
    }

    /** @return true if there is nothing here to show */
    public boolean isEmpty()
    {
        return (presetName == null) && (view == null);
    }

    /**
     * The preset this look wears.
     *
     * <p>A named look resolves to that preset and nothing else, so an operator who names one
     * and then edits the preset file sees the edit.
     *
     * <p>A seen look asks the biome first, and only falls to the indoor look when being
     * enclosed actually tells you something. Some places are enclosed by their nature -- the
     * Nether is solid rock with a ceiling on it, a cave is a cave -- and for those the sampler
     * reports {@code enclosed} for every mirror ever pointed there, so treating that as "this
     * is a room" would mean no mirror into the Nether could ever wear the Nether's look. Those
     * presets say {@link MirrorPreset#sheltered()} and keep it.
     *
     * <p>What is left is a room somewhere it is not normal to be enclosed, which is the
     * library case this rule was written for.
     *
     * @return the preset, or null if there is none to be had
     */
    public MirrorPreset preset()
    {
        if (presetName != null)
        {
            return MirrorPresetRegistry.byName(presetName);
        }
        if (view == null)
        {
            return null;
        }
        final MirrorPreset byBiome = MirrorPresetRegistry.forBiome(view.biome());
        if (!view.enclosed() || ((byBiome != null) && byBiome.sheltered()))
        {
            return byBiome;
        }
        return MirrorPresetRegistry.indoors();
    }
}
