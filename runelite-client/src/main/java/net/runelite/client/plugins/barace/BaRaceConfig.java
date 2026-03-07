package net.runelite.client.plugins.barace;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(BaRaceConfig.GROUP)
public interface BaRaceConfig extends Config
{
	String GROUP = "barace";

	@ConfigItem(
		keyName = "team",
		name = "Team",
		description = "Which team you are on (1 or 2)",
		position = 1
	)
	@Range(min = 1, max = 2)
	default int team()
	{
		return 1;
	}
}
