package net.runelite.client.plugins.barace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TestAttackRole
{
	ATTACKER("ATTACKER"),
	COLLECTOR("COLLECTOR"),
	HEALER("HEALER"),
	DEFENDER("DEFENDER");

	private final String roleKey;
}
