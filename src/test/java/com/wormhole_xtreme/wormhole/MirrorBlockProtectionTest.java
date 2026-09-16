package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.config.ConfigTestSupport;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * A mirror cannot be broken out from under the people using it.
 *
 * <p>Punching a mirror is how you go through it, so a punch that could also break the banner
 * would lose the mirror to the first player who tried it -- and in creative, a punch breaks at
 * once. The wall round the opening is what hides the mirror's world from anywhere but the
 * opening, so it stays too. {@code mirror remove} is the way to take one down.
 *
 * <p>Through the real listeners, since that is where a missed check would let the block go.
 */
class MirrorBlockProtectionTest
{
    private World world;
    private Player player;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        ConfigTestSupport.clear();
        MirrorManager.clear();
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
            blockAt(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        player = mock(Player.class);
        when(player.getName()).thenReturn("builder");
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        // An operator: nobody may break a mirror by hand, not even one who may remove it.
        when(player.isOp()).thenReturn(true);
        MirrorManager.add(new QuantumMirror("library", new MirrorBlock("world", 10, 64, 10), null));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        MirrorManager.clear();
        ConfigTestSupport.clear();
        PluginTestSupport.remove();
    }

    /** The banner at 10 64 10, hung facing north on stone; everything else stone. */
    private Block blockAt(final int x, final int y, final int z)
    {
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        if ((x == 10) && (y == 64) && (z == 10))
        {
            final Directional facing = mock(Directional.class);
            when(facing.getFacing()).thenReturn(BlockFace.NORTH);
            when(block.getType()).thenReturn(Material.WHITE_WALL_BANNER);
            when(block.getBlockData()).thenReturn(facing);
            return block;
        }
        final BlockData stone = mock(BlockData.class);
        when(stone.isOccluding()).thenReturn(true);
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getBlockData()).thenReturn(stone);
        return block;
    }

    /** Said above the hotbar: a player holding the button down would otherwise fill their chat. */
    @Test
    void breakingAMirrorsBannerIsRefusedAndSaysHowToTakeItDown()
    {
        final Player.Spigot hotbar = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(hotbar);
        final BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(10, 64, 10), player);

        new WormholeXTremeBlockListener().onBlockBreak(event);

        assertTrue(event.isCancelled(), "a mirror's banner does not break");
        final org.mockito.ArgumentCaptor<net.md_5.bungee.api.chat.BaseComponent> said =
            org.mockito.ArgumentCaptor.forClass(net.md_5.bungee.api.chat.BaseComponent.class);
        verify(hotbar).sendMessage(org.mockito.ArgumentMatchers.eq(net.md_5.bungee.api.ChatMessageType.ACTION_BAR),
            said.capture());
        assertTrue(said.getValue().toPlainText().contains("mirror remove"), "and says how: " + said.getValue().toPlainText());
        verify(player, never()).sendMessage(anyString());
    }

    @Test
    void punchingTheWallAMirrorHangsOnDoesNotStartBreakingIt()
    {
        final BlockDamageEvent event = new BlockDamageEvent(player, world.getBlockAt(10, 64, 11),
            new ItemStack(Material.DIAMOND_PICKAXE), false);

        new WormholeXTremeBlockListener().onBlockDamage(event);

        assertTrue(event.isCancelled(), "the wall behind the banner is the mirror's opening");
    }

    @Test
    void wallPastTheFaceBreaksAsUsual()
    {
        final BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(13, 64, 11), player);

        new WormholeXTremeBlockListener().onBlockBreak(event);

        assertFalse(event.isCancelled(), "three blocks along the wall is ordinary wall");
    }

    /** A blast next to a mirror takes the rest of the wall, and leaves the mirror standing. */
    @Test
    void anExplosionLeavesTheMirrorAndItsFaceStanding()
    {
        final Block banner = world.getBlockAt(10, 64, 10);
        final Block behind = world.getBlockAt(10, 63, 11);
        final Block beside = world.getBlockAt(14, 64, 11);
        final List<Block> blown = new ArrayList<>(List.of(banner, behind, beside));
        // Mocked rather than built: from 1.21 the constructor also takes an ExplosionResult, which
        // 1.20 does not have, so no one constructor call compiles across the supported range.
        final EntityExplodeEvent event = mock(EntityExplodeEvent.class);
        when(event.blockList()).thenReturn(blown);

        new WormholeXTremeEntityListener().onEntityExplode(event);

        verify(event, never()).setCancelled(true);
        assertFalse(blown.contains(banner), "the banner survives it");
        assertFalse(blown.contains(behind), "and so does the opening behind it");
        assertTrue(blown.contains(beside), "while wall past the face goes as usual");
    }
}
