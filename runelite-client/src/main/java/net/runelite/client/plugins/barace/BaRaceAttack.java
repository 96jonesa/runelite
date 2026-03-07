package net.runelite.client.plugins.barace;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaRaceAttack extends PartyMemberMessage
{
	private final int team;
	private final String role;
	// Duration in ticks. 0 means wave-based (until end of wave).
	private final int durationTicks;
}
