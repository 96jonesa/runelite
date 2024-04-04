package net.runelite.client.plugins.batiles;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * Used to denote marked tiles and their colors.
 * Note: This is not used for serialization of ground markers; see {@link GroundMarkerPoint}
 */
@Value
class ColorTileMarker
{
    private WorldPoint worldPoint;
    @Nullable
    private Color color;
    @Nullable
    private String label;
}
