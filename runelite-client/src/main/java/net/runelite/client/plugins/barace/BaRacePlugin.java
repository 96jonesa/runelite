package net.runelite.client.plugins.barace;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "BA Race",
	description = "Barbarian Assault racing between two teams with cross-team attacks",
	tags = {"barbarian", "assault", "race", "minigame", "party"},
	enabledByDefault = false
)
@Slf4j
public class BaRacePlugin extends Plugin
{
	private static final String[] JUNK_OPTIONS = {
		"Recalibrate",
		"Harmonize",
		"Synchronize",
		"Defragment",
		"Reconfigure"
	};

	@Inject
	private Client client;

	@Inject
	private BaRaceConfig config;

	@Inject
	private PartyService party;

	@Inject
	private WSClient wsClient;

	@Inject
	private ChatMessageManager chatMessageManager;

	private static final int TEST_ATTACK_TICKS = 10;

	private String currentRole;
	private boolean inWave;
	private boolean hasAttacked;
	private boolean underAttack;
	private boolean attackPending;
	private int testAttackTicksRemaining;

	@Provides
	BaRaceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaRaceConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(BaRaceAttack.class);
		resetState();
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(BaRaceAttack.class);
		resetState();
	}

	private void resetState()
	{
		currentRole = null;
		inWave = false;
		hasAttacked = false;
		underAttack = false;
		attackPending = false;
		testAttackTicksRemaining = 0;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.BARBASSAULT_OVER_ATT:
				currentRole = "ATTACKER";
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_DEF:
				currentRole = "DEFENDER";
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_HEAL:
				currentRole = "HEALER";
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_COL:
				currentRole = "COLLECTOR";
				onWaveStart();
				break;
		}
	}

	private void onWaveStart()
	{
		inWave = true;

		if (attackPending)
		{
			underAttack = true;
			attackPending = false;
			announceMessage("You are under attack! Extra menu options until end of wave.");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& event.getMessage().startsWith("---- Wave:"))
		{
			String[] message = event.getMessage().split(" ");
			String wave = message[2];

			if (wave.equals("1"))
			{
				hasAttacked = false;
				underAttack = false;
				attackPending = false;
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.BARBASSAULT_AREAEXIT_PENDING && event.getValue() == 0)
		{
			inWave = false;
			underAttack = false;
			currentRole = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (testAttackTicksRemaining > 0)
		{
			testAttackTicksRemaining--;
			if (testAttackTicksRemaining == 0)
			{
				underAttack = false;
				announceMessage("Test attack expired.");
			}
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!underAttack)
		{
			return;
		}

		// Only add junk options on the first real menu entry per menu open
		// to avoid adding them repeatedly. We add them when we see "Walk here"
		// which is typically the last (bottom) entry added.
		if (!"Walk here".equals(event.getOption()))
		{
			return;
		}

		for (String junkOption : JUNK_OPTIONS)
		{
			client.createMenuEntry(-1)
				.setOption(junkOption)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> {});
		}
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (event.getCommand().equalsIgnoreCase("baattack"))
		{
			triggerAttack();
		}
		else if (event.getCommand().equalsIgnoreCase("testbaattack"))
		{
			triggerTestAttack();
		}
	}

	@Subscribe
	public void onBaRaceAttack(BaRaceAttack event)
	{
		int attackerTeam = event.getTeam();
		String attackerRole = event.getRole();
		int myTeam = config.team();

		// Only affected if on the opposing team
		if (attackerTeam == myTeam)
		{
			return;
		}

		// "ALL" targets every role, otherwise must match
		boolean roleMatches = "ALL".equals(attackerRole)
			|| (currentRole != null && currentRole.equals(attackerRole));

		if (!roleMatches)
		{
			return;
		}

		if (event.getDurationTicks() > 0)
		{
			// Tick-based attack (e.g. test command)
			underAttack = true;
			testAttackTicksRemaining = event.getDurationTicks();
			announceMessage("Under attack for " + event.getDurationTicks() + " ticks!");
		}
		else if (inWave)
		{
			underAttack = true;
			announceMessage("You are under attack! Extra menu options until end of wave.");
		}
		else
		{
			attackPending = true;
			announceMessage("Attack incoming! Extra menu options will apply next wave.");
		}
	}

	public void triggerAttack()
	{
		if (!party.isInParty())
		{
			announceMessage("You must be in a party to use BA Race attacks.");
			return;
		}

		if (currentRole == null)
		{
			announceMessage("You must be in a BA wave to trigger an attack.");
			return;
		}

		if (hasAttacked)
		{
			announceMessage("You have already used your attack this run.");
			return;
		}

		hasAttacked = true;

		BaRaceAttack attack = new BaRaceAttack(config.team(), currentRole, 0);
		party.send(attack);

		announceMessage("Attack sent! Opposing " + currentRole.toLowerCase() + "s will be disrupted.");
	}

	private void triggerTestAttack()
	{
		if (!party.isInParty())
		{
			announceMessage("You must be in a party to use test attacks.");
			return;
		}

		BaRaceAttack attack = new BaRaceAttack(config.team(), "ALL", TEST_ATTACK_TICKS);
		party.send(attack);

		announceMessage("Test attack sent to all opposing team members for " + TEST_ATTACK_TICKS + " ticks.");
	}

	private void announceMessage(String text)
	{
		final String chatMessage = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[BA Race] ")
			.append(ChatColorType.NORMAL)
			.append(text)
			.build();

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(chatMessage)
			.build());
	}
}
