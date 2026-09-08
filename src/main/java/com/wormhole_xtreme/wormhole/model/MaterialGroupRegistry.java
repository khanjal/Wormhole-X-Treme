package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.utils.YamlMaps;

/**
 * Holds the {@link MaterialGroup}s defined in config.yml, in declaration order.
 *
 * <p>The first group declared is the default: it is what a gate uses when nothing more
 * specific applies, and what the built-in fallback provides if config.yml defines none.
 *
 * <p>Lookups by structure material are the hot path — gate detection calls
 * {@link #getGroupByStructureMaterial(Material)} once per candidate shape — so they run
 * against a prebuilt map and are O(1) in the number of groups. This is the whole point of
 * the design: a server can define twenty palettes without making gate detection any
 * slower, whereas twenty variant {@code .shape} files would multiply detection cost by
 * twenty.
 *
 * <p>Everything a reader needs lives in one immutable {@link Snapshot}, held in a single
 * {@link AtomicReference}. Readers never lock, and writers replace the whole thing rather
 * than updating it in place. Loading happens once at startup and on an explicit reload.
 */
public final class MaterialGroupRegistry
{
    private static final String GROUP_PREFIX = "Material group \"";

    /**
     * Everything a reader needs, in one object so it can be swapped in one write.
     *
     * <p>These three were three separate {@code volatile} fields, assigned one after another
     * at the end of a load. Three writes are three chances to be read between: a reader
     * arriving mid-reload could get the new groups with the old default, or find a palette by
     * name that {@link #getGroupByStructureMaterial} did not yet know about.
     *
     * <p>No such reader exists today. Nothing in this plugin runs off the main thread -- there
     * is no async task in it anywhere -- so nothing reads while a load writes, and none of
     * that could actually happen. What was wrong was the disagreement: {@code volatile} says
     * cross-thread reads are expected, three separate writes say they are not, and both
     * cannot be right. This settles it the safe way round rather than by deleting the
     * {@code volatile} and betting the plugin stays single-threaded, and it costs a reference
     * read on a path that was already doing one.
     *
     * <p>Both maps are stored unmodifiable and never touched after construction, so the
     * snapshot a reader is holding stays the snapshot it read.
     *
     * @param byName
     *            groups by lower-cased name, in declaration order
     * @param byStructureMaterial
     *            groups by frame material, for O(1) detection
     * @param defaultGroup
     *            the first declared group, or null before anything has been loaded
     */
    private record Snapshot(Map<String, MaterialGroup> byName,
        Map<Material, MaterialGroup> byStructureMaterial,
        MaterialGroup defaultGroup)
    {
    }

    /** The one thing every reader reads and every writer replaces. */
    private static final AtomicReference<Snapshot> STATE =
        new AtomicReference<>(new Snapshot(Collections.emptyMap(), Collections.emptyMap(), null));

    private MaterialGroupRegistry() {}

    /**
     * Gets the default group — the first one declared in config.yml.
     *
     * @return the default group, never null once {@link #load(Map)} has run
     */
    public static MaterialGroup getDefaultGroup()
    {
        return STATE.get().defaultGroup();
    }

    /**
     * Gets a group by name.
     *
     * @param name
     *            the group name, case-insensitive
     * @return the group, or null if no such group is defined
     */
    public static MaterialGroup getGroup(final String name)
    {
        if (name == null)
        {
            return null;
        }
        return STATE.get().byName().get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the group whose frame is built from {@code material}.
     *
     * <p>This is the detection entry point: read the material of a candidate gate's frame
     * block out of the world, and this says which palette — if any — that gate belongs to.
     *
     * @param material
     *            the frame material found in the world
     * @return the matching group, or null if no group uses that material
     */
    public static MaterialGroup getGroupByStructureMaterial(final Material material)
    {
        if (material == null)
        {
            return null;
        }
        return STATE.get().byStructureMaterial().get(material);
    }

    /**
     * Gets every defined group, in declaration order.
     *
     * @return the groups
     */
    public static Collection<MaterialGroup> getGroups()
    {
        return STATE.get().byName().values();
    }

    /**
     * Reads one configured material group, or says why it cannot be used.
     *
     * <p>Three separate refusals, each worth its own message to whoever wrote the config: the
     * entry is not a mapping at all; it has no readable structure material; or its structure
     * material is already another group's. That last one matters because a frame material is
     * how a gate is identified -- two groups claiming obsidian would make every obsidian gate
     * ambiguous, so the second one is dropped rather than allowed to shadow the first.
     *
     * @param entry
     *            one group's name and its mapping of materials
     * @param claimed
     *            the structure materials already taken by earlier groups
     * @return the group, or null if it cannot be used
     */
    private static MaterialGroup readGroup(final Map.Entry<String, Object> entry,
        final Map<Material, MaterialGroup> claimed)
    {
        final String groupName = entry.getKey();
        if (!(entry.getValue() instanceof Map))
        {
            warn(GROUP_PREFIX + groupName + "\" is not a mapping of materials; skipping.");
            return null;
        }
        final Map<String, Object> values = YamlMaps.asMap(entry.getValue());

        final Material structure = parseMaterial(groupName, "structure", values.get("structure"));
        if (structure == null)
        {
            warn(GROUP_PREFIX + groupName + "\" has no readable structure material; skipping.");
            return null;
        }
        if (claimed.containsKey(structure))
        {
            warn(GROUP_PREFIX + groupName + "\" uses structure material " + structure
                + ", already claimed by \"" + claimed.get(structure).getName()
                + "\". A frame material identifies exactly one group, so this group is unavailable.");
            return null;
        }

        final Material portal = defaulted(parseMaterial(groupName, "portal", values.get("portal")), Material.WATER);
        final Material iris = defaulted(parseMaterial(groupName, "iris", values.get("iris")), Material.STONE);
        final Material light = defaulted(parseMaterial(groupName, "light", values.get("light")), Material.GLOWSTONE);
        final Material sign = defaulted(parseMaterial(groupName, "sign", values.get("sign")), Material.OAK_WALL_SIGN);
        // Left null when absent rather than defaulted like the rest. A default here would
        // silently widen what detection accepts as a gate frame for every server that has
        // never asked for distinct chevrons.
        final Material chevron = parseMaterial(groupName, "chevron", values.get("chevron"));

        return new MaterialGroup(groupName, structure, portal, iris, light, sign, chevron);
    }

    /**
     * Loads groups from the {@code gate-material-groups} section of config.yml.
     *
     * <p>Each entry is a group name mapped to its materials, for example:
     *
     * <pre>
     * gate-material-groups:
     *   Standard:
     *     structure: OBSIDIAN
     *     portal: WATER
     *     iris: STONE
     *     light: GLOWSTONE
     * </pre>
     *
     * <p>Groups with an unreadable or duplicate structure material are skipped with a
     * warning: a duplicate would make detection ambiguous, since the frame material is
     * what identifies the palette. Missing portal/iris/light values fall back to the
     * built-in defaults rather than failing the whole group.
     *
     * @param section
     *            the parsed config section, may be null or empty
     */
    public static void load(final Map<String, Object> section)
    {
        final Map<String, MaterialGroup> byName = new LinkedHashMap<>();
        final Map<Material, MaterialGroup> byMaterial = new LinkedHashMap<>();
        MaterialGroup first = null;

        if (section != null)
        {
            for (final Map.Entry<String, Object> entry : section.entrySet())
            {
                final MaterialGroup group = readGroup(entry, byMaterial);
                if (group != null)
                {
                    byName.put(group.getName().toLowerCase(Locale.ROOT), group);
                    byMaterial.put(group.getStructureMaterial(), group);
                    first = (first == null) ? group : first;
                }
            }
        }

        if (first == null)
        {
            // No usable configuration: fall back to the classic obsidian gate so the
            // plugin still works on a server that has never touched this section.
            final MaterialGroup builtin = new MaterialGroup("Standard", Material.OBSIDIAN, Material.WATER,
                Material.STONE, Material.GLOWSTONE, Material.OAK_WALL_SIGN);
            byName.put(builtin.getName().toLowerCase(Locale.ROOT), builtin);
            byMaterial.put(builtin.getStructureMaterial(), builtin);
            first = builtin;
        }

        STATE.set(new Snapshot(Collections.unmodifiableMap(byName),
            Collections.unmodifiableMap(byMaterial), first));

        final List<String> names = new ArrayList<>();
        for (final MaterialGroup g : byName.values())
        {
            names.add(g.getName() + "=" + g.getStructureMaterial());
        }
        log("Loaded " + names.size() + " gate material group(s), default \"" + first.getName() + "\": " + names);
    }

    /**
     * Works out which palettes the loaded shapes imply but config.yml does not yet define.
     *
     * <p>Only unambiguous palettes are returned. A group is identified by its frame
     * material, so a frame material can name exactly one palette — and shapes do not
     * necessarily agree. The shipped set is the illustration: all seven are framed in
     * obsidian but ask for three different irises (glass, stone, bedrock), so there is no
     * single obsidian palette to derive and none is offered. A lone diamond gate with gold
     * chevrons has no such conflict and is offered as "Diamond".
     *
     * <p>Shapes keep working either way. This only surfaces a palette so it can be reused
     * across other geometry.
     *
     * @param shapes
     *            every loaded shape
     * @return palettes worth adding to config.yml, in frame-material order
     */
    public static List<MaterialGroup> discoverUndeclaredGroups(final Collection<StargateShape> shapes)
    {
        // Frame material -> the distinct material sets the shapes using it ask for.
        final Map<Material, List<MaterialGroup>> byFrame = new LinkedHashMap<>();
        for (final StargateShape shape : shapes)
        {
            final Material frame = shape.getShapeStructureMaterial();
            if (frame == null || getGroupByStructureMaterial(frame) != null)
            {
                continue; // already claimed by a configured group
            }
            List<MaterialGroup> seen = byFrame.get(frame);
            if (seen == null)
            {
                seen = new ArrayList<>();
                byFrame.put(frame, seen);
            }
            final MaterialGroup candidate = new MaterialGroup(suggestGroupName(frame), frame,
                shape.getShapePortalMaterial(), shape.getShapeIrisMaterial(), shape.getShapeLightMaterial(),
                shape.getShapeSignMaterial(), shape.getShapeChevronMaterial());
            if (!containsSameMaterials(seen, candidate))
            {
                seen.add(candidate);
            }
        }

        final List<MaterialGroup> discovered = new ArrayList<>();
        for (final Map.Entry<Material, List<MaterialGroup>> entry : byFrame.entrySet())
        {
            if (entry.getValue().size() == 1)
            {
                discovered.add(entry.getValue().get(0));
            }
            else
            {
                warn("Shapes framed in " + entry.getKey() + " disagree on their other materials, so no"
                    + " material group can be derived from them. Those shapes keep their own materials;"
                    + " add a gate-material-groups entry by hand if you want that palette reusable.");
            }
        }
        return discovered;
    }

    private static boolean containsSameMaterials(final List<MaterialGroup> seen, final MaterialGroup candidate)
    {
        for (final MaterialGroup g : seen)
        {
            if (g.getPortalMaterial() == candidate.getPortalMaterial()
                && g.getIrisMaterial() == candidate.getIrisMaterial()
                && g.getLightMaterial() == candidate.getLightMaterial()
                && g.getSignMaterial() == candidate.getSignMaterial()
                && g.getChevronMaterial() == candidate.getChevronMaterial())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Turns a frame material into a readable group name: DIAMOND_BLOCK becomes "Diamond",
     * POLISHED_BLACKSTONE becomes "PolishedBlackstone".
     *
     * <p>Named after the material rather than the shape that revealed it, because the
     * group belongs to the material — several shapes may end up sharing it, and naming it
     * "MinimalSignDial" would be actively misleading.
     *
     * @param frame
     *            the frame material
     * @return a suggested group name
     */
    static String suggestGroupName(final Material frame)
    {
        final StringBuilder out = new StringBuilder();
        for (final String part : frame.name().split("_"))
        {
            if (part.isEmpty() || "BLOCK".equals(part))
            {
                continue; // "DIAMOND_BLOCK" reads better as "Diamond"
            }
            out.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        final String name = out.toString();
        return name.isEmpty() ? frame.name() : name;
    }

    /**
     * Adds a discovered group to the in-memory registry so it takes effect immediately,
     * without waiting for the next restart to re-read config.yml.
     *
     * @param group
     *            the group to register
     */
    public static void registerDiscoveredGroup(final MaterialGroup group)
    {
        if (group == null)
        {
            return;
        }
        // Read, copy, write -- the one shape a plain assignment cannot do safely, because two
        // of these landing together means both copy the same maps and the second write
        // discards the first, losing a palette outright. Only reachable from a single
        // sequential loop today, so nothing has ever lost one; updateAndGet re-runs on the
        // collision rather than leaving that resting on the call site staying sequential.
        //
        // Which is why the body below has to stay a pure function of what it is handed: it
        // may run more than once, so anything with a side effect in it would happen more
        // than once too.
        STATE.updateAndGet(current ->
        {
            if (current.byStructureMaterial().containsKey(group.getStructureMaterial()))
            {
                return current;
            }
            final Map<String, MaterialGroup> byName = new LinkedHashMap<>(current.byName());
            final Map<Material, MaterialGroup> byMaterial =
                new LinkedHashMap<>(current.byStructureMaterial());
            byName.put(group.getName().toLowerCase(Locale.ROOT), group);
            byMaterial.put(group.getStructureMaterial(), group);
            return new Snapshot(Collections.unmodifiableMap(byName),
                Collections.unmodifiableMap(byMaterial),
                current.defaultGroup() == null ? group : current.defaultGroup());
        });
    }

    /**
     * Parses a material name, tolerating a null or unrecognised value.
     *
     * @param groupName
     *            the group being parsed, for the warning message
     * @param key
     *            the config key being parsed, for the warning message
     * @param raw
     *            the raw config value
     * @return the material, or null if absent or unrecognised
     */
    private static Material parseMaterial(final String groupName, final String key, final Object raw)
    {
        if (raw == null)
        {
            return null;
        }
        final Material m = Material.matchMaterial(raw.toString().trim().toUpperCase(Locale.ROOT));
        if (m == null)
        {
            warn(GROUP_PREFIX + groupName + "\" has unrecognised " + key + " material \"" + raw + "\".");
        }
        return m;
    }

    private static Material defaulted(final Material value, final Material fallback)
    {
        return value != null ? value : fallback;
    }

    private static void warn(final String message)
    {
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, message);
        }
    }

    private static void log(final String message)
    {
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, message);
        }
    }
}
