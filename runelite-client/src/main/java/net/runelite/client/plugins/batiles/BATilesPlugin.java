package net.runelite.client.plugins.batiles;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
		name = "BA Tiles",
		description = "Ground Markers for specific Barbarian Assault waves and roles",
		tags = {"minigame", "overlay", "tiles"}
)
public class BATilesPlugin extends Plugin {
	private static final String CONFIG_GROUP = "baTiles";
	private static final String WALK_HERE = "Walk here";
	private static final String REGION_PREFIX = "region_";
	private static final int BA_WAVE_NUM_INDEX = 2;
	private static final int START_WAVE = 1;

	@Getter(AccessLevel.PACKAGE)
	private final List<ColorTileMarker> points = new ArrayList<>();

	@Inject
	private Client client;

	@Inject
	private BATilesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BATilesOverlay overlay;

	@Inject
	private ChatboxPanelManager chatboxPanelManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private BATilesSharingManager sharingManager;

	@Inject
	private Gson gson;

	@Inject
	private ColorPickerManager colorPickerManager;

	private int currentWave = START_WAVE;
	private String currentRole = "a";
	private GroundMarkerPoint copiedPoint = null;

	void savePoints(int regionId, Collection<GroundMarkerPoint> points)
	{
		if (points == null || points.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
			return;
		}

		String json = gson.toJson(points);
		configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
	}

	Collection<GroundMarkerPoint> getPoints(int regionId)
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
		if (Strings.isNullOrEmpty(json))
		{
			return Collections.emptyList();
		}

		// CHECKSTYLE:OFF
		return gson.fromJson(json, new TypeToken<List<GroundMarkerPoint>>(){}.getType());
		// CHECKSTYLE:ON
	}

	@Provides
	BATilesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BATilesConfig.class);
	}

	void loadPoints()
	{
		points.clear();

		int[] regions = client.getMapRegions();

		if (regions == null)
		{
			return;
		}

		for (int regionId : regions)
		{
			// load points for region
			log.debug("Loading points for region {}", regionId);
			Collection<GroundMarkerPoint> regionPoints = getPoints(regionId);

			Collection<GroundMarkerPoint> pointsToLoad = regionPoints.stream()
					.filter(point -> point.getWaves() == null || containsAtLeastOneWave(point.getWaves(), wavesToDisplay()))
					.filter(point -> point.getRoles() == null || containsAtLeastOneRole(point.getRoles(), rolesToDisplay()))
					.collect(Collectors.toList());

			Collection<ColorTileMarker> colorTileMarkers = translateToColorTileMarker(pointsToLoad);
			points.addAll(colorTileMarkers);
		}
	}

	List<Integer> wavesToDisplay() {
		List<Integer> waves = new ArrayList<Integer>();

		if (config.showTilesForCurrentWave()) {
			waves.add(currentWave);
		}

		if (config.showTilesForWave1()) {
			waves.add(1);
		}

		if (config.showTilesForWave2()) {
			waves.add(2);
		}

		if (config.showTilesForWave3()) {
			waves.add(3);
		}

		if (config.showTilesForWave4()) {
			waves.add(4);
		}

		if (config.showTilesForWave5()) {
			waves.add(5);
		}

		if (config.showTilesForWave6()) {
			waves.add(6);
		}

		if (config.showTilesForWave7()) {
			waves.add(7);
		}

		if (config.showTilesForWave8()) {
			waves.add(8);
		}

		if (config.showTilesForWave9()) {
			waves.add(9);
		}

		if (config.showTilesForWave10()) {
			waves.add(10);
		}

		return waves;
	}

	List<String> rolesToDisplay() {
		List<String> roles = new ArrayList<String>();

		if (config.showTilesForCurrentRole()) {
			roles.add(currentRole);
		}

		if (config.showTilesForAttacker()) {
			roles.add("a");
		}

		if (config.showTilesForCollector()) {
			roles.add("c");
		}

		if (config.showTilesForDefender()) {
			roles.add("d");
		}

		if (config.showTilesForHealer()) {
			roles.add("h");
		}

		return roles;
	}

	boolean containsAtLeastOneWave(List<Integer> checkIfContains, List<Integer> waves) {
		for (int wave : waves) {
			if (checkIfContains.contains(wave)) {
				return true;
			}
		}

		return false;
	}

	boolean containsAtLeastOneRole(List<String> checkIfContains, List<String> roles) {
		for (String role : roles) {
			if (checkIfContains.contains(role)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Translate a collection of ground marker points to color tile markers, accounting for instances
	 *
	 * @param points {@link GroundMarkerPoint}s to be converted to {@link ColorTileMarker}s
	 * @return A collection of color tile markers, converted from the passed ground marker points, accounting for local
	 *         instance points. See {@link WorldPoint#toLocalInstance(Client, WorldPoint)}
	 */
	private Collection<ColorTileMarker> translateToColorTileMarker(Collection<GroundMarkerPoint> points)
	{
		if (points.isEmpty())
		{
			return Collections.emptyList();
		}

		return points.stream()
				.map(point -> new ColorTileMarker(
						WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()),
						point.getColor(), point.getLabel()))
				.flatMap(colorTile ->
				{
					final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, colorTile.getWorldPoint());
					return localWorldPoints.stream().map(wp -> new ColorTileMarker(wp, colorTile.getColor(), colorTile.getLabel()));
				})
				.collect(Collectors.toList());
	}

	@Override
	public void startUp()
	{
		overlayManager.add(overlay);
		if (config.showImportExport())
		{
			sharingManager.addImportExportMenuOptions();
			sharingManager.addClearMenuOption();
		}
		loadPoints();
		eventBus.register(sharingManager);
	}

	@Override
	public void shutDown()
	{
		eventBus.unregister(sharingManager);
		overlayManager.remove(overlay);
		sharingManager.removeMenuOptions();
		points.clear();
		currentWave = START_WAVE;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() == ChatMessageType.GAMEMESSAGE
				&& event.getMessage().startsWith("---- Wave:"))
		{
			String[] message = event.getMessage().split(" ");

			try {
				currentWave = Integer.parseInt(message[BA_WAVE_NUM_INDEX]);
			} catch (NumberFormatException e) {}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.BA_ATTACKER:
			{
				currentRole = "a";
				break;
			}
			case InterfaceID.BA_DEFENDER:
			{
				currentRole = "d";
				break;
			}
			case InterfaceID.BA_HEALER:
			{
				currentRole = "h";
				break;
			}
			case InterfaceID.BA_COLLECTOR:
			{
				currentRole = "c";
				break;
			}
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged profileChanged)
	{
		loadPoints();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// map region has just been updated
		loadPoints();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final boolean hotKeyPressed = client.isKeyPressed(KeyCode.KC_CONTROL);
		if (hotKeyPressed && event.getOption().equals(WALK_HERE))
		{
			final Tile selectedSceneTile = client.getSelectedSceneTile();

			if (selectedSceneTile == null)
			{
				return;
			}

			final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
			final int regionId = worldPoint.getRegionID();
			var regionPoints = getPoints(regionId);
			var existingPoints = regionPoints.stream()
					.filter(p -> p.getRegionX() == worldPoint.getRegionX() && p.getRegionY() == worldPoint.getRegionY() && p.getZ() == worldPoint.getPlane())
					.collect(Collectors.toList());

			client.createMenuEntry(-1)
					.setOption("Mark")
					.setTarget("BA Tile")
					.setType(MenuAction.RUNELITE)
					.onClick(e ->
					{
						Tile target = client.getSelectedSceneTile();
						if (target != null)
						{
							markTile(target.getLocalLocation());
						}
					});

			int j = 0;

			if (copiedPoint != null) {
				client.createMenuEntry(-2)
						.setOption("Paste")
						.setTarget("BA Tile")
						.setType(MenuAction.RUNELITE)
						.onClick(e ->
						{
							Tile target = client.getSelectedSceneTile();
							if (target != null) {
								pasteTile(target.getLocalLocation());
							}
						});

				j = 1;
			}

			if (!existingPoints.isEmpty()) {
				for (int i = 0; i < existingPoints.size(); i++) {
					GroundMarkerPoint point = existingPoints.get(i);

					MenuEntry pointConfigMenu = client.createMenuEntry(-2 - i)
							.setOption(ColorUtil.prependColorTag("Configure", point.getColor()))
							.setTarget("BA Tile " + (point.getLabel() == null ? "" : point.getLabel() + " ") + point.getWaves() + " " + point.getRoles())
							.setType(MenuAction.RUNELITE_SUBMENU);

					client.createMenuEntry(-2 - i - j)
							.setOption("Set waves")
							.setType(MenuAction.RUNELITE)
							.setParent(pointConfigMenu)
							.onClick(e -> setTileWaves(point));

					client.createMenuEntry(-2 - i - 1 - j)
							.setOption("Set roles")
							.setType(MenuAction.RUNELITE)
							.setParent(pointConfigMenu)
							.onClick(e -> setTileRoles(point));

					client.createMenuEntry(-2 - i - 2 - j)
							.setOption("Set label")
							.setType(MenuAction.RUNELITE)
							.setParent(pointConfigMenu)
							.onClick(e -> labelTile(point));

					client.createMenuEntry(-2 - i - 3 - j)
							.setOption("Unmark")
							.setType(MenuAction.RUNELITE)
							.setParent(pointConfigMenu)
							.onClick(e -> unmarkTile(point));

					client.createMenuEntry(-2 - i - 4 - j)
							.setOption("Copy")
							.setType(MenuAction.RUNELITE)
							.setParent(pointConfigMenu)
							.onClick(e -> copyTile(point));

					client.createMenuEntry(-2 - i - 3 - j)
							.setOption("Pick color")
							.setParent(pointConfigMenu)
							.setType(MenuAction.RUNELITE)
							.onClick(e ->
							{
								Color color = point.getColor();
								SwingUtilities.invokeLater(() ->
								{
									RuneliteColorPicker colorPicker = colorPickerManager.create(SwingUtilities.windowForComponent((Applet) client),
											color, "Tile marker color", false);
									colorPicker.setOnClose(c -> colorTile(point, c));
									colorPicker.setVisible(true);
								});
							});

					var existingColors = points.stream()
							.map(ColorTileMarker::getColor)
							.distinct()
							.collect(Collectors.toList());
					for (Color color : existingColors)
					{
						if (!color.equals(point.getColor()))
						{
							client.createMenuEntry(-2 - i - 4 - j)
									.setOption(ColorUtil.prependColorTag("Color", color))
									.setType(MenuAction.RUNELITE)
									.setParent(pointConfigMenu)
									.onClick(e -> colorTile(point, color));
						}
					}
				}
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals(BATilesConfig.BA_TILES_CONFIG_GROUP)
				&& event.getKey().equals(BATilesConfig.SHOW_IMPORT_EXPORT_KEY_NAME))
		{
			sharingManager.removeMenuOptions();

			if (config.showImportExport())
			{
				sharingManager.addImportExportMenuOptions();
				sharingManager.addClearMenuOption();
			}
		}

		loadPoints();
	}

	private void markTile(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return;
		}

		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

		int regionId = worldPoint.getRegionID();
		List<Integer> waves = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		List<String> roles = List.of("a", "c", "d", "h");
		GroundMarkerPoint point = new GroundMarkerPoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), config.markerColor(), null, waves, roles);
		log.debug("Updating point: {} - {}", point, worldPoint);

		List<GroundMarkerPoint> groundMarkerPoints = new ArrayList<>(getPoints(regionId));
		groundMarkerPoints.add(point);

		savePoints(regionId, groundMarkerPoints);

		loadPoints();
	}

	private void unmarkTile(GroundMarkerPoint existing) {
		log.debug("Updating point: {}", existing);

		List<GroundMarkerPoint> groundMarkerPoints = new ArrayList<>(getPoints(existing.getRegionId()));
		groundMarkerPoints.remove(existing);

		savePoints(existing.getRegionId(), groundMarkerPoints);

		loadPoints();
	}

	private void labelTile(GroundMarkerPoint existing)
	{
		chatboxPanelManager.openTextInput("Tile label")
				.value(Optional.ofNullable(existing.getLabel()).orElse(""))
				.onDone((input) ->
				{
					input = Strings.emptyToNull(input);

					if (input != null && input.length() > 10) {
						input = input.substring(0, 10);
					}

					var newPoint = new GroundMarkerPoint(existing.getRegionId(), existing.getRegionX(), existing.getRegionY(), existing.getZ(), existing.getColor(), input, existing.getWaves(), existing.getRoles());
					Collection<GroundMarkerPoint> points = new ArrayList<>(getPoints(existing.getRegionId()));
					points.remove(existing);
					points.add(newPoint);
					savePoints(existing.getRegionId(), points);

					loadPoints();
				})
				.build();
	}

	private void colorTile(GroundMarkerPoint existing, Color newColor)
	{
		var newPoint = new GroundMarkerPoint(existing.getRegionId(), existing.getRegionX(), existing.getRegionY(), existing.getZ(), newColor, existing.getLabel(), existing.getWaves(), existing.getRoles());
		Collection<GroundMarkerPoint> points = new ArrayList<>(getPoints(existing.getRegionId()));
		points.remove(existing);
		points.add(newPoint);
		savePoints(existing.getRegionId(), points);

		loadPoints();
	}

	private void setTileWaves(GroundMarkerPoint existing)
	{
		chatboxPanelManager.openTextInput("Tile waves")
				.value("")
				.onDone((input) ->
				{
					input = Strings.emptyToNull(input);

					String[] tokens;

					if (input == null) {
						tokens = new String[] {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
					} else {
						tokens = input.split(",");
					}

					List<Integer> waves = new ArrayList<Integer>();

					for (String token : tokens) {
						int wave;

						try {
							wave = Integer.parseInt(token.trim());
						} catch (NumberFormatException e) {
							return;
						}

						if (wave < 1 || wave > 10) {
							return;
						}

						if (!waves.contains(wave)) {
							waves.add(wave);
						}
					}

					Collections.sort(waves);

					var newPoint = new GroundMarkerPoint(existing.getRegionId(), existing.getRegionX(), existing.getRegionY(), existing.getZ(), existing.getColor(), existing.getLabel(), waves, existing.getRoles());
					Collection<GroundMarkerPoint> points = new ArrayList<>(getPoints(existing.getRegionId()));
					points.remove(existing);
					points.add(newPoint);
					savePoints(existing.getRegionId(), points);

					loadPoints();
				})
				.build();
	}

	private void setTileRoles(GroundMarkerPoint existing)
	{
		chatboxPanelManager.openTextInput("Tile roles")
				.value("")
				.onDone((input) ->
				{
					input = Strings.emptyToNull(input);

					String[] tokens;

					if (input == null) {
						tokens = new String[] {"a", "c", "d", "h"};
					} else {
						tokens = input.split(",");
					}

					List<String> roles = new ArrayList<String>();

					for (String token : tokens) {
						String role = token.trim();

						if (!role.equals("a") && !role.equals("c") && !role.equals("d") && !role.equals("h")) {
							return;
						}

						if (!roles.contains(role)) {
							roles.add(role);
						}
					}

					Collections.sort(roles);

					var newPoint = new GroundMarkerPoint(existing.getRegionId(), existing.getRegionX(), existing.getRegionY(), existing.getZ(), existing.getColor(), existing.getLabel(), existing.getWaves(), roles);
					Collection<GroundMarkerPoint> points = new ArrayList<>(getPoints(existing.getRegionId()));
					points.remove(existing);
					points.add(newPoint);
					savePoints(existing.getRegionId(), points);

					loadPoints();
				})
				.build();
	}

	private void copyTile(GroundMarkerPoint existing) {
		copiedPoint = existing;
	}

	private void pasteTile(LocalPoint localPoint) {
		if (copiedPoint == null) {
			return;
		}

		if (localPoint == null)
		{
			return;
		}

		WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

		int regionId = worldPoint.getRegionID();
		var newPoint = new GroundMarkerPoint(regionId, worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), copiedPoint.getColor(), copiedPoint.getLabel(), copiedPoint.getWaves(), copiedPoint.getRoles());
		Collection<GroundMarkerPoint> points = new ArrayList<>(getPoints(regionId));
		points.add(newPoint);
		savePoints(regionId, points);

		loadPoints();
	}
}
