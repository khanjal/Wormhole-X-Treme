package com.wormhole_xtreme.wormhole.logic;

import com.wormhole_xtreme.wormhole.model.Stargate3DShape;
import com.wormhole_xtreme.wormhole.model.StargateShape;

/**
 * A factory for creating StargateShape objects.
 */
public class StargateShapeFactory
{
    /** Static helpers only; never instantiated. */
    private StargateShapeFactory()
    {
    }


    /**
     * Creates a new StargateShape object.
     */
    private static StargateShape create2DShape(final String[] fileLines)
    {
        return new StargateShape(fileLines);
    }

    /**
     * Creates a new StargateShape object.
     */
    private static Stargate3DShape create3DShape(final String[] fileLines)
    {
        return new Stargate3DShape(fileLines);
    }

    /**
     * Creates a new StargateShape object.
     * 
     * @param fileLines
     *            a shape file's lines; any line starting {@code Version=2} makes it a 3-D shape
     */
    public static StargateShape createShapeFromFile(final String[] fileLines)
    {
        for (final String line : fileLines)
        {
            if (line.startsWith("Version=2"))
            {
                return create3DShape(fileLines);
            }
        }

        return create2DShape(fileLines);
    }
}
