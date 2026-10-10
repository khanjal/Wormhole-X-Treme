package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * What a stand-in wears and holds (#296): only what can be seen of its creature's items.
 *
 * <p>An item on an entity is sent whole to whoever sees it. Before this, a far player's shulker box,
 * bundle or written book went to the viewer with its contents, pages, name and lore: a window into
 * someone's inventory, not their room.
 */
class VisibleItemsTest
{
    private Function<VisibleItems.Visible, ItemStack> items;
    private final List<VisibleItems.Visible> made = new ArrayList<>();
    private final List<ItemStack> handedOut = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        items = VisibleItems.items;
        VisibleItems.items = seen ->
        {
            made.add(seen);
            final ItemStack fresh = mock(ItemStack.class);
            handedOut.add(fresh);
            return fresh;
        };
    }

    @AfterEach
    void tearDown() throws Exception
    {
        VisibleItems.items = items;
        PluginTestSupport.remove();
    }

    /**
     * A shulker box full of things, a bundle and a written book, all named and with lore, show as the
     * bare kind: nothing of what is in them is kept to be sent.
     */
    @Test
    void aShulkerBoxBundleAndWrittenBookShowAsTheirKindAlone()
    {
        final ItemStack shulker = stack(Material.SHULKER_BOX, mock(BlockStateMeta.class));
        final ItemStack bundle = stack(Material.BUNDLE, mock(BundleMeta.class));
        final ItemStack book = stack(Material.WRITTEN_BOOK, mock(BookMeta.class));
        for (final ItemStack full : List.of(shulker, bundle, book))
        {
            when(full.getItemMeta().hasDisplayName()).thenReturn(true);
            when(full.getItemMeta().hasLore()).thenReturn(true);

            assertEquals(new VisibleItems.Visible(full.getType(), false, null, null, null), VisibleItems.of(full),
                full.getType() + " keeps nothing but its kind");
        }
    }

    /** What shows is kept: the glint of an enchanted item or book, an armour trim and a leather colour. */
    @Test
    void aGlintATrimAndALeatherColourAreKept()
    {
        final ItemMeta enchanted = mock(ItemMeta.class);
        when(enchanted.hasEnchants()).thenReturn(true);
        assertEquals(new VisibleItems.Visible(Material.DIAMOND_SWORD, true, null, null, null),
            VisibleItems.of(stack(Material.DIAMOND_SWORD, enchanted)));

        final EnchantmentStorageMeta stored = mock(EnchantmentStorageMeta.class);
        when(stored.hasStoredEnchants()).thenReturn(true);
        assertEquals(new VisibleItems.Visible(Material.ENCHANTED_BOOK, true, null, null, null),
            VisibleItems.of(stack(Material.ENCHANTED_BOOK, stored)));

        final ArmorMeta trimmed = mock(ArmorMeta.class);
        final ArmorTrim trim = mock(ArmorTrim.class);
        when(trimmed.hasTrim()).thenReturn(true);
        when(trimmed.getTrim()).thenReturn(trim);
        assertSame(trim, VisibleItems.of(stack(Material.IRON_CHESTPLATE, trimmed)).trim());

        final LeatherArmorMeta dyed = mock(LeatherArmorMeta.class);
        when(dyed.getColor()).thenReturn(Color.RED);
        assertEquals(Color.RED, VisibleItems.of(stack(Material.LEATHER_BOOTS, dyed)).dye());
    }

    /** Nothing in a slot shows as nothing; a stack's count is not part of what shows. */
    @Test
    void anEmptySlotIsNothingAndACountIsNotSeen()
    {
        assertNull(VisibleItems.of(null));
        assertNull(VisibleItems.of(stack(Material.AIR, null)));
        final ItemStack many = stack(Material.ARROW, null);
        when(many.getAmount()).thenReturn(64);
        final ItemStack few = stack(Material.ARROW, null);
        when(few.getAmount()).thenReturn(3);
        assertEquals(VisibleItems.of(many), VisibleItems.of(few));
    }

    /**
     * What a stand-in is given to wear is a fresh item for what shows, never the creature's own: its
     * shulker box goes on as a new, empty one.
     */
    @Test
    void aStandInWearsFreshItemsNeverTheCreaturesOwn()
    {
        final LivingEntity original = mock(Zombie.class);
        final EntityEquipment theirs = mock(EntityEquipment.class);
        when(original.getEquipment()).thenReturn(theirs);
        final ItemStack shulker = stack(Material.SHULKER_BOX, mock(BlockStateMeta.class));
        when(theirs.getItemInMainHand()).thenReturn(shulker);
        final ItemStack boots = stack(Material.IRON_BOOTS, null);
        when(theirs.getBoots()).thenReturn(boots);
        final LivingEntity copy = mock(Zombie.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(copy.getEquipment()).thenReturn(worn);

        VisibleItems.putOn(copy, VisibleItems.wornBy(original), false);

        assertEquals(List.of(Material.IRON_BOOTS, Material.SHULKER_BOX), made.stream().map(VisibleItems.Visible::type).toList());
        verify(worn).setBoots(handedOut.get(0));
        verify(worn).setItemInMainHand(handedOut.get(1));
        verify(worn, never()).setItemInMainHand(shulker);
        assertNotSame(shulker, handedOut.get(1));
        verify(worn).setHelmet(null);
    }

    /** An armour stand keeps the head it wears: its helmet slot is left alone. */
    @Test
    void anArmourStandsHelmetIsLeftAlone()
    {
        final LivingEntity copy = mock(Zombie.class);
        final EntityEquipment worn = mock(EntityEquipment.class);
        when(copy.getEquipment()).thenReturn(worn);

        VisibleItems.putOn(copy, new VisibleItems.Visible[VisibleItems.SLOTS], true);

        verify(worn, never()).setHelmet(any());
        verify(worn).setChestplate(null);
    }

    private static ItemStack stack(final Material type, final ItemMeta meta)
    {
        final ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.hasItemMeta()).thenReturn(meta != null);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }
}
