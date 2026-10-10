package com.wormhole_xtreme.wormhole.model.window;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/**
 * What a stand-in wears and holds (#296): only what can be seen of its creature's items, never the
 * items themselves.
 *
 * <p>An item put on an entity is sent whole to whoever sees it, so a copied shulker box, bundle,
 * written book or map would hand the viewer its contents, pages, name and lore. A stand-in gets a
 * fresh item of the same kind instead, one of it, with only its glint, armour trim, leather dye and a
 * head's skin carried over, the skin on a profile of its own ({@link #skinOnly}).
 */
final class VisibleItems
{
    /** The slots copied, in this order: helmet, chestplate, leggings, boots, main hand, off hand. */
    static final int SLOTS = 6;

    /** {@code ItemMeta.setEnchantmentGlintOverride}, 1.20.5 on; before it a glint is shown by one enchantment. */
    private static final Method GLINT = glintMethod();

    /** Makes the item for what is seen; replaceable for a test, which has no item factory. */
    static Function<Visible, ItemStack> items = VisibleItems::itemOf;

    /** Makes an empty profile with this id and no name; replaceable for a test, which has no server. */
    static Function<UUID, PlayerProfile> profiles = Bukkit::createPlayerProfile;

    /**
     * What can be seen of an item.
     *
     * @param type
     *            its kind
     * @param glint
     *            whether it glints, as an enchanted item does
     * @param trim
     *            its armour trim, or null
     * @param dye
     *            its leather colour, or null
     * @param skull
     *            a head's skin, or null
     */
    record Visible(Material type, boolean glint, ArmorTrim trim, Color dye, PlayerProfile skull)
    {
    }

    /** Static methods only. */
    private VisibleItems()
    {
    }

    /**
     * What can be seen of an item.
     *
     * @param item
     *            an item worn or held
     * @return what shows of it, or null for nothing there
     */
    static Visible of(final ItemStack item)
    {
        final Material type = (item == null) ? null : item.getType();
        if ((type == null) || (type == Material.AIR))
        {
            return null;
        }
        final ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta == null)
        {
            return new Visible(type, false, null, null, null);
        }
        final boolean glint = meta.hasEnchants()
            || ((meta instanceof EnchantmentStorageMeta stored) && stored.hasStoredEnchants());
        final ArmorTrim trim = ((meta instanceof ArmorMeta armour) && armour.hasTrim()) ? armour.getTrim() : null;
        final Color dye = (meta instanceof LeatherArmorMeta leather) ? leather.getColor() : null;
        final PlayerProfile skull = (meta instanceof SkullMeta head) ? head.getOwnerProfile() : null;
        return new Visible(type, glint, trim, dye, skull);
    }

    /**
     * What can be seen of everything a creature wears and holds.
     *
     * @param original
     *            the creature
     * @return one per slot, as {@link #SLOTS} orders them; empty where it has no equipment
     */
    static Visible[] wornBy(final LivingEntity original)
    {
        final EntityEquipment from = original.getEquipment();
        if (from == null)
        {
            return new Visible[0];
        }
        return new Visible[] { of(from.getHelmet()), of(from.getChestplate()), of(from.getLeggings()), of(from.getBoots()),
            of(from.getItemInMainHand()), of(from.getItemInOffHand()) };
    }

    /**
     * Puts on a stand-in items showing what its creature wears and holds.
     *
     * @param copy
     *            the stand-in
     * @param worn
     *            from {@link #wornBy}
     * @param keepHelmet
     *            true to leave the helmet as it is, as an armour stand's head
     */
    static void putOn(final LivingEntity copy, final Visible[] worn, final boolean keepHelmet)
    {
        final EntityEquipment to = copy.getEquipment();
        if ((to == null) || (worn.length < SLOTS))
        {
            return;
        }
        if (!keepHelmet)
        {
            to.setHelmet(itemFor(worn[0]));
        }
        to.setChestplate(itemFor(worn[1]));
        to.setLeggings(itemFor(worn[2]));
        to.setBoots(itemFor(worn[3]));
        to.setItemInMainHand(itemFor(worn[4]));
        to.setItemInOffHand(itemFor(worn[5]));
    }

    /**
     * The item showing what is seen.
     *
     * @param seen
     *            what shows, or null for nothing
     * @return a fresh item, or null for nothing or one that could not be made
     */
    static ItemStack itemFor(final Visible seen)
    {
        if (seen == null)
        {
            return null;
        }
        try
        {
            return items.apply((seen.skull() == null) ? seen
                : new Visible(seen.type(), seen.glint(), seen.trim(), seen.dye(), skinOnly(seen.skull())));
        }
        catch (final RuntimeException | LinkageError notMade)
        {
            StandIns.failedOnce("Could not make an item a stand-in wears", notMade);
            return null;
        }
    }

    /**
     * A profile carrying only a skin: a fresh id and no name, so the account behind the skin does not
     * travel to the viewer with it, as the original profile's name, id and signed textures would.
     *
     * @param from
     *            a profile with a skin
     * @return a new profile with that skin and cape and nothing else, or null for no skin or one that
     *         could not be made, which shows the default skin
     */
    static PlayerProfile skinOnly(final PlayerProfile from)
    {
        try
        {
            final PlayerTextures theirs = from.getTextures();
            final URL skin = theirs.getSkin();
            if (skin == null)
            {
                return null;
            }
            final PlayerProfile fresh = profiles.apply(UUID.randomUUID());
            final PlayerTextures textures = fresh.getTextures();
            textures.setSkin(skin, theirs.getSkinModel());
            textures.setCape(theirs.getCape());
            fresh.setTextures(textures);
            return fresh;
        }
        catch (final RuntimeException | LinkageError notMade)
        {
            StandIns.failedOnce("Could not copy a skin for a stand-in", notMade);
            return null;
        }
    }

    /** One of the kind, with only what is seen set on it. */
    private static ItemStack itemOf(final Visible seen)
    {
        final ItemStack item = new ItemStack(seen.type());
        final ItemMeta meta = item.getItemMeta();
        if (meta == null)
        {
            return item;
        }
        if (seen.glint())
        {
            glint(meta);
        }
        if ((seen.trim() != null) && (meta instanceof ArmorMeta armour))
        {
            armour.setTrim(seen.trim());
        }
        if ((seen.dye() != null) && (meta instanceof LeatherArmorMeta leather))
        {
            leather.setColor(seen.dye());
        }
        if ((seen.skull() != null) && (meta instanceof SkullMeta head))
        {
            head.setOwnerProfile(seen.skull());
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Makes an item glint: by the override where the server has it, else by an enchantment nobody sees. */
    private static void glint(final ItemMeta meta)
    {
        if (GLINT != null)
        {
            try
            {
                GLINT.invoke(meta, Boolean.TRUE);
                return;
            }
            catch (final ReflectiveOperationException | RuntimeException | LinkageError notThere)
            {
                // Glints by an enchantment instead.
            }
        }
        meta.addEnchant(Enchantment.LURE, 1, true);
    }

    /** The glint override, or null on a server before 1.20.5. */
    private static Method glintMethod()
    {
        try
        {
            return ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
        }
        catch (final NoSuchMethodException | RuntimeException | LinkageError absent)
        {
            return null;
        }
    }
}
