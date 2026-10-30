package com.wormhole_xtreme.wormhole.model.preview;

import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.logic.DialSpinPattern;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Cell;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Palette;
import com.wormhole_xtreme.wormhole.logic.GateBlueprint.Part;
import com.wormhole_xtreme.wormhole.utils.HiddenEntities;
import com.wormhole_xtreme.wormhole.utils.MaterialUtils;

/**
 * How a build preview's frame is drawn and its guide styled (#430, #734).
 */
final class PreviewFrame
{
    /** Static helpers only. */
    private PreviewFrame()
    {
    }

    /** Lit as if in daylight, so a preview reads the same underground as in the open. */
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    /** The size of what the preview's button answers clicks on. */
    private static final float BUTTON_SIZE = 0.6f;

    /** How big the guide draws a block still to place. */
    private static final float MISSING_SCALE = 0.5f;

    /** How big the guide draws its outline over a wrong block, just clear of the block's faces. */
    private static final float WRONG_SCALE = 1.02f;

    /** What the guide marks a block in the opening with. */
    private static final Material IN_THE_WAY = Material.RED_STAINED_GLASS;

    /**
     * Draws the frame, DHD and sign as the guide says, or whole without it.
     *
     * @return whether every block the gate is found by is in place
     */
    static boolean drawFrame(final Player owner, final GatePreview preview)
    {
        boolean built = true;
        for (int i = 0; i < preview.cells().size(); i++)
        {
            final Cell cell = preview.cells().get(i);
            final BuildGuide.State state = preview.guide() ? guideState(preview, cell) : null;
            // The dial sign is optional: a gate is found without one.
            built &= (state == BuildGuide.State.PLACED) || (cell.part() == Part.DIAL_SIGN);
            if (!preview.showing(cell) || (state == BuildGuide.State.PLACED))
            {
                GatePreview.removeAt(preview.displays(), i);
                continue;
            }
            spawnMissing(owner, preview, preview.displays(), i, preview.world(), cell, dataFor(preview, cell));
            styleForGuide(preview.displays().get(i), state);
        }
        return built;
    }

    /**
     * Marks, with the guide on, whatever stands in the opening.
     *
     * @return whether the opening is clear
     */
    static boolean drawInTheWay(final Player owner, final GatePreview preview)
    {
        boolean clear = true;
        for (int i = 0; i < preview.opening().size(); i++)
        {
            final Cell cell = preview.opening().get(i);
            if (!preview.guide() || !BuildGuide.blocksOpening(GatePreviews.typeAt(preview.world(), cell)))
            {
                GatePreview.removeAt(preview.blockedDisplays(), i);
                continue;
            }
            clear = false;
            spawnMissing(owner, preview, preview.blockedDisplays(), i, preview.world(), cell, GatePreviews.blockData.apply(IN_THE_WAY));
            styleForGuide(preview.blockedDisplays().get(i), BuildGuide.State.WRONG);
        }
        return clear;
    }

    static void spawnMissing(final Player owner, final GatePreview preview, final List<BlockDisplay> shown,
        final int i, final World world, final Cell cell, final BlockData data)
    {
        final BlockDisplay existing = shown.get(i);
        if (((existing != null) && existing.isValid()) || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
        {
            return;
        }
        final BlockDisplay spawned = HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner,
            new Location(world, cell.x(), cell.y(), cell.z()), BlockDisplay.class, display ->
            {
                display.setBlock(data);
                display.setBrightness(FULL_BRIGHT);
            });
        shown.set(i, spawned);
        if (spawned != null)
        {
            GatePreviews.watching(null, preview).forEach(viewer -> viewer.showEntity(WormholeXTreme.getThisPlugin(), spawned));
        }
    }

    /** The invisible box over the preview's button that a click lands on. */
    static void drawButton(final Player owner, final GatePreview preview)
    {
        final Cell cell = preview.buttonCell();
        // A real button there is pressed to find the gate, so the box must not take its clicks.
        if ((cell == null) || !preview.showing(cell)
            || (BuildGuide.of(cell, preview.palette(), GatePreviews.typeAt(preview.world(), cell)) == BuildGuide.State.PLACED))
        {
            preview.removeButton();
            return;
        }
        final Interaction existing = preview.button();
        if (((existing != null) && existing.isValid()) || !preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4))
        {
            return;
        }
        final Location centre = new Location(preview.world(), cell.x() + 0.5, cell.y() + ((1.0 - BUTTON_SIZE) / 2.0),
            cell.z() + 0.5);
        preview.button(HiddenEntities.spawnFor(WormholeXTreme.getThisPlugin(), owner, centre, Interaction.class,
            box ->
            {
                box.setInteractionWidth(BUTTON_SIZE);
                box.setInteractionHeight(BUTTON_SIZE);
                box.setResponsive(true);
            }));
    }

    /** How a cell compares with the world, or null where its chunk is not loaded to read. */
    private static BuildGuide.State guideState(final GatePreview preview, final Cell cell)
    {
        return preview.world().isChunkLoaded(cell.x() >> 4, cell.z() >> 4)
            ? BuildGuide.of(cell, preview.palette(), GatePreviews.typeAt(preview.world(), cell))
            : null;
    }

    /** Draws a display small for a block still to place, outlined in red over a wrong one, and whole otherwise. */
    static void styleForGuide(final BlockDisplay display, final BuildGuide.State state)
    {
        if (display == null)
        {
            return;
        }
        final float scale;
        if (state == BuildGuide.State.MISSING)
        {
            scale = MISSING_SCALE;
        }
        else
        {
            scale = (state == BuildGuide.State.WRONG) ? WRONG_SCALE : 1.0f;
        }
        final float offset = (1.0f - scale) / 2.0f;
        display.setTransformation(new Transformation(new Vector3f(offset, offset, offset), new AxisAngle4f(),
            new Vector3f(scale, scale, scale), new AxisAngle4f()));
        final boolean wrong = state == BuildGuide.State.WRONG;
        display.setGlowing(wrong);
        display.setGlowColorOverride(wrong ? Color.RED : null);
    }

    /**
     * What a frame cell shows now: lit while its wave is or the ring's light is on it, and as built
     * otherwise. Under UNIVERSE the locked chevrons ride round with the ring until the last locks,
     * so only its light counts till then.
     */
    static BlockData dataFor(final GatePreview preview, final Cell cell)
    {
        final boolean riding = GatePreviews.spins(preview) && (GatePreviews.pattern(preview) == DialSpinPattern.UNIVERSE)
            && (preview.litWaves() < preview.lastWave());
        final boolean lit = (!riding && (cell.wave() > 0) && (cell.wave() <= preview.litWaves()))
            || preview.spinCells().contains(cell);
        return lit ? litData(preview.drawnPalette(), cell) : blockDataFor(preview, cell);
    }

    /**
     * A lit chevron: a chevron block with an on state switched on, and the light material for
     * everything else, as a real gate draws it.
     */
    static BlockData litData(final Palette palette, final Cell cell)
    {
        final BlockData fixtureOn = (palette.chevron() == null) ? null
            : MaterialUtils.litFormOf(GatePreviews.blockData.apply(palette.chevron()));
        return MaterialUtils.litChevron(palette.materialOf(cell), palette.chevron(), fixtureOn,
            MaterialUtils.drawnAs(GatePreviews.blockData.apply(palette.light())));
    }

    /** What the opening's displays show: they stand only while the iris is closed. */
    static BlockData openingData(final GatePreview preview)
    {
        return MaterialUtils.laidAcross(GatePreviews.blockData.apply(preview.palette().iris()), preview.grid().facing());
    }

    /** What a cell is drawn as, with the button and sign turned to face the builder. */
    static BlockData blockDataFor(final GatePreview preview, final Cell cell)
    {
        final BlockData data = GatePreviews.blockData.apply(preview.drawnPalette().materialOf(cell));
        final boolean faces = (cell.part() == Part.BUTTON) || (cell.part() == Part.DIAL_SIGN);
        if (faces && (data instanceof Directional directional)
            && directional.getFaces().contains(preview.grid().facing()))
        {
            directional.setFacing(preview.grid().facing());
        }
        // No Orientable branch here on purpose: a blueprint's cells are frame, chevron, button
        // and dial sign only -- the opening is a list of its own -- so there is nothing in this
        // one that wants laying in the gate's plane. openingData is where that happens.
        return data;
    }
}
