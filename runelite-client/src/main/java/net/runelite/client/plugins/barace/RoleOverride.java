package net.runelite.client.plugins.barace;

import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleOverride
{
	CURRENT("Current", null),
	ATTACKER("Attacker", Role.ATTACKER),
	COLLECTOR("Collector", Role.COLLECTOR),
	HEALER("Healer", Role.HEALER),
	DEFENDER("Defender", Role.DEFENDER);

	private final String name;
	@Nullable
	private final Role role;

	@Override
	public String toString()
	{
		return name;
	}
}
