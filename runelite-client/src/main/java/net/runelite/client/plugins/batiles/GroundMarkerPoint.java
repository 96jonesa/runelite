package net.runelite.client.plugins.batiles;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

/**
 * Used for serialization of ground marker points.
 */
@Value
@EqualsAndHashCode()
class GroundMarkerPoint
{
    private int regionId;
    private int regionX;
    private int regionY;
    private int z;
    @Nullable
    private Color color;
    @Nullable
    private String label;
    @Nullable
    private List<Integer> waves;
    @Nullable
    private List<String> roles;
}
