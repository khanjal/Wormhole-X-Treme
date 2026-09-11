package com.wormhole_xtreme.wormhole.model.mirror;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;

import com.wormhole_xtreme.wormhole.utils.PluginLog;

/**
 * Puts a look onto a mirror's banner.
 *
 * <p>Two layers of meaning end up on the same cloth. The preset supplies the frame -- the base
 * colour and the border and shapes that say what kind of place this is -- and the view supplies
 * up to three coarse squares in the colours that actually dominate over there. The squares go
 * on first so the frame sits over them, which is what stops a sampled colour from swallowing
 * the whole banner.
 *
 * <p>Six patterns is the limit. That is not an API limit but a crafting one, and a banner with
 * more than six does not render the extras on every client, so three squares plus a
 * three-layer preset is the budget this works to. A preset with more layers than fit is cut
 * from the end rather than refused.
 */
public final class MirrorStamp
{
    /** What a banner can show without a client dropping the extras. */
    private static final int MAX_PATTERNS = 6;

    /**
     * Where the sampled colours go, in order of how common they are.
     *
     * <p>Squares rather than stripes or gradients because a square reads as a thing rather than
     * as a background -- three of them in the colours of the far side look like objects seen
     * through a frame, which is as close to a view as a banner gets.
     */
    private static final String[] VIEW_PATTERNS =
        { "SQUARE_TOP_LEFT", "SQUARE_TOP_RIGHT", "SQUARE_BOTTOM_LEFT" };

    /**
     * Pattern types by name, resolved once each.
     *
     * <p>Reflectively, and that is not caution -- it is required. {@code PatternType} is an
     * enum through 1.20.6 and an interface from 1.21, so {@code PatternType.valueOf(name)}
     * compiled against 1.20.4 emits a class-method reference that the JVM refuses against an
     * interface: it throws {@code IncompatibleClassChangeError} on every server from 1.21 on.
     * {@code Registry.BANNER_PATTERN} has the opposite problem -- it does not exist on 1.20.
     * Reflection is the one route that works across the whole supported range, and it is paid
     * once per name rather than once per stamp.
     */
    private static final Map<String, PatternType> RESOLVED = new HashMap<>();

    /** {@code PatternType.valueOf}, however it is declared on this server. */
    private static final Method VALUE_OF = findValueOf();

    /** Static helpers only. */
    private MirrorStamp()
    {
    }

    /**
     * Stamps a banner with a preset alone, no sampling.
     *
     * @param block
     *            the banner block
     * @param preset
     *            the look to apply
     * @return true if the banner was changed
     */
    public static boolean apply(final Block block, final MirrorPreset preset)
    {
        return apply(block, preset, null);
    }

    /**
     * Stamps a banner with a preset, and with what is on the far side if it could be seen.
     *
     * @param block
     *            the banner block
     * @param preset
     *            the frame
     * @param view
     *            what the far side looks like, or null to use the preset alone
     * @return true if the banner was changed
     */
    public static boolean apply(final Block block, final MirrorPreset preset,
        final MirrorView view)
    {
        final Banner banner = dress(block, preset, view);
        if (banner == null)
        {
            return false;
        }
        banner.update(true);
        return true;
    }

    /**
     * Puts a remembered look onto a banner, in the world.
     *
     * @param block
     *            the banner block
     * @param look
     *            what it should look like
     * @return true if the banner was changed
     */
    public static boolean applyLook(final Block block, final MirrorLook look)
    {
        if ((look == null) || look.isEmpty())
        {
            return false;
        }
        return apply(block, look.preset(), look.view());
    }

    /**
     * A banner state wearing a look, not written to the world.
     *
     * <p>{@code Block.getState()} hands back a copy, so dressing it and never calling
     * {@code update()} produces exactly what a per-player packet needs: an appearance that
     * exists only for whoever it is sent to.
     *
     * <p>Used to put a proximity mirror's look <em>back</em> for somebody who has been shown
     * the blank. The world's own banner is stamped throughout -- it is
     * {@link #blankState(Block)} that makes the temporary copy, not this.
     *
     * @param block
     *            the banner block
     * @param look
     *            what to dress the copy in
     * @return the detached state, or null if that block is not a banner or there is no look
     */
    public static Banner lookState(final Block block, final MirrorLook look)
    {
        if ((look == null) || look.isEmpty())
        {
            return null;
        }
        return dress(block, look.preset(), look.view());
    }

    /**
     * A banner state with every pattern taken off, not written to the world.
     *
     * <p>What a proximity mirror looks like to somebody too far away to have been shown it.
     * Detached, like {@link #lookState}, because the world's own banner stays stamped -- the
     * patterns are vanilla data and outlive this plugin, so the blank is the illusion and the
     * stamped banner is the truth, not the other way round.
     *
     * <p>The base colour is left alone rather than forced to white: it is whatever banner the
     * operator hung there, and a mirror that is off should still look like the thing they
     * built.
     *
     * @param block
     *            the banner block
     * @return the undressed copy, or null if that block is not a banner
     */
    public static Banner blankState(final Block block)
    {
        if (block == null)
        {
            return null;
        }
        final BlockState state = block.getState();
        if (!(state instanceof Banner banner))
        {
            return null;
        }
        banner.setPatterns(new ArrayList<>());
        return banner;
    }

    /**
     * A banner state dressed in a preset and a view, not yet written anywhere.
     *
     * @return the dressed copy, or null if that block is not a banner
     */
    private static Banner dress(final Block block, final MirrorPreset preset,
        final MirrorView view)
    {
        if ((block == null) || (preset == null))
        {
            return null;
        }
        final BlockState state = block.getState();
        if (!(state instanceof Banner banner))
        {
            return null;
        }
        banner.setBaseColor(baseFor(preset, view));
        banner.setPatterns(patternsFor(preset, view));
        return banner;
    }

    /**
     * What colour the cloth itself is.
     *
     * <p>The preset's own colour, except indoors. Inside a building the biome describes the
     * ground the roof happens to stand on, so the preset that biome picked says nothing useful
     * about the room -- the commonest block in it says far more, and a library comes back the
     * brown of its shelves rather than the green of the meadow outside.
     */
    private static DyeColor baseFor(final MirrorPreset preset, final MirrorView view)
    {
        if ((view != null) && view.enclosed() && (view.dominant() != null))
        {
            return view.dominant();
        }
        return preset.base();
    }

    /** The sampled squares first, then as much of the preset's frame as still fits. */
    private static List<Pattern> patternsFor(final MirrorPreset preset, final MirrorView view)
    {
        final List<Pattern> patterns = new ArrayList<>();
        addViewSquares(patterns, view);
        for (final MirrorPreset.Layer layer : preset.layers())
        {
            if (patterns.size() >= MAX_PATTERNS)
            {
                break;
            }
            add(patterns, layer.colour(), layer.pattern());
        }
        return patterns;
    }

    /**
     * One square per sampled colour.
     *
     * <p>Indoors the commonest colour is already the base, so it is not also drawn as a square
     * -- that would be a brown square on brown cloth. The rest still go on, which is how the
     * shelves in a library end up with the stone of its walls beside them.
     */
    private static void addViewSquares(final List<Pattern> patterns, final MirrorView view)
    {
        if (view == null)
        {
            return;
        }
        final List<DyeColor> colours = view.colours();
        final int from = (view.enclosed() && !colours.isEmpty()) ? 1 : 0;
        for (int i = from; i < colours.size(); i++)
        {
            final int slot = i - from;
            if (slot >= VIEW_PATTERNS.length)
            {
                break;
            }
            add(patterns, colours.get(i), VIEW_PATTERNS[slot]);
        }
    }

    /**
     * Adds one pattern, or skips it.
     *
     * <p>Skipping rather than failing, the same way an unrecognised sound name is skipped:
     * seven pattern names present at this plugin's compile target are gone by 1.20.6, and a
     * preset an operator copied from a newer server can name something this one has never
     * heard of. Either way the banner should still come out looking like something.
     */
    private static void add(final List<Pattern> patterns, final DyeColor colour,
        final String patternName)
    {
        final PatternType type = patternType(patternName);
        if (type == null)
        {
            PluginLog.log(Level.FINE, "Mirror preset names pattern '" + patternName
                + "', which this server does not have; skipping that layer.");
            return;
        }
        patterns.add(new Pattern(colour, type));
    }

    /**
     * A pattern type by name, or null if this server cannot give one.
     *
     * <p>Catching {@code LinkageError} alongside the exceptions is load-bearing rather than
     * defensive. From 1.21 on, {@code PatternType}'s own static initialiser builds its constants
     * out of {@code Registry}, which needs a running server -- so the first call on a server
     * that has not finished starting throws {@code ExceptionInInitializerError}, and every call
     * after it throws {@code NoClassDefFoundError}. Both are Errors, both would otherwise leave
     * a command handler by way of an exception nobody declared, and both mean exactly what a
     * missing pattern means to the caller: there is no type to be had, so skip the layer.
     *
     * <p>Caching that null is deliberate too. A class whose initialiser has failed once is
     * unusable for the life of the JVM, so there is nothing to be gained by asking again.
     *
     * @param name
     *            the pattern's name, as Bukkit spells it
     * @return the type, or null
     */
    static PatternType patternType(final String name)
    {
        if ((name == null) || name.isEmpty() || (VALUE_OF == null))
        {
            return null;
        }
        // computeIfAbsent would not cache a miss, so an unknown name in a preset would go
        // through reflection on every stamp. Two calls, and the null is remembered.
        if (RESOLVED.containsKey(name))
        {
            return RESOLVED.get(name);
        }
        PatternType type = null;
        try
        {
            type = (PatternType) VALUE_OF.invoke(null, name);
        }
        catch (final ReflectiveOperationException | RuntimeException | LinkageError notThere)
        {
            type = null;
        }
        RESOLVED.put(name, type);
        return type;
    }

    /** Looks up {@code PatternType.valueOf} once, whatever kind of type it is declared on. */
    private static Method findValueOf()
    {
        try
        {
            return PatternType.class.getMethod("valueOf", String.class);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError e)
        {
            PluginLog.log(Level.WARNING, "This server's PatternType has no usable"
                + " valueOf(String); mirrors will not be stamped with patterns.", e);
            return null;
        }
    }
}
