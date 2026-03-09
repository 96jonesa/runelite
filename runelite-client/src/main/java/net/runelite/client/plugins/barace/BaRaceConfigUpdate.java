package net.runelite.client.plugins.barace;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaRaceConfigUpdate extends PartyMemberMessage
{
	private final Role role;
	private final Attack attack;
	private final int durationTicks;
	private final Set<Role> targetRoles;
}
