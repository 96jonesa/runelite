package net.runelite.client.plugins.barace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleOverride
{
	CURRENT("Current"),
	ATTACKER("Attacker"),
	COLLECTOR("Collector"),
	HEALER("Healer"),
	DEFENDER("Defender");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
