package net.runelite.client.plugins.barace;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Renderable;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

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

	private static final int TEST_ATTACK_TICKS = 10;

	private static final Set<Integer> PENANCE_RUNNER_IDS = ImmutableSet.of(
		NpcID.BARBASSAULT_PEN_RUNNER_LV1,
		NpcID.BARBASSAULT_PEN_RUNNER_LV2,
		NpcID.BARBASSAULT_PEN_RUNNER_LV3,
		NpcID.BARBASSAULT_PEN_RUNNER_LV4,
		NpcID.BARBASSAULT_PEN_RUNNER_LV5,
		NpcID.BARBASSAULT_PEN_RUNNER_LV6,
		NpcID.BARBASSAULT_PEN_RUNNER_LV7,
		NpcID.BARBASSAULT_PEN_RUNNER_LV8,
		NpcID.BARBASSAULT_PEN_RUNNER_LV9
	);

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

	@Inject
	private KeyManager keyManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	private Role currentRole;
	private Attack activeAttack;
	private Attack pendingAttack;
	private boolean inWave;
	private boolean hasAttacked;
	private boolean isTestAttack;
	private int ticksRemaining;

	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			if (activeAttack == Attack.HIDE_RUNNERS && renderable instanceof NPC)
			{
				NPC npc = (NPC) renderable;
				if (PENANCE_RUNNER_IDS.contains(npc.getId())
					|| "Private Pierreb".equals(npc.getName()))
				{
					return false;
				}
			}
			return true;
		}
	};

	private final HotkeyListener attackHotkeyListener = new HotkeyListener(() -> config.attackHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			triggerAttack();
		}
	};

	private final HotkeyListener testAttackHotkeyListener = new HotkeyListener(() -> config.testAttackHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			triggerTestAttack();
		}
	};

	@Provides
	BaRaceConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BaRaceConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(BaRaceAttack.class);
		keyManager.registerKeyListener(attackHotkeyListener);
		keyManager.registerKeyListener(testAttackHotkeyListener);
		renderCallbackManager.register(renderCallback);
		resetState();
	}

	@Override
	protected void shutDown()
	{
		wsClient.unregisterMessage(BaRaceAttack.class);
		keyManager.unregisterKeyListener(attackHotkeyListener);
		keyManager.unregisterKeyListener(testAttackHotkeyListener);
		renderCallbackManager.unregister(renderCallback);
		resetState();
	}

	private void resetState()
	{
		currentRole = null;
		activeAttack = null;
		pendingAttack = null;
		inWave = false;
		hasAttacked = false;
		isTestAttack = false;
		ticksRemaining = 0;
	}

	private Role getEffectiveRole()
	{
		RoleOverride override = config.roleOverride();
		if (override == RoleOverride.CURRENT)
		{
			return currentRole;
		}
		return override.getRole();
	}

	private static Attack defaultAttackForRole(Role role)
	{
		switch (role)
		{
			case ATTACKER:
				return Attack.BLOCK_INVENTORY;
			case COLLECTOR:
				return Attack.JUNK_MENU_OPTIONS;
			case HEALER:
				return Attack.BLOCK_LEFT_CLICK_INVENTORY;
			case DEFENDER:
				return Attack.HIDE_RUNNERS;
			default:
				return Attack.JUNK_MENU_OPTIONS;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		switch (event.getGroupId())
		{
			case InterfaceID.BARBASSAULT_OVER_ATT:
				currentRole = Role.ATTACKER;
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_DEF:
				currentRole = Role.DEFENDER;
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_HEAL:
				currentRole = Role.HEALER;
				onWaveStart();
				break;
			case InterfaceID.BARBASSAULT_OVER_COL:
				currentRole = Role.COLLECTOR;
				onWaveStart();
				break;
		}
	}

	private void onWaveStart()
	{
		inWave = true;

		if (pendingAttack != null)
		{
			activeAttack = pendingAttack;
			pendingAttack = null;
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
				activeAttack = null;
				pendingAttack = null;
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.BARBASSAULT_AREAEXIT_PENDING && event.getValue() == 0)
		{
			inWave = false;
			activeAttack = null;
			currentRole = null;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ticksRemaining > 0)
		{
			ticksRemaining--;
			if (ticksRemaining == 0)
			{
				activeAttack = null;
				if (isTestAttack)
				{
					announceMessage("Test attack expired.");
					isTestAttack = false;
				}
			}
		}
	}

	// Attacker attack: block all inventory interactions
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (activeAttack != Attack.BLOCK_INVENTORY)
		{
			return;
		}

		MenuEntry[] menuEntries = client.getMenuEntries();
		MenuEntry[] filtered = new MenuEntry[menuEntries.length];
		int idx = 0;

		for (MenuEntry entry : menuEntries)
		{
			Widget widget = entry.getWidget();
			if (widget != null && WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.INVENTORY)
			{
				continue;
			}
			filtered[idx++] = entry;
		}

		if (idx < menuEntries.length)
		{
			client.setMenuEntries(Arrays.copyOf(filtered, idx));
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (activeAttack == null)
		{
			return;
		}

		if (activeAttack == Attack.BLOCK_INVENTORY)
		{
			Widget widget = event.getMenuEntry().getWidget();
			if (widget != null && WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.INVENTORY)
			{
				event.getMenuEntry().setType(MenuAction.RUNELITE);
				event.getMenuEntry().onClick(e -> {});
			}
		}

		if (activeAttack == Attack.JUNK_MENU_OPTIONS && "Walk here".equals(event.getOption()))
		{
			for (String junkOption : JUNK_OPTIONS)
			{
				client.createMenuEntry(-1)
					.setOption(junkOption)
					.setTarget(event.getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(e -> {});
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (activeAttack != Attack.BLOCK_LEFT_CLICK_INVENTORY)
		{
			return;
		}

		Widget widget = event.getWidget();
		if (widget != null && WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.INVENTORY
			&& !client.isMenuOpen())
		{
			event.consume();
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
		// Only affected if on the opposing team
		if (event.getTeam() == config.team())
		{
			return;
		}

		Role effectiveRole = getEffectiveRole();

		// Must match one of the targeted roles
		if (effectiveRole == null || !event.getRoles().contains(effectiveRole))
		{
			return;
		}

		Attack attack = event.getAttack();

		if (event.getDurationTicks() > 0)
		{
			// Tick-based attack
			activeAttack = attack;
			isTestAttack = event.isTest();
			ticksRemaining = event.getDurationTicks();
			if (event.isTest())
			{
				announceMessage("Under " + attack + " attack for " + event.getDurationTicks() + " ticks!");
			}
		}
		else if (inWave)
		{
			activeAttack = attack;
		}
		else
		{
			pendingAttack = attack;
		}
	}

	public void triggerAttack()
	{
		if (!party.isInParty())
		{
			announceMessage("You must be in a party to use BA Race attacks.");
			return;
		}

		Role effectiveRole = getEffectiveRole();
		if (effectiveRole == null)
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

		BaRaceAttack attack = new BaRaceAttack(config.team(), Collections.singletonList(effectiveRole), defaultAttackForRole(effectiveRole), 0, false);
		party.send(attack);

		announceMessage("Attack sent! Opposing " + effectiveRole.name().toLowerCase() + "s will be disrupted.");
	}

	private void triggerTestAttack()
	{
		if (!party.isInParty())
		{
			announceMessage("You must be in a party to use test attacks.");
			return;
		}

		Role effectiveRole = getEffectiveRole();
		if (effectiveRole == null)
		{
			announceMessage("You must set a role override or be in a BA wave to use test attacks.");
			return;
		}

		BaRaceAttack attack = new BaRaceAttack(config.team(), Collections.singletonList(effectiveRole), defaultAttackForRole(effectiveRole), TEST_ATTACK_TICKS, true);
		party.send(attack);

		announceMessage("Test " + effectiveRole.name().toLowerCase() + " attack sent for " + TEST_ATTACK_TICKS + " ticks.");
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
