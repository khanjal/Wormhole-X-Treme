package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.wormhole_xtreme.wormhole.config.ConfigManager.ConfigKeys;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.model.MaterialGroup;
import com.wormhole_xtreme.wormhole.model.ring.RingAccess;
import com.wormhole_xtreme.wormhole.model.ring.RingStyle;

/**
 * Every per-setting accessor on {@link ConfigManager}, pinned before its body moves (#430).
 *
 * <p>Step 4 of #430 moves some eighty getters into holder classes, with ConfigManager delegating.
 * A move like that breaks quietly: a getter that reads its neighbour's key, a fallback literal
 * retyped as the shipped default, a clamp dropped on the way. Each still compiles and most still
 * look right on a server running the defaults. So each accessor is read four ways, every expected
 * value written here as a literal rather than computed from the code under test or from
 * {@link DefaultSettings}: with nothing configured, on the shipped defaults, with every setting
 * holding a value no other setting holds, and with an out-of-range value where it clamps.
 *
 * <p>The distinct-value phase is what catches a swapped key: two sibling getters reading each
 * other's settings agree on the defaults but not there. Switches only have two values, so they
 * are also flipped one at a time. And the table is held to ConfigManager by reflection, so an
 * accessor added later without a row fails here rather than going uncharacterised.
 *
 * <p>Every write puts a new {@link Setting} in the map rather than calling
 * {@link ConfigManager#setConfigValue}, so each case sets exactly the keys it names.
 */
class ConfigAccessorTableTest
{
    /** Neither of these is a pattern or animation the server is set to in any phase. */
    private static final MaterialGroup PEGASUS_ROWS_GROUP = new MaterialGroup("Pegasus", Material.STONE,
        Material.WATER, Material.GLASS, Material.GLOWSTONE, Material.OAK_WALL_SIGN)
        .withDialSpin(DialSpinPattern.PEGASUS).withIrisAnimation("rows");

    /** A group that says nothing, so the server's setting decides. */
    private static final MaterialGroup PLAIN_GROUP = new MaterialGroup("Plain", Material.STONE,
        Material.WATER, Material.GLASS, Material.GLOWSTONE, Material.OAK_WALL_SIGN);

    /**
     * The public statics that are not one setting's accessor, each with why.
     *
     * <p>Keyed by signature, as {@link #signature(String, List)} writes it.
     */
    private static final Map<String, String> EXCLUDED = excluded();

    /** One value per setting, none shared with another setting or equal to its own default. */
    private static final Map<ConfigKeys, Object> DISTINCT = distinct();

    /** Every accessor, with what it reads when empty, on the defaults, and on {@link #DISTINCT}. */
    private static final List<Row> ROWS = rows();

    /** Out-of-range and odd values, each written over the defaults. */
    private static final List<Odd> ODD = odd();

    @BeforeEach
    void startEmpty()
    {
        ConfigTestSupport.clear();
    }

    @AfterEach
    void leaveEmpty()
    {
        ConfigTestSupport.clear();
    }

    /**
     * One accessor call: a method on ConfigManager, by name and parameter types, with its arguments.
     *
     * @param label
     *            how a failure names it
     * @param name
     *            the method's name
     * @param params
     *            its parameter types
     * @param args
     *            what it is called with
     */
    private record Call(String label, String name, List<Class<?>> params, List<Object> args)
    {
        /** @return what the accessor answers now */
        Object invoke()
        {
            final Method method;
            try
            {
                method = ConfigManager.class.getMethod(name, params.toArray(new Class<?>[0]));
            }
            catch (final NoSuchMethodException gone)
            {
                throw new AssertionError(label + " is no longer on ConfigManager", gone);
            }
            try
            {
                return method.invoke(null, args.toArray());
            }
            catch (final InvocationTargetException thrown)
            {
                if (thrown.getCause() instanceof RuntimeException runtime)
                {
                    throw runtime;
                }
                throw new AssertionError(label + " threw", thrown.getCause());
            }
            catch (final IllegalAccessException hidden)
            {
                throw new AssertionError(label + " cannot be called", hidden);
            }
        }

        /** @return its signature, as {@link ConfigAccessorTableTest#signature(String, List)} writes it */
        String signature()
        {
            return ConfigAccessorTableTest.signature(name, params);
        }
    }

    /**
     * One accessor's three readings.
     *
     * @param call
     *            the accessor
     * @param key
     *            the setting it reads, or null for one derived from others
     * @param empty
     *            what it answers with nothing configured: its fallback literal
     * @param shipped
     *            what it answers on the shipped defaults
     * @param distinct
     *            what it answers with every setting set to its {@link #DISTINCT} value
     */
    private record Row(Call call, ConfigKeys key, Object empty, Object shipped, Object distinct)
    {
    }

    /**
     * One odd value and what an accessor makes of it.
     *
     * @param call
     *            the accessor
     * @param writes
     *            the settings written over the defaults
     * @param expected
     *            its answer, or the exception class it throws
     */
    private record Odd(Call call, Map<ConfigKeys, Object> writes, Object expected)
    {
        /** @return how a failure names it */
        String label()
        {
            return call.label() + " with " + writes;
        }
    }

    /** @return the signature of a method, such as {@code getGateIrisAnimation(String,MaterialGroup)} */
    private static String signature(final String name, final List<Class<?>> params)
    {
        return name + params.stream().map(Class::getSimpleName).collect(Collectors.joining(",", "(", ")"));
    }

    /** @return a call with no arguments */
    private static Call call(final String name)
    {
        return new Call(name + "()", name, List.of(), List.of());
    }

    /** @return a row for an accessor with no arguments */
    private static Row row(final String name, final ConfigKeys key, final Object empty, final Object shipped,
        final Object distinct)
    {
        return new Row(call(name), key, empty, shipped, distinct);
    }

    /** @return a row for {@code getGateDialSpinPattern(own, group)} */
    private static Row dialSpin(final String label, final DialSpinPattern own, final MaterialGroup group,
        final Object empty, final Object shipped, final Object distinct)
    {
        return new Row(new Call("getGateDialSpinPattern(" + label + ")", "getGateDialSpinPattern",
            List.of(DialSpinPattern.class, MaterialGroup.class), Arrays.asList(own, group)),
            ConfigKeys.GATE_DIAL_SPIN, empty, shipped, distinct);
    }

    /** @return the call {@code getGateIrisAnimation(own, group)} */
    private static Call irisCall(final String label, final String own, final MaterialGroup group)
    {
        return new Call("getGateIrisAnimation(" + label + ")", "getGateIrisAnimation",
            List.of(String.class, MaterialGroup.class), Arrays.asList(own, group));
    }

    /** @return the call {@code getGateView(own)} */
    private static Call viewCall(final String label, final String own)
    {
        return new Call("getGateView(" + label + ")", "getGateView", List.of(String.class), Arrays.asList(own));
    }

    /** @return a row for {@code isIrisAnimated(animation)}, which reads no setting */
    private static Row irisAnimated(final String animation, final boolean answer)
    {
        final Call call = new Call("isIrisAnimated(" + animation + ")", "isIrisAnimated", List.of(String.class),
            Arrays.asList(animation));
        return new Row(call, null, answer, answer, answer);
    }

    /** @return one odd value for one setting */
    private static Odd odd(final String name, final ConfigKeys key, final Object written, final Object expected)
    {
        return odd(call(name), key, written, expected);
    }

    /** @return one odd value for one setting, read through any call */
    private static Odd odd(final Call call, final ConfigKeys key, final Object written, final Object expected)
    {
        final Map<ConfigKeys, Object> writes = new LinkedHashMap<>();
        writes.put(key, written);
        return new Odd(call, writes, expected);
    }

    /** @return two settings written together, for an accessor that reads both */
    private static Odd odd(final String name, final ConfigKeys first, final Object firstValue, final ConfigKeys second,
        final Object secondValue, final Object expected)
    {
        final Map<ConfigKeys, Object> writes = new LinkedHashMap<>();
        writes.put(first, firstValue);
        writes.put(second, secondValue);
        return new Odd(call(name), writes, expected);
    }

    /** Writes one setting as a config file would have, typed by its value. */
    private static void write(final ConfigKeys key, final Object value)
    {
        if (value instanceof Boolean b)
        {
            ConfigTestSupport.set(key, b.booleanValue());
        }
        else if (value instanceof Integer i)
        {
            ConfigTestSupport.set(key, i.intValue());
        }
        else if (value instanceof Double d)
        {
            ConfigTestSupport.set(key, d.doubleValue());
        }
        else if (value instanceof String s)
        {
            ConfigTestSupport.set(key, s);
        }
        else
        {
            throw new IllegalArgumentException("no way to write " + value + " to " + key);
        }
    }

    /** @return an assertion that a call answers a value, named for the call and the phase */
    private static Executable reads(final Call call, final Object expected, final String phase)
    {
        return () -> assertEquals(expected, call.invoke(), call.label() + " " + phase);
    }

    /**
     * With nothing configured, as on a server whose config.yml lost a setting, every accessor
     * answers the literal written into it.
     */
    @Test
    void everyAccessorFallsBackToItsOwnLiteralWithNothingConfigured()
    {
        assertAll(ROWS.stream().map(row -> reads(row.call(), row.empty(), "with nothing configured")));
    }

    /**
     * On the shipped defaults every accessor answers what a new server gets. Mostly the same as
     * the fallback; the sign colours and {@code sign-glowing-text} are where the two differ.
     */
    @Test
    void everyAccessorReadsTheShippedDefault()
    {
        ConfigTestSupport.loadDefaults();
        assertAll(ROWS.stream().map(row -> reads(row.call(), row.shipped(), "on the shipped defaults")));
    }

    /**
     * With every setting holding a value no other one holds, each accessor answers its own: one that
     * reads a sibling's key answers the sibling's value instead. The two speeds are off 1.0 here, so
     * the ring and beam timings are also read scaled.
     */
    @Test
    void everyAccessorReadsItsOwnSettingWhenEverySettingDiffers()
    {
        DISTINCT.forEach(ConfigAccessorTableTest::write);
        assertAll(ROWS.stream().map(row -> reads(row.call(), row.distinct(), "with every setting distinct")));
    }

    /**
     * A switch has only two values, so two switches with the same default swapped would pass the
     * distinct phase. Flipped one at a time over the defaults, only that switch's accessor moves.
     */
    @Test
    void flippingOneSwitchMovesOnlyItsOwnAccessor()
    {
        final List<Row> switches = ROWS.stream()
            .filter(row -> (row.key() != null) && (row.shipped() instanceof Boolean))
            .toList();
        assertTrue(switches.size() >= 35, "only " + switches.size() + " switches found; the filter is wrong");
        final List<Executable> checks = new ArrayList<>();
        for (final Row flipped : switches)
        {
            checks.add(() ->
            {
                ConfigTestSupport.clear();
                ConfigTestSupport.loadDefaults();
                write(flipped.key(), flipped.distinct());
                for (final Row row : switches)
                {
                    final Object expected = (row.key() == flipped.key()) ? row.distinct() : row.shipped();
                    assertEquals(expected, row.call().invoke(),
                        row.call().label() + " with only " + flipped.key() + " flipped");
                }
            });
        }
        assertAll(checks);
    }

    /** Out-of-range, unknown and oddly written values, each over the defaults. */
    @Test
    void oddValuesAreClampedScaledOrRefusedAsBefore()
    {
        final List<Executable> checks = new ArrayList<>();
        for (final Odd odd : ODD)
        {
            checks.add(() ->
            {
                ConfigTestSupport.clear();
                ConfigTestSupport.loadDefaults();
                odd.writes().forEach(ConfigAccessorTableTest::write);
                if ((odd.expected() instanceof Class<?> type) && Throwable.class.isAssignableFrom(type))
                {
                    assertThrows(type.asSubclass(Throwable.class), odd.call()::invoke, odd.label() + " should throw");
                }
                else
                {
                    assertEquals(odd.expected(), odd.call().invoke(), odd.label());
                }
            });
        }
        assertAll(checks);
    }

    /**
     * Every public static on ConfigManager is either in the table or excluded with a reason, so a
     * getter added after this was written cannot go uncharacterised into the move.
     */
    @Test
    void theTableNamesEveryAccessorConfigManagerHas()
    {
        final Set<String> methods = publicStatics();
        assertTrue(methods.size() >= 120, "only " + methods.size() + " public statics seen on ConfigManager");

        final Set<String> accessors = new TreeSet<>(methods);
        accessors.removeAll(EXCLUDED.keySet());
        assertTrue(accessors.size() >= 100, "only " + accessors.size() + " accessors left after the exclusions");

        final Set<String> tabled = ROWS.stream().map(row -> row.call().signature()).collect(Collectors.toSet());
        final Set<String> missing = new TreeSet<>(accessors);
        missing.removeAll(tabled);
        assertTrue(missing.isEmpty(), "accessors with no row in the table: " + missing);
    }

    /** A row or an exclusion naming a method that is gone is out of date, not harmless. */
    @Test
    void theTableAndTheExclusionsNameNothingThatIsGone()
    {
        final Set<String> methods = publicStatics();
        final Set<String> stale = new TreeSet<>();
        ROWS.stream().map(row -> row.call().signature()).filter(sig -> !methods.contains(sig)).forEach(stale::add);
        ODD.stream().map(odd -> odd.call().signature()).filter(sig -> !methods.contains(sig)).forEach(stale::add);
        EXCLUDED.keySet().stream().filter(sig -> !methods.contains(sig)).forEach(stale::add);
        assertTrue(stale.isEmpty(), "named here but not a public static on ConfigManager: " + stale);
    }

    /**
     * The distinct phase only catches a swap if the fixture is what it says: every setting written,
     * no two settings sharing a value, none at its own default, and every setting read by a row.
     */
    @Test
    void theDistinctFixtureSetsEverySettingToAValueOfItsOwn()
    {
        final Set<ConfigKeys> unset = new TreeSet<>(Arrays.asList(ConfigKeys.values()));
        unset.removeAll(DISTINCT.keySet());
        assertTrue(unset.isEmpty(), "settings the distinct phase leaves unset: " + unset);

        final Map<Object, ConfigKeys> seen = new HashMap<>();
        final List<String> shared = new ArrayList<>();
        DISTINCT.forEach((key, value) ->
        {
            final ConfigKeys before = (value instanceof Boolean) ? null : seen.put(value, key);
            if (before != null)
            {
                shared.add(before + " and " + key + " are both " + value);
            }
        });
        assertTrue(shared.isEmpty(), "values shared between settings: " + shared);

        // An overload given its own pattern or group answers that, whatever the setting says.
        final List<String> unmoved = ROWS.stream()
            .filter(row -> (row.key() != null) && row.call().params().isEmpty())
            .filter(row -> row.distinct().equals(row.shipped()))
            .map(row -> row.call().label())
            .toList();
        assertTrue(unmoved.isEmpty(), "accessors the distinct phase does not move off the default: " + unmoved);

        final Set<ConfigKeys> unread = new TreeSet<>(Arrays.asList(ConfigKeys.values()));
        ROWS.stream().map(Row::key).filter(Objects::nonNull).forEach(unread::remove);
        assertTrue(unread.isEmpty(), "settings no row reads: " + unread);
    }

    /** @return the signature of every public static method declared on ConfigManager */
    private static Set<String> publicStatics()
    {
        final Set<String> found = new TreeSet<>();
        for (final Method method : ConfigManager.class.getDeclaredMethods())
        {
            final int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && !method.isSynthetic())
            {
                found.add(signature(method.getName(), Arrays.asList(method.getParameterTypes())));
            }
        }
        return found;
    }

    /** @return the public statics that are not one setting's accessor */
    private static Map<String, String> excluded()
    {
        final Map<String, String> skip = new LinkedHashMap<>();
        final String command = "the /wormhole config command's machinery, over every setting";
        skip.put("settingNames()", command);
        skip.put("settingNamesMatching(String)", command);
        skip.put("describeSetting(String)", command);
        skip.put("settingRefusal(String,String)", command);
        skip.put("applySetting(String,String)", command + "; writes config.yml");
        skip.put("settingRefusal(CommandSender,String,String)", command);
        skip.put("applySetting(CommandSender,String,String)", command + "; writes config.yml");
        skip.put("valuesFor(String)", "tab completion's choices, not a setting's value");
        skip.put("irisAnimations()", "the list of animation names, not a setting's value");
        skip.put("parseIrisAnimation(String)", "a parser over text it is given");
        skip.put("parseGateView(String)", "a parser over text it is given");
        skip.put("parseServerLanguage(String)", "a parser over text it is given");
        skip.put("appendDiscoveredMaterialGroups(List)", "writes config.yml");
        skip.put("setupConfigs(String)", "reads config.yml");
        final String setter = "a setter";
        skip.put("setConfigValue(ConfigKeys,Object)", setter);
        skip.put("setTimeoutActivate(int)", setter);
        skip.put("setTimeoutShutdown(int)", setter);
        skip.put("setUseCooldownEnabled(boolean)", setter);
        skip.put("setUseCooldownSeconds(int)", setter);
        return skip;
    }

    /** @return one value per setting: no two alike, none its own default, all inside any clamp */
    private static Map<ConfigKeys, Object> distinct()
    {
        final Map<ConfigKeys, Object> d = new EnumMap<>(ConfigKeys.class);
        // General
        d.put(ConfigKeys.LOG_LEVEL, "FINE");
        d.put(ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, true);
        d.put(ConfigKeys.PETS_FOLLOW_OWNER, false);
        d.put(ConfigKeys.CLICKABLE_CHAT, false);
        d.put(ConfigKeys.SERVER_LANGUAGE, "pt-BR");
        // Stargates
        d.put(ConfigKeys.TIMEOUT_ACTIVATE, 31);
        d.put(ConfigKeys.TIMEOUT_SHUTDOWN, 39);
        d.put(ConfigKeys.MAX_OPEN_SECONDS, 301);
        d.put(ConfigKeys.REDSTONE_EXTEND_OPEN_TIME, false);
        d.put(ConfigKeys.USE_COOLDOWN_ENABLED, true);
        d.put(ConfigKeys.USE_COOLDOWN_SECONDS, 121);
        d.put(ConfigKeys.WORMHOLE_USE_IS_TELEPORT, true);
        d.put(ConfigKeys.SAME_WORLD_ONLY, true);
        d.put(ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, true);
        d.put(ConfigKeys.GATE_DIAL_SPIN, "lap");
        d.put(ConfigKeys.GATE_RIPPLE, true);
        d.put(ConfigKeys.GATE_IRIS_ANIMATION, " Spiral ");
        d.put(ConfigKeys.MIN_GATE_DISTANCE, 13);
        d.put(ConfigKeys.MAX_GATES_PER_PLAYER, 7);
        d.put(ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, false);
        d.put(ConfigKeys.GATE_PREVIEW_MINUTES, 14);
        d.put(ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5001);
        // Gate views and mirrors
        d.put(ConfigKeys.VIEW_DEPTH, 20);
        d.put(ConfigKeys.GATE_VIEW, "behind");
        d.put(ConfigKeys.GATE_VIEW_FUNNEL, false);
        d.put(ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 17);
        d.put(ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 15);
        d.put(ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 18);
        d.put(ConfigKeys.MIRROR_SHOW_ENTITIES, true);
        d.put(ConfigKeys.MIRROR_SHOW_PLAYERS, true);
        d.put(ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 3);
        d.put(ConfigKeys.MIRROR_PER_WORLD_LIMIT, 6);
        d.put(ConfigKeys.MIRROR_APPROACH_MESSAGE, false);
        // Gate signs
        d.put(ConfigKeys.SIGN_GLOWING_TEXT, true);
        d.put(ConfigKeys.SIGN_DIAL_MATCH_MATERIAL, false);
        d.put(ConfigKeys.SIGN_COLOR_GATE_NAME, " GOLD ");
        d.put(ConfigKeys.SIGN_COLOR_NETWORK, "AQUA");
        d.put(ConfigKeys.SIGN_COLOR_OWNER, "BLUE");
        d.put(ConfigKeys.SIGN_COLOR_SELECTED, "RED");
        d.put(ConfigKeys.SIGN_COLOR_NEIGHBOUR, "WHITE");
        // Transport rings
        d.put(ConfigKeys.RING_COOLDOWN_TICKS, 601);
        d.put(ConfigKeys.RING_ANIMATION_SPEED, 1.25);
        d.put(ConfigKeys.RING_OUTLINE_ON_REFUSAL, false);
        d.put(ConfigKeys.RING_REACH, 5);
        d.put(ConfigKeys.RING_MIN_SEPARATION, 9);
        d.put(ConfigKeys.RING_MAX_LINK_DISTANCE, 257);
        d.put(ConfigKeys.RING_MAX_CEILING_DROP, 11);
        d.put(ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 12);
        d.put(ConfigKeys.RING_DEFAULT_ACCESS, "public");
        d.put(ConfigKeys.RING_DEFAULT_STYLE, "sequential");
        d.put(ConfigKeys.RING_DEFAULT_LIGHT, "sea_lantern");
        d.put(ConfigKeys.RING_DEFAULT_FLASH, "SHROOMLIGHT");
        // Beaming
        d.put(ConfigKeys.BEAM_ANIMATION_SPEED, 0.75);
        d.put(ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, true);
        d.put(ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 122);
        d.put(ConfigKeys.BEAM_ECONOMY_USE_COST, 2.5);
        // Economy
        d.put(ConfigKeys.ECONOMY_ENABLED, true);
        d.put(ConfigKeys.ECONOMY_USE_COST, 3.5);
        d.put(ConfigKeys.ECONOMY_BUILD_COST, 4.5);
        // Stargate sounds
        d.put(ConfigKeys.GATE_SOUNDS_ENABLED, false);
        d.put(ConfigKeys.GATE_SOUND_VOLUME, 1.75);
        d.put(ConfigKeys.GATE_SOUND_ACTIVATE, "test.gate.activate");
        d.put(ConfigKeys.GATE_SOUND_CHEVRON, "test.gate.chevron");
        d.put(ConfigKeys.GATE_SOUND_LOCK, "test.gate.lock");
        d.put(ConfigKeys.GATE_SOUND_KAWOOSH, "test.gate.kawoosh");
        d.put(ConfigKeys.GATE_SOUND_AMBIENT, "test.gate.ambient");
        d.put(ConfigKeys.GATE_SOUND_CLOSE, "test.gate.close");
        d.put(ConfigKeys.GATE_SOUND_IRIS_CLOSE, "test.gate.iris_close");
        d.put(ConfigKeys.GATE_SOUND_IRIS_OPEN, "test.gate.iris_open");
        // Transport ring sounds
        d.put(ConfigKeys.RING_SOUNDS_ENABLED, false);
        d.put(ConfigKeys.RING_SOUND_VOLUME, 0.625);
        d.put(ConfigKeys.RING_SOUND_OPEN, " test.ring.open ");
        d.put(ConfigKeys.RING_SOUND_RING, "test.ring.ring");
        d.put(ConfigKeys.RING_SOUND_FLASH, "test.ring.flash");
        d.put(ConfigKeys.RING_SOUND_CLOSE, "test.ring.close");
        d.put(ConfigKeys.RING_SOUND_REFUSED, "test.ring.refused");
        // Beaming sounds
        d.put(ConfigKeys.BEAM_SOUNDS_ENABLED, false);
        d.put(ConfigKeys.BEAM_SOUND_VOLUME, 0.375);
        d.put(ConfigKeys.BEAM_SOUND_CHARGE, "test.beam.charge");
        d.put(ConfigKeys.BEAM_SOUND_DEPART, "test.beam.depart");
        d.put(ConfigKeys.BEAM_SOUND_ARRIVE, "test.beam.arrive");
        // Integrations
        d.put(ConfigKeys.COREPROTECT_ENABLED, true);
        d.put(ConfigKeys.WORLDGUARD_ENABLED, true);
        d.put(ConfigKeys.DYNMAP_ENABLED, true);
        d.put(ConfigKeys.BLUEMAP_ENABLED, true);
        d.put(ConfigKeys.SQUAREMAP_ENABLED, true);
        d.put(ConfigKeys.PL3XMAP_ENABLED, true);
        d.put(ConfigKeys.MAP_SHOW_GATES, false);
        d.put(ConfigKeys.MAP_SHOW_RINGS, false);
        d.put(ConfigKeys.MAP_SHOW_BEAMS, false);
        d.put(ConfigKeys.MAP_SHOW_MIRRORS, false);
        d.put(ConfigKeys.MAP_SHOW_IRIS_GATES, false);
        d.put(ConfigKeys.PLACEHOLDERS_ENABLED, true);
        d.put(ConfigKeys.METRICS_ENABLED, false);
        d.put(ConfigKeys.UPDATE_CHECK, false);
        return d;
    }

    /**
     * @return every accessor: name, the setting it reads, then its answer with nothing configured,
     *         on the shipped defaults, and on {@link #DISTINCT}
     */
    private static List<Row> rows()
    {
        return List.of(
            // General
            row("getLogLevel", ConfigKeys.LOG_LEVEL, Level.INFO, Level.INFO, Level.FINE),
            row("getPermissionsSupportDisable", ConfigKeys.PERMISSIONS_SUPPORT_DISABLE, false, false, true),
            row("isPetsFollowOwner", ConfigKeys.PETS_FOLLOW_OWNER, true, true, false),
            row("isClickableChat", ConfigKeys.CLICKABLE_CHAT, true, true, false),
            row("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, "en", "en", "pt_br"),

            // Stargates
            row("getTimeoutActivate", ConfigKeys.TIMEOUT_ACTIVATE, 30, 30, 31),
            row("getTimeoutShutdown", ConfigKeys.TIMEOUT_SHUTDOWN, 38, 38, 39),
            row("getMaxOpenSeconds", ConfigKeys.MAX_OPEN_SECONDS, 300, 300, 301),
            row("isRedstoneExtendOpenTime", ConfigKeys.REDSTONE_EXTEND_OPEN_TIME, true, true, false),
            row("isUseCooldownEnabled", ConfigKeys.USE_COOLDOWN_ENABLED, false, false, true),
            row("getUseCooldownSeconds", ConfigKeys.USE_COOLDOWN_SECONDS, 120, 120, 121),
            row("getWormholeUseIsTeleport", ConfigKeys.WORMHOLE_USE_IS_TELEPORT, false, false, true),
            row("isSameWorldOnly", ConfigKeys.SAME_WORLD_ONLY, false, false, true),
            row("isShowGateWelcomeMessage", ConfigKeys.SHOW_GATE_WELCOME_MESSAGE, false, false, true),
            row("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN,
                DialSpinPattern.NONE, DialSpinPattern.NONE, DialSpinPattern.LAP),
            dialSpin("CHASE, Pegasus group", DialSpinPattern.CHASE, PEGASUS_ROWS_GROUP,
                DialSpinPattern.CHASE, DialSpinPattern.CHASE, DialSpinPattern.CHASE),
            dialSpin("null, Pegasus group", null, PEGASUS_ROWS_GROUP,
                DialSpinPattern.PEGASUS, DialSpinPattern.PEGASUS, DialSpinPattern.PEGASUS),
            dialSpin("null, plain group", null, PLAIN_GROUP,
                DialSpinPattern.NONE, DialSpinPattern.NONE, DialSpinPattern.LAP),
            dialSpin("null, null", null, null, DialSpinPattern.NONE, DialSpinPattern.NONE, DialSpinPattern.LAP),
            row("isGateRipple", ConfigKeys.GATE_RIPPLE, false, false, true),
            new Row(irisCall("instant, rows group", "instant", PEGASUS_ROWS_GROUP), ConfigKeys.GATE_IRIS_ANIMATION,
                "instant", "instant", "instant"),
            new Row(irisCall("null, rows group", null, PEGASUS_ROWS_GROUP), ConfigKeys.GATE_IRIS_ANIMATION,
                "rows", "rows", "rows"),
            new Row(irisCall("null, plain group", null, PLAIN_GROUP), ConfigKeys.GATE_IRIS_ANIMATION,
                "sweep", "sweep", "spiral"),
            new Row(irisCall("null, null", null, null), ConfigKeys.GATE_IRIS_ANIMATION, "sweep", "sweep", "spiral"),
            irisAnimated("instant", false),
            irisAnimated("INSTANT", false),
            irisAnimated("sweep", true),
            irisAnimated(null, true),
            row("getMinGateDistance", ConfigKeys.MIN_GATE_DISTANCE, 0, 0, 13),
            row("getMaxGatesPerPlayer", ConfigKeys.MAX_GATES_PER_PLAYER, 0, 0, 7),
            row("isGateMaterialGroupsAutodiscover", ConfigKeys.GATE_MATERIAL_GROUPS_AUTODISCOVER, true, true, false),
            row("getGatePreviewMinutes", ConfigKeys.GATE_PREVIEW_MINUTES, 10, 10, 14),
            row("getGatePreviewMaxBlocks", ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, 5000, 5000, 5001),

            // Gate views and mirrors
            row("getViewDepth", ConfigKeys.VIEW_DEPTH, 160, 160, 20),
            row("getGateViewFirstDepth", null, 32, 32, 20),
            row("getGateView", ConfigKeys.GATE_VIEW, "horizon", "horizon", "behind"),
            // A gate's own level wins either way, so the server's setting changes nothing when it has one.
            new Row(viewCall("null", null), ConfigKeys.GATE_VIEW, "horizon", "horizon", "behind"),
            new Row(viewCall("horizon", "horizon"), ConfigKeys.GATE_VIEW, "horizon", "horizon", "horizon"),
            new Row(viewCall("open", "open"), ConfigKeys.GATE_VIEW, "open", "open", "open"),
            row("isGateViewFunnel", ConfigKeys.GATE_VIEW_FUNNEL, true, true, false),
            row("getGateViewFunnelLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 16, 16, 17),
            row("getGateViewFunnelMinLength", ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 0, 0, 15),
            row("getGateViewFunnelLongest", null, 16, 16, 0),
            row("getMirrorProximityDistance", ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 16, 16, 18),
            row("isMirrorShowEntities", ConfigKeys.MIRROR_SHOW_ENTITIES, false, false, true),
            row("isMirrorShowPlayers", ConfigKeys.MIRROR_SHOW_PLAYERS, false, false, true),
            row("getMirrorEntityLoadRadius", ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 2, 2, 3),
            row("getMirrorPerWorldLimit", ConfigKeys.MIRROR_PER_WORLD_LIMIT, 1, 1, 6),
            row("isMirrorApproachMessage", ConfigKeys.MIRROR_APPROACH_MESSAGE, true, true, false),

            // Gate signs: the getter's fallback is not the shipped default for any of these
            row("isSignGlowingText", ConfigKeys.SIGN_GLOWING_TEXT, true, false, true),
            row("isSignDialMatchMaterial", ConfigKeys.SIGN_DIAL_MATCH_MATERIAL, true, true, false),
            row("getSignColorGateName", ConfigKeys.SIGN_COLOR_GATE_NAME, "", "DARK_AQUA", "GOLD"),
            row("getSignColorNetwork", ConfigKeys.SIGN_COLOR_NETWORK, "", "GRAY", "AQUA"),
            row("getSignColorOwner", ConfigKeys.SIGN_COLOR_OWNER, "", "GRAY", "BLUE"),
            row("getSignColorSelected", ConfigKeys.SIGN_COLOR_SELECTED, "", "DARK_GREEN", "RED"),
            row("getSignColorNeighbour", ConfigKeys.SIGN_COLOR_NEIGHBOUR, "", "GRAY", "WHITE"),

            // Transport rings; the timings are at ring-animation-speed, 1.25 in the distinct phase
            row("getRingCooldownTicks", ConfigKeys.RING_COOLDOWN_TICKS, 600, 600, 601),
            row("getRingAnimationSpeed", ConfigKeys.RING_ANIMATION_SPEED, 1.0, 1.0, 1.25),
            row("getRingCountdownTicks", null, 100, 100, 80),
            row("getRingDeployTicks", null, 2, 2, 2),
            row("getRingSettleTicks", null, 20, 20, 16),
            row("getRingFlashTicks", null, 3, 3, 2),
            row("getRingLightsLingerTicks", null, 20, 20, 16),
            row("getRingOutlineTicks", null, 40, 40, 32),
            row("getRingHoldTicks", null, 20, 20, 16),
            row("isRingOutlineOnRefusal", ConfigKeys.RING_OUTLINE_ON_REFUSAL, true, true, false),
            row("getRingReach", ConfigKeys.RING_REACH, 4, 4, 5),
            row("getRingMinSeparation", ConfigKeys.RING_MIN_SEPARATION, 8, 8, 9),
            row("getRingMaxLinkDistance", ConfigKeys.RING_MAX_LINK_DISTANCE, 256, 256, 257),
            row("getRingMaxCeilingDrop", ConfigKeys.RING_MAX_CEILING_DROP, 10, 10, 11),
            row("getRingMaxPairsPerPlayer", ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, 10, 10, 12),
            row("getRingDefaultAccess", ConfigKeys.RING_DEFAULT_ACCESS,
                RingAccess.PRIVATE, RingAccess.PRIVATE, RingAccess.PUBLIC),
            row("getRingDefaultStyle", ConfigKeys.RING_DEFAULT_STYLE,
                RingStyle.CONCURRENT, RingStyle.CONCURRENT, RingStyle.SEQUENTIAL),
            row("getRingDefaultLight", ConfigKeys.RING_DEFAULT_LIGHT,
                Material.REDSTONE_LAMP, Material.REDSTONE_LAMP, Material.SEA_LANTERN),
            row("getRingDefaultFlash", ConfigKeys.RING_DEFAULT_FLASH,
                Material.GLOWSTONE, Material.GLOWSTONE, Material.SHROOMLIGHT),

            // Beaming; the timings are at beam-animation-speed, 0.75 in the distinct phase
            row("getBeamAnimationSpeed", ConfigKeys.BEAM_ANIMATION_SPEED, 1.0, 1.0, 0.75),
            row("getBeamEnvelopTicks", null, 12, 12, 16),
            row("getBeamVanishAtStep", null, 6, 6, 8),
            row("getBeamRiseTicks", null, 18, 18, 24),
            row("getBeamTeleportAtStep", null, 12, 12, 16),
            row("getBeamDescendTicks", null, 20, 20, 27),
            row("getBeamFadeTicks", null, 8, 8, 11),
            row("isBeamUseCooldownEnabled", ConfigKeys.BEAM_USE_COOLDOWN_ENABLED, false, false, true),
            row("getBeamUseCooldownSeconds", ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, 120L, 120L, 122L),
            row("getBeamEconomyUseCost", ConfigKeys.BEAM_ECONOMY_USE_COST, 0.0, 0.0, 2.5),

            // Economy
            row("isEconomyEnabled", ConfigKeys.ECONOMY_ENABLED, false, false, true),
            row("getEconomyUseCost", ConfigKeys.ECONOMY_USE_COST, 0.0, 0.0, 3.5),
            row("getEconomyBuildCost", ConfigKeys.ECONOMY_BUILD_COST, 0.0, 0.0, 4.5),

            // Stargate sounds
            row("isGateSoundsEnabled", ConfigKeys.GATE_SOUNDS_ENABLED, true, true, false),
            row("getGateSoundVolume", ConfigKeys.GATE_SOUND_VOLUME, 1.5f, 1.5f, 1.75f),
            row("getGateSoundActivate", ConfigKeys.GATE_SOUND_ACTIVATE,
                "block.conduit.activate", "block.conduit.activate", "test.gate.activate"),
            row("getGateSoundChevron", ConfigKeys.GATE_SOUND_CHEVRON,
                "block.iron_trapdoor.close", "block.iron_trapdoor.close", "test.gate.chevron"),
            row("getGateSoundLock", ConfigKeys.GATE_SOUND_LOCK,
                "block.beacon.power_select", "block.beacon.power_select", "test.gate.lock"),
            row("getGateSoundKawoosh", ConfigKeys.GATE_SOUND_KAWOOSH,
                "entity.player.splash.high_speed", "entity.player.splash.high_speed", "test.gate.kawoosh"),
            row("getGateSoundAmbient", ConfigKeys.GATE_SOUND_AMBIENT,
                "ambient.underwater.loop", "ambient.underwater.loop", "test.gate.ambient"),
            row("getGateSoundClose", ConfigKeys.GATE_SOUND_CLOSE,
                "block.conduit.deactivate", "block.conduit.deactivate", "test.gate.close"),
            row("getGateSoundIrisClose", ConfigKeys.GATE_SOUND_IRIS_CLOSE,
                "block.iron_door.close", "block.iron_door.close", "test.gate.iris_close"),
            row("getGateSoundIrisOpen", ConfigKeys.GATE_SOUND_IRIS_OPEN,
                "block.iron_door.open", "block.iron_door.open", "test.gate.iris_open"),

            // Transport ring sounds
            row("isRingSoundsEnabled", ConfigKeys.RING_SOUNDS_ENABLED, true, true, false),
            row("getRingSoundVolume", ConfigKeys.RING_SOUND_VOLUME, 1.0f, 1.0f, 0.625f),
            row("getRingSoundOpen", ConfigKeys.RING_SOUND_OPEN,
                "block.beacon.activate", "block.beacon.activate", "test.ring.open"),
            row("getRingSoundRing", ConfigKeys.RING_SOUND_RING,
                "block.piston.extend", "block.piston.extend", "test.ring.ring"),
            row("getRingSoundFlash", ConfigKeys.RING_SOUND_FLASH,
                "block.beacon.power_select", "block.beacon.power_select", "test.ring.flash"),
            row("getRingSoundClose", ConfigKeys.RING_SOUND_CLOSE,
                "block.beacon.deactivate", "block.beacon.deactivate", "test.ring.close"),
            row("getRingSoundRefused", ConfigKeys.RING_SOUND_REFUSED,
                "block.note_block.bass", "block.note_block.bass", "test.ring.refused"),

            // Beaming sounds
            row("isBeamSoundsEnabled", ConfigKeys.BEAM_SOUNDS_ENABLED, true, true, false),
            row("getBeamSoundVolume", ConfigKeys.BEAM_SOUND_VOLUME, 1.0f, 1.0f, 0.375f),
            row("getBeamSoundCharge", ConfigKeys.BEAM_SOUND_CHARGE,
                "block.respawn_anchor.charge", "block.respawn_anchor.charge", "test.beam.charge"),
            row("getBeamSoundDepart", ConfigKeys.BEAM_SOUND_DEPART,
                "entity.enderman.teleport", "entity.enderman.teleport", "test.beam.depart"),
            row("getBeamSoundArrive", ConfigKeys.BEAM_SOUND_ARRIVE,
                "entity.shulker.teleport", "entity.shulker.teleport", "test.beam.arrive"),

            // Integrations
            row("isCoreProtectEnabled", ConfigKeys.COREPROTECT_ENABLED, false, false, true),
            row("isWorldGuardEnabled", ConfigKeys.WORLDGUARD_ENABLED, false, false, true),
            row("isDynmapEnabled", ConfigKeys.DYNMAP_ENABLED, false, false, true),
            row("isBlueMapEnabled", ConfigKeys.BLUEMAP_ENABLED, false, false, true),
            row("isSquaremapEnabled", ConfigKeys.SQUAREMAP_ENABLED, false, false, true),
            row("isPl3xMapEnabled", ConfigKeys.PL3XMAP_ENABLED, false, false, true),
            row("isMapShowGates", ConfigKeys.MAP_SHOW_GATES, true, true, false),
            row("isMapShowRings", ConfigKeys.MAP_SHOW_RINGS, true, true, false),
            row("isMapShowBeams", ConfigKeys.MAP_SHOW_BEAMS, true, true, false),
            row("isMapShowMirrors", ConfigKeys.MAP_SHOW_MIRRORS, true, true, false),
            row("isMapShowIrisGates", ConfigKeys.MAP_SHOW_IRIS_GATES, true, true, false),
            row("isPlaceholdersEnabled", ConfigKeys.PLACEHOLDERS_ENABLED, false, false, true),
            row("isMetricsEnabled", ConfigKeys.METRICS_ENABLED, true, true, false),
            row("isUpdateCheckEnabled", ConfigKeys.UPDATE_CHECK, true, true, false));
    }

    /** @return the odd values: clamps, floors, speeds off 1.0, unknown names and loose spellings */
    private static List<Odd> odd()
    {
        final List<Odd> o = new ArrayList<>();
        // Unclamped, and meant to stay so through the move
        o.add(odd("getTimeoutActivate", ConfigKeys.TIMEOUT_ACTIVATE, -5, -5));
        o.add(odd("getMaxOpenSeconds", ConfigKeys.MAX_OPEN_SECONDS, -5, -5));
        o.add(odd("getUseCooldownSeconds", ConfigKeys.USE_COOLDOWN_SECONDS, -5, -5));
        o.add(odd("getBeamUseCooldownSeconds", ConfigKeys.BEAM_USE_COOLDOWN_SECONDS, -5, -5L));
        o.add(odd("getGateSoundVolume", ConfigKeys.GATE_SOUND_VOLUME, 9.0, 9.0f));
        o.add(odd("getEconomyUseCost", ConfigKeys.ECONOMY_USE_COST, -1.5, -1.5));

        // General
        o.add(odd("getLogLevel", ConfigKeys.LOG_LEVEL, "FINEST", Level.FINEST));
        o.add(odd("getLogLevel", ConfigKeys.LOG_LEVEL, "800", Level.INFO));
        o.add(odd("getLogLevel", ConfigKeys.LOG_LEVEL, "bogus", IllegalArgumentException.class));
        o.add(odd("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, " DE ", "de"));
        o.add(odd("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, "true", "en"));
        o.add(odd("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, true, "en"));
        o.add(odd("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, "x!", "en"));
        o.add(odd("getServerLanguage", ConfigKeys.SERVER_LANGUAGE, "", "en"));

        // Stargates
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, "true", DialSpinPattern.TOP));
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, "false", DialSpinPattern.NONE));
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, false, DialSpinPattern.NONE));
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, "off", DialSpinPattern.NONE));
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, " chase ", DialSpinPattern.CHASE));
        o.add(odd("getGateDialSpinPattern", ConfigKeys.GATE_DIAL_SPIN, "bogus", DialSpinPattern.NONE));
        // Not checked against the animations there are: an unknown one comes back as written
        o.add(odd(irisCall("null, null", null, null), ConfigKeys.GATE_IRIS_ANIMATION, " BOGUS ", "bogus"));
        o.add(odd("getMinGateDistance", ConfigKeys.MIN_GATE_DISTANCE, -5, 0));
        o.add(odd("getMaxGatesPerPlayer", ConfigKeys.MAX_GATES_PER_PLAYER, -5, 0));
        o.add(odd("getGatePreviewMinutes", ConfigKeys.GATE_PREVIEW_MINUTES, 0, 1));
        o.add(odd("getGatePreviewMinutes", ConfigKeys.GATE_PREVIEW_MINUTES, -5, 1));
        o.add(odd("getGatePreviewMaxBlocks", ConfigKeys.GATE_PREVIEW_MAX_BLOCKS, -5, 0));

        // Gate views and mirrors
        o.add(odd("getViewDepth", ConfigKeys.VIEW_DEPTH, 0, 4));
        o.add(odd("getViewDepth", ConfigKeys.VIEW_DEPTH, 1000, 160));
        o.add(odd("getViewDepth", ConfigKeys.VIEW_DEPTH, 100, 100));
        o.add(odd("getGateViewFirstDepth", ConfigKeys.VIEW_DEPTH, 100, 32));
        o.add(odd("getGateViewFirstDepth", ConfigKeys.VIEW_DEPTH, 10, 10));
        o.add(odd("getGateViewFirstDepth", ConfigKeys.VIEW_DEPTH, 0, 4));
        o.add(odd("getGateView", ConfigKeys.GATE_VIEW, " OPEN ", "open"));
        o.add(odd("getGateView", ConfigKeys.GATE_VIEW, "bogus", "horizon"));
        o.add(odd("getGateViewFunnelLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 0, 1));
        o.add(odd("getGateViewFunnelLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, -5, 1));
        o.add(odd("getGateViewFunnelLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 100, 64));
        o.add(odd("getGateViewFunnelMinLength", ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, -5, 0));
        o.add(odd("getGateViewFunnelMinLength", ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 100, 16));
        o.add(odd("getGateViewFunnelMinLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 5,
            ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 10, 5));
        o.add(odd("getGateViewFunnelMinLength", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 100,
            ConfigKeys.GATE_VIEW_FUNNEL_MIN_LENGTH, 70, 64));
        o.add(odd("getGateViewFunnelLongest", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 40, 40));
        o.add(odd("getGateViewFunnelLongest", ConfigKeys.GATE_VIEW_FUNNEL_LENGTH, 100, 64));
        o.add(odd("getGateViewFunnelLongest", ConfigKeys.GATE_VIEW_FUNNEL, false, 0));
        o.add(odd("getMirrorProximityDistance", ConfigKeys.MIRROR_PROXIMITY_DISTANCE, 0, 1));
        o.add(odd("getMirrorProximityDistance", ConfigKeys.MIRROR_PROXIMITY_DISTANCE, -5, 1));
        o.add(odd("getMirrorPerWorldLimit", ConfigKeys.MIRROR_PER_WORLD_LIMIT, -5, 0));
        o.add(odd("getMirrorPerWorldLimit", ConfigKeys.MIRROR_PER_WORLD_LIMIT, 0, 0));
        o.add(odd("getMirrorEntityLoadRadius", ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, 9, 4));
        o.add(odd("getMirrorEntityLoadRadius", ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, -5, 0));
        o.add(odd("getMirrorEntityLoadRadius", ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, " 3 ", 3));
        // Not "two": that warns once per JVM, and FarChunkHoldsTest counts the warning.
        o.add(odd("getMirrorEntityLoadRadius", ConfigKeys.MIRROR_ENTITY_LOAD_RADIUS, "9", 4));

        // Gate signs
        o.add(odd("getSignColorOwner", ConfigKeys.SIGN_COLOR_OWNER, "  light_purple ", "light_purple"));

        // Transport rings
        o.add(odd("getRingCooldownTicks", ConfigKeys.RING_COOLDOWN_TICKS, -5, 0));
        o.add(odd("getRingReach", ConfigKeys.RING_REACH, 1, 2));
        o.add(odd("getRingReach", ConfigKeys.RING_REACH, -5, 2));
        o.add(odd("getRingMinSeparation", ConfigKeys.RING_MIN_SEPARATION, -5, 0));
        o.add(odd("getRingMaxLinkDistance", ConfigKeys.RING_MAX_LINK_DISTANCE, -5, 0));
        o.add(odd("getRingMaxLinkDistance", ConfigKeys.RING_MAX_LINK_DISTANCE, 100000, 100000));
        o.add(odd("getRingMaxPairsPerPlayer", ConfigKeys.RING_MAX_PAIRS_PER_PLAYER, -5, 0));
        o.add(odd("getRingMaxCeilingDrop", ConfigKeys.RING_MAX_CEILING_DROP, 0, 3));
        o.add(odd("getRingMaxCeilingDrop", ConfigKeys.RING_MAX_CEILING_DROP, 2, 3));
        o.add(odd("getRingMaxCeilingDrop", ConfigKeys.RING_MAX_CEILING_DROP, 4, 4));
        o.add(odd("getRingDefaultAccess", ConfigKeys.RING_DEFAULT_ACCESS, "Public", RingAccess.PUBLIC));
        o.add(odd("getRingDefaultAccess", ConfigKeys.RING_DEFAULT_ACCESS, "bogus", RingAccess.PRIVATE));
        o.add(odd("getRingDefaultStyle", ConfigKeys.RING_DEFAULT_STYLE, "bogus", RingStyle.CONCURRENT));
        o.add(odd("getRingDefaultLight", ConfigKeys.RING_DEFAULT_LIGHT, "not_a_block", Material.REDSTONE_LAMP));
        o.add(odd("getRingDefaultFlash", ConfigKeys.RING_DEFAULT_FLASH, "not_a_block", Material.GLOWSTONE));
        ringSpeed(o, 0.1, 0.5, 200, 4, 40, 6, 40, 80, 40);
        ringSpeed(o, 0.5, 0.5, 200, 4, 40, 6, 40, 80, 40);
        ringSpeed(o, 2.0, 2.0, 50, 1, 10, 2, 10, 20, 10);
        ringSpeed(o, 5.0, 2.0, 50, 1, 10, 2, 10, 20, 10);
        ringSpeed(o, Double.NaN, 1.0, 100, 2, 20, 3, 20, 40, 20);

        // Beaming
        beamSpeed(o, 0.1, 0.5, 24, 12, 36, 24, 40, 16);
        beamSpeed(o, 2.0, 2.0, 6, 3, 9, 6, 10, 4);
        beamSpeed(o, 5.0, 2.0, 6, 3, 9, 6, 10, 4);
        beamSpeed(o, Double.NaN, 1.0, 12, 6, 18, 12, 20, 8);

        // Sounds: none, in any capitals and with spaces round it, is silence
        o.add(odd("getGateSoundKawoosh", ConfigKeys.GATE_SOUND_KAWOOSH, "none", ""));
        o.add(odd("getRingSoundRefused", ConfigKeys.RING_SOUND_REFUSED, " NONE ", ""));
        o.add(odd("getBeamSoundArrive", ConfigKeys.BEAM_SOUND_ARRIVE, "  a.b  ", "a.b"));
        return o;
    }

    /** Adds the ring speed and every ring timing at one written {@code ring-animation-speed}. */
    private static void ringSpeed(final List<Odd> o, final double written, final double speed, final int countdown,
        final int deploy, final int settle, final int flash, final int linger, final int outline, final int hold)
    {
        final ConfigKeys key = ConfigKeys.RING_ANIMATION_SPEED;
        o.add(odd("getRingAnimationSpeed", key, written, speed));
        o.add(odd("getRingCountdownTicks", key, written, countdown));
        o.add(odd("getRingDeployTicks", key, written, deploy));
        o.add(odd("getRingSettleTicks", key, written, settle));
        o.add(odd("getRingFlashTicks", key, written, flash));
        o.add(odd("getRingLightsLingerTicks", key, written, linger));
        o.add(odd("getRingOutlineTicks", key, written, outline));
        o.add(odd("getRingHoldTicks", key, written, hold));
    }

    /** Adds the beam speed and every beam timing at one written {@code beam-animation-speed}. */
    private static void beamSpeed(final List<Odd> o, final double written, final double speed, final int envelop,
        final int vanish, final int rise, final int teleport, final int descend, final int fade)
    {
        final ConfigKeys key = ConfigKeys.BEAM_ANIMATION_SPEED;
        o.add(odd("getBeamAnimationSpeed", key, written, speed));
        o.add(odd("getBeamEnvelopTicks", key, written, envelop));
        o.add(odd("getBeamVanishAtStep", key, written, vanish));
        o.add(odd("getBeamRiseTicks", key, written, rise));
        o.add(odd("getBeamTeleportAtStep", key, written, teleport));
        o.add(odd("getBeamDescendTicks", key, written, descend));
        o.add(odd("getBeamFadeTicks", key, written, fade));
    }
}
