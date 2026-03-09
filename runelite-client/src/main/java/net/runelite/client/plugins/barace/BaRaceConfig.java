package net.runelite.client.plugins.barace;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
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

	@ConfigItem(
		keyName = "attackHotkey",
		name = "Attack hotkey",
		description = "Key to trigger an attack on the opposing team",
		position = 2
	)
	default Keybind attackHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "testAttackHotkey",
		name = "Test attack hotkey",
		description = "Key to trigger a test attack (10 ticks, no restrictions)",
		position = 3
	)
	default Keybind testAttackHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "roleOverride",
		name = "Role override",
		description = "Override which role the plugin considers you as (for testing)",
		position = 4
	)
	default RoleOverride roleOverride()
	{
		return RoleOverride.CURRENT;
	}

	// --- Attacker Attack ---

	@ConfigSection(
		name = "Attacker Attack",
		description = "Configure the attack used by the attacker role",
		position = 10
	)
	String attackerSection = "attackerSection";

	@ConfigItem(keyName = "attackerAttack", name = "Attack", description = "Which attack effect to apply",
		position = 11, section = attackerSection)
	default Attack attackerAttack() { return Attack.BLOCK_INVENTORY; }

	@ConfigItem(keyName = "attackerAttack", name = "", description = "")
	void setAttackerAttack(Attack attack);

	@ConfigItem(keyName = "attackerDuration", name = "Duration (ticks)", description = "0 = until end of wave",
		position = 12, section = attackerSection)
	default int attackerDuration() { return 0; }

	@ConfigItem(keyName = "attackerDuration", name = "", description = "")
	void setAttackerDuration(int duration);

	@ConfigItem(keyName = "attackerTargetAttacker", name = "Target: Attacker", description = "",
		position = 13, section = attackerSection)
	default boolean attackerTargetAttacker() { return true; }

	@ConfigItem(keyName = "attackerTargetAttacker", name = "", description = "")
	void setAttackerTargetAttacker(boolean v);

	@ConfigItem(keyName = "attackerTargetCollector", name = "Target: Collector", description = "",
		position = 14, section = attackerSection)
	default boolean attackerTargetCollector() { return false; }

	@ConfigItem(keyName = "attackerTargetCollector", name = "", description = "")
	void setAttackerTargetCollector(boolean v);

	@ConfigItem(keyName = "attackerTargetHealer", name = "Target: Healer", description = "",
		position = 15, section = attackerSection)
	default boolean attackerTargetHealer() { return false; }

	@ConfigItem(keyName = "attackerTargetHealer", name = "", description = "")
	void setAttackerTargetHealer(boolean v);

	@ConfigItem(keyName = "attackerTargetDefender", name = "Target: Defender", description = "",
		position = 16, section = attackerSection)
	default boolean attackerTargetDefender() { return false; }

	@ConfigItem(keyName = "attackerTargetDefender", name = "", description = "")
	void setAttackerTargetDefender(boolean v);

	// --- Collector Attack ---

	@ConfigSection(
		name = "Collector Attack",
		description = "Configure the attack used by the collector role",
		position = 20
	)
	String collectorSection = "collectorSection";

	@ConfigItem(keyName = "collectorAttack", name = "Attack", description = "Which attack effect to apply",
		position = 21, section = collectorSection)
	default Attack collectorAttack() { return Attack.JUNK_MENU_OPTIONS; }

	@ConfigItem(keyName = "collectorAttack", name = "", description = "")
	void setCollectorAttack(Attack attack);

	@ConfigItem(keyName = "collectorDuration", name = "Duration (ticks)", description = "0 = until end of wave",
		position = 22, section = collectorSection)
	default int collectorDuration() { return 0; }

	@ConfigItem(keyName = "collectorDuration", name = "", description = "")
	void setCollectorDuration(int duration);

	@ConfigItem(keyName = "collectorTargetAttacker", name = "Target: Attacker", description = "",
		position = 23, section = collectorSection)
	default boolean collectorTargetAttacker() { return false; }

	@ConfigItem(keyName = "collectorTargetAttacker", name = "", description = "")
	void setCollectorTargetAttacker(boolean v);

	@ConfigItem(keyName = "collectorTargetCollector", name = "Target: Collector", description = "",
		position = 24, section = collectorSection)
	default boolean collectorTargetCollector() { return true; }

	@ConfigItem(keyName = "collectorTargetCollector", name = "", description = "")
	void setCollectorTargetCollector(boolean v);

	@ConfigItem(keyName = "collectorTargetHealer", name = "Target: Healer", description = "",
		position = 25, section = collectorSection)
	default boolean collectorTargetHealer() { return false; }

	@ConfigItem(keyName = "collectorTargetHealer", name = "", description = "")
	void setCollectorTargetHealer(boolean v);

	@ConfigItem(keyName = "collectorTargetDefender", name = "Target: Defender", description = "",
		position = 26, section = collectorSection)
	default boolean collectorTargetDefender() { return false; }

	@ConfigItem(keyName = "collectorTargetDefender", name = "", description = "")
	void setCollectorTargetDefender(boolean v);

	// --- Healer Attack ---

	@ConfigSection(
		name = "Healer Attack",
		description = "Configure the attack used by the healer role",
		position = 30
	)
	String healerSection = "healerSection";

	@ConfigItem(keyName = "healerAttack", name = "Attack", description = "Which attack effect to apply",
		position = 31, section = healerSection)
	default Attack healerAttack() { return Attack.BLOCK_LEFT_CLICK_INVENTORY; }

	@ConfigItem(keyName = "healerAttack", name = "", description = "")
	void setHealerAttack(Attack attack);

	@ConfigItem(keyName = "healerDuration", name = "Duration (ticks)", description = "0 = until end of wave",
		position = 32, section = healerSection)
	default int healerDuration() { return 0; }

	@ConfigItem(keyName = "healerDuration", name = "", description = "")
	void setHealerDuration(int duration);

	@ConfigItem(keyName = "healerTargetAttacker", name = "Target: Attacker", description = "",
		position = 33, section = healerSection)
	default boolean healerTargetAttacker() { return false; }

	@ConfigItem(keyName = "healerTargetAttacker", name = "", description = "")
	void setHealerTargetAttacker(boolean v);

	@ConfigItem(keyName = "healerTargetCollector", name = "Target: Collector", description = "",
		position = 34, section = healerSection)
	default boolean healerTargetCollector() { return false; }

	@ConfigItem(keyName = "healerTargetCollector", name = "", description = "")
	void setHealerTargetCollector(boolean v);

	@ConfigItem(keyName = "healerTargetHealer", name = "Target: Healer", description = "",
		position = 35, section = healerSection)
	default boolean healerTargetHealer() { return true; }

	@ConfigItem(keyName = "healerTargetHealer", name = "", description = "")
	void setHealerTargetHealer(boolean v);

	@ConfigItem(keyName = "healerTargetDefender", name = "Target: Defender", description = "",
		position = 36, section = healerSection)
	default boolean healerTargetDefender() { return false; }

	@ConfigItem(keyName = "healerTargetDefender", name = "", description = "")
	void setHealerTargetDefender(boolean v);

	// --- Defender Attack ---

	@ConfigSection(
		name = "Defender Attack",
		description = "Configure the attack used by the defender role",
		position = 40
	)
	String defenderSection = "defenderSection";

	@ConfigItem(keyName = "defenderAttack", name = "Attack", description = "Which attack effect to apply",
		position = 41, section = defenderSection)
	default Attack defenderAttack() { return Attack.HIDE_RUNNERS; }

	@ConfigItem(keyName = "defenderAttack", name = "", description = "")
	void setDefenderAttack(Attack attack);

	@ConfigItem(keyName = "defenderDuration", name = "Duration (ticks)", description = "0 = until end of wave",
		position = 42, section = defenderSection)
	default int defenderDuration() { return 0; }

	@ConfigItem(keyName = "defenderDuration", name = "", description = "")
	void setDefenderDuration(int duration);

	@ConfigItem(keyName = "defenderTargetAttacker", name = "Target: Attacker", description = "",
		position = 43, section = defenderSection)
	default boolean defenderTargetAttacker() { return false; }

	@ConfigItem(keyName = "defenderTargetAttacker", name = "", description = "")
	void setDefenderTargetAttacker(boolean v);

	@ConfigItem(keyName = "defenderTargetCollector", name = "Target: Collector", description = "",
		position = 44, section = defenderSection)
	default boolean defenderTargetCollector() { return false; }

	@ConfigItem(keyName = "defenderTargetCollector", name = "", description = "")
	void setDefenderTargetCollector(boolean v);

	@ConfigItem(keyName = "defenderTargetHealer", name = "Target: Healer", description = "",
		position = 45, section = defenderSection)
	default boolean defenderTargetHealer() { return false; }

	@ConfigItem(keyName = "defenderTargetHealer", name = "", description = "")
	void setDefenderTargetHealer(boolean v);

	@ConfigItem(keyName = "defenderTargetDefender", name = "Target: Defender", description = "",
		position = 46, section = defenderSection)
	default boolean defenderTargetDefender() { return true; }

	@ConfigItem(keyName = "defenderTargetDefender", name = "", description = "")
	void setDefenderTargetDefender(boolean v);
}
