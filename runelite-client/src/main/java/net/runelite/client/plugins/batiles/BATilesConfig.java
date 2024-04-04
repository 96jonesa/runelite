package net.runelite.client.plugins.batiles;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(BATilesConfig.BA_TILES_CONFIG_GROUP)
public interface BATilesConfig extends Config
{
	String BA_TILES_CONFIG_GROUP = "baTiles";
	String SHOW_IMPORT_EXPORT_KEY_NAME = "showImportExport";
	String SHOW_TILES_FOR_CURRENT_ROLE_KEY_NAME = "showTilesForCurrentRole";
	String SHOW_TILES_FOR_ATTACKER_KEY_NAME = "showTilesForAttacker";
	String SHOW_TILES_FOR_COLLECTOR_KEY_NAME = "showTilesForCollector";
	String SHOW_TILES_FOR_DEFENDER_KEY_NAME = "showTilesForDefender";
	String SHOW_TILES_FOR_HEALER_KEY_NAME = "showTilesForHealer";
	String SHOW_TILES_FOR_CURRENT_WAVE_KEY_NAME = "showTilesForCurrentWave";
	String SHOW_TILES_FOR_WAVE_1_KEY_NAME = "showTilesForWave1";
	String SHOW_TILES_FOR_WAVE_2_KEY_NAME = "showTilesForWave2";
	String SHOW_TILES_FOR_WAVE_3_KEY_NAME = "showTilesForWave3";
	String SHOW_TILES_FOR_WAVE_4_KEY_NAME = "showTilesForWave4";
	String SHOW_TILES_FOR_WAVE_5_KEY_NAME = "showTilesForWave5";
	String SHOW_TILES_FOR_WAVE_6_KEY_NAME = "showTilesForWave6";
	String SHOW_TILES_FOR_WAVE_7_KEY_NAME = "showTilesForWave7";
	String SHOW_TILES_FOR_WAVE_8_KEY_NAME = "showTilesForWave8";
	String SHOW_TILES_FOR_WAVE_9_KEY_NAME = "showTilesForWave9";
	String SHOW_TILES_FOR_WAVE_10_KEY_NAME = "showTilesForWave10";

	@Alpha
	@ConfigItem(
			position = 0,
			keyName = "markerColor",
			name = "Tile color",
			description = "The default color for marked tiles"
	)
	default Color markerColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
			position = 1,
			keyName = SHOW_IMPORT_EXPORT_KEY_NAME,
			name = "Show Import/Export/Clear options",
			description = "Show the Import, Export, and Clear options on the world map right-click menu"
	)
	default boolean showImportExport()
	{
		return true;
	}

	@ConfigItem(
			position = 2,
			keyName = "borderWidth",
			name = "Border Width",
			description = "Width of the marked tile border"
	)
	default double borderWidth()
	{
		return 2;
	}

	@ConfigItem(
			position = 3,
			keyName = "fillOpacity",
			name = "Fill Opacity",
			description = "Opacity of the tile fill color"
	)
	@Range(
			max = 255
	)
	default int fillOpacity()
	{
		return 50;
	}

	@ConfigItem(
			position = 4,
			keyName = SHOW_TILES_FOR_CURRENT_ROLE_KEY_NAME,
			name = "Show tiles for current role",
			description = "Show tiles for current role"
	)
	default boolean showTilesForCurrentRole()
	{
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = SHOW_TILES_FOR_ATTACKER_KEY_NAME,
			name = "Show tiles for Attacker",
			description = "Show tiles for Attacker, regardless of current wave"
	)
	default boolean showTilesForAttacker()
	{
		return false;
	}

	@ConfigItem(
			position = 6,
			keyName = SHOW_TILES_FOR_COLLECTOR_KEY_NAME,
			name = "Show tiles for Collector",
			description = "Show tiles for Collector, regardless of current wave"
	)
	default boolean showTilesForCollector()
	{
		return false;
	}

	@ConfigItem(
			position = 7,
			keyName = SHOW_TILES_FOR_DEFENDER_KEY_NAME,
			name = "Show tiles for Defender",
			description = "Show tiles for Defender, regardless of current wave"
	)
	default boolean showTilesForDefender()
	{
		return false;
	}

	@ConfigItem(
			position = 8,
			keyName = SHOW_TILES_FOR_HEALER_KEY_NAME,
			name = "Show tiles for Healer",
			description = "Show tiles for Healer, regardless of current wave"
	)
	default boolean showTilesForHealer()
	{
		return false;
	}

	@ConfigItem(
			position = 9,
			keyName = SHOW_TILES_FOR_CURRENT_WAVE_KEY_NAME,
			name = "Show tiles for current wave",
			description = "Show tiles for current wave"
	)
	default boolean showTilesForCurrentWave()
	{
		return true;
	}

	@ConfigItem(
			position = 10,
			keyName = SHOW_TILES_FOR_WAVE_1_KEY_NAME,
			name = "Show tiles for wave 1",
			description = "Show tiles for wave 1, regardless of current wave"
	)
	default boolean showTilesForWave1()
	{
		return false;
	}

	@ConfigItem(
			position = 11,
			keyName = SHOW_TILES_FOR_WAVE_2_KEY_NAME,
			name = "Show tiles for wave 2",
			description = "Show tiles for wave 2, regardless of current wave"
	)
	default boolean showTilesForWave2()
	{
		return false;
	}

	@ConfigItem(
			position = 12,
			keyName = SHOW_TILES_FOR_WAVE_3_KEY_NAME,
			name = "Show tiles for wave 3",
			description = "Show tiles for wave 3, regardless of current wave"
	)
	default boolean showTilesForWave3()
	{
		return false;
	}

	@ConfigItem(
			position = 13,
			keyName = SHOW_TILES_FOR_WAVE_4_KEY_NAME,
			name = "Show tiles for wave 4",
			description = "Show tiles for wave 4, regardless of current wave"
	)
	default boolean showTilesForWave4()
	{
		return false;
	}

	@ConfigItem(
			position = 14,
			keyName = SHOW_TILES_FOR_WAVE_5_KEY_NAME,
			name = "Show tiles for wave 5",
			description = "Show tiles for wave 5, regardless of current wave"
	)
	default boolean showTilesForWave5()
	{
		return false;
	}

	@ConfigItem(
			position = 15,
			keyName = SHOW_TILES_FOR_WAVE_6_KEY_NAME,
			name = "Show tiles for wave 6",
			description = "Show tiles for wave 6, regardless of current wave"
	)
	default boolean showTilesForWave6()
	{
		return false;
	}

	@ConfigItem(
			position = 16,
			keyName = SHOW_TILES_FOR_WAVE_7_KEY_NAME,
			name = "Show tiles for wave 7",
			description = "Show tiles for wave 7, regardless of current wave"
	)
	default boolean showTilesForWave7()
	{
		return false;
	}

	@ConfigItem(
			position = 17,
			keyName = SHOW_TILES_FOR_WAVE_8_KEY_NAME,
			name = "Show tiles for wave 8",
			description = "Show tiles for wave 8, regardless of current wave"
	)
	default boolean showTilesForWave8()
	{
		return false;
	}

	@ConfigItem(
			position = 18,
			keyName = SHOW_TILES_FOR_WAVE_9_KEY_NAME,
			name = "Show tiles for wave 9",
			description = "Show tiles for wave 9, regardless of current wave"
	)
	default boolean showTilesForWave9()
	{
		return false;
	}

	@ConfigItem(
			position = 19,
			keyName = SHOW_TILES_FOR_WAVE_10_KEY_NAME,
			name = "Show tiles for wave 10",
			description = "Show tiles for wave 10, regardless of current wave"
	)
	default boolean showTilesForWave10()
	{
		return false;
	}
}
