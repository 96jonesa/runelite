package net.runelite.client.plugins.batiles;

import com.google.common.base.Strings;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;
import java.util.Collection;

public class BATilesOverlay extends Overlay
{
    private static final int MAX_DRAW_DISTANCE = 32;

    private final Client client;
    private final BATilesConfig config;
    private final BATilesPlugin plugin;

    @Inject
    private BATilesOverlay(Client client, BATilesConfig config, BATilesPlugin plugin)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        final Collection<ColorTileMarker> points = plugin.getPoints();
        if (points.isEmpty())
        {
            return null;
        }

        Stroke stroke = new BasicStroke((float) config.borderWidth());
        for (final ColorTileMarker point : points)
        {
            WorldPoint worldPoint = point.getWorldPoint();
            if (worldPoint.getPlane() != client.getPlane())
            {
                continue;
            }

            Color tileColor = point.getColor();
            if (tileColor == null)
            {
                // If this is an old tile which has no color, use marker color
                tileColor = config.markerColor();
            }

            drawTile(graphics, worldPoint, tileColor, point.getLabel(), stroke);
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color, @Nullable String label, Stroke borderStroke)
    {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= MAX_DRAW_DISTANCE)
        {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, config.fillOpacity()), borderStroke);
        }

        if (!Strings.isNullOrEmpty(label))
        {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
            if (canvasTextLocation != null)
            {
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, color);
            }
        }
    }
}
