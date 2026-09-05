package com.wormhole_xtreme.wormhole.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Material;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

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
 * <p>The maps are replaced wholesale on load and read through volatile references, so
 * readers never lock. Loading happens once at startup and on an explicit reload.
 */
public final class MaterialGroupRegistry
{
    /** Groups by name, in declaration order. Replaced wholesale on load. */
    private static volatile Map<String, MaterialGroup> groupsByName = Collections.emptyMap();

    /** Groups by frame material, for O(1) detection. Replaced wholesale on load. */
    private static volatile Map<Material, MaterialGroup> groupsByStructureMaterial = Collections.emptyMap();

    /** The first declared group, used when nothing more specific applies. */
    private static volatile MaterialGroup defaultGroup;

    private MaterialGroupRegistry() {}

    /**
     * Gets the default group — the first one declared in config.yml.
     *
     * @return the default group, never null once {@link #load(Map)} has run
     */
    public static MaterialGroup getDefaultGroup()
    {
        return defaultGroup;
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
        return groupsByName.get(name.toLowerCase(Locale.ROOT));
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
        return groupsByStructureMaterial.get(material);
    }

    /**
     * Gets every defined group, in declaration order.
     *
     * @return the groups
     */
    public static Collection<MaterialGroup> getGroups()
    {
        return groupsByName.values();
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
        final Map<String, MaterialGroup> byName = new LinkedHashMap<String, MaterialGroup>();
        final Map<Material, MaterialGroup> byMaterial = new LinkedHashMap<Material, MaterialGroup>();
        MaterialGroup first = null;

        if (section != null)
        {
            for (final Map.Entry<String, Object> entry : section.entrySet())
            {
                final String groupName = entry.getKey();
                if (!(entry.getValue() instanceof Map))
                {
                    warn("Material group \"" + groupName + "\" is not a mapping of materials; skipping.");
                    continue;
                }
                @SuppressWarnings("unchecked")
                final Map<String, Object> values = (Map<String, Object>) entry.getValue();

                final Material structure = parseMaterial(groupName, "structure", values.get("structure"));
                if (structure == null)
                {
                    warn("Material group \"" + groupName + "\" has no readable structure material; skipping.");
                    continue;
                }
                if (byMaterial.containsKey(structure))
                {
                    warn("Material group \"" + groupName + "\" uses structure material " + structure
                        + ", already claimed by \"" + byMaterial.get(structure).getName()
                        + "\". A frame material identifies exactly one group, so this group is unavailable.");
                    continue;
                }

                final Material portal = defaulted(parseMaterial(groupName, "portal", values.get("portal")), Material.WATER);
                final Material iris = defaulted(parseMaterial(groupName, "iris", values.get("iris")), Material.STONE);
                final Material light = defaulted(parseMaterial(groupName, "light", values.get("light")), Material.GLOWSTONE);
                final Material sign = defaulted(parseMaterial(groupName, "sign", values.get("sign")), Material.OAK_WALL_SIGN);

                final MaterialGroup group = new MaterialGroup(groupName, structure, portal, iris, light, sign);
                byName.put(groupName.toLowerCase(Locale.ROOT), group);
                byMaterial.put(structure, group);
                if (first == null)
                {
                    first = group;
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

        groupsByName = Collections.unmodifiableMap(byName);
        groupsByStructureMaterial = Collections.unmodifiableMap(byMaterial);
        defaultGroup = first;

        final List<String> names = new ArrayList<String>();
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
        final Map<Material, List<MaterialGroup>> byFrame = new LinkedHashMap<Material, List<MaterialGroup>>();
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
                seen = new ArrayList<MaterialGroup>();
                byFrame.put(frame, seen);
            }
            final MaterialGroup candidate = new MaterialGroup(suggestGroupName(frame), frame,
                shape.getShapePortalMaterial(), shape.getShapeIrisMaterial(), shape.getShapeLightMaterial(),
                shape.getShapeSignMaterial());
            if (!containsSameMaterials(seen, candidate))
            {
                seen.add(candidate);
            }
        }

        final List<MaterialGroup> discovered = new ArrayList<MaterialGroup>();
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
                && g.getSignMaterial() == candidate.getSignMaterial())
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
        if (group == null || groupsByStructureMaterial.containsKey(group.getStructureMaterial()))
        {
            return;
        }
        final Map<String, MaterialGroup> byName = new LinkedHashMap<String, MaterialGroup>(groupsByName);
        final Map<Material, MaterialGroup> byMaterial = new LinkedHashMap<Material, MaterialGroup>(groupsByStructureMaterial);
        byName.put(group.getName().toLowerCase(Locale.ROOT), group);
        byMaterial.put(group.getStructureMaterial(), group);
        groupsByName = Collections.unmodifiableMap(byName);
        groupsByStructureMaterial = Collections.unmodifiableMap(byMaterial);
        if (defaultGroup == null)
        {
            defaultGroup = group;
        }
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
            warn("Material group \"" + groupName + "\" has unrecognised " + key + " material \"" + raw + "\".");
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
            WormholeXTreme.getThisPlugin().prettyLog(Level.WARNING, false, message);
        }
    }

    private static void log(final String message)
    {
        if (WormholeXTreme.getThisPlugin() != null)
        {
            WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, message);
        }
    }
}
