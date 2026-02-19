/*
 * Copyright (c) 2025
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.mutejingles;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Mute Jingles",
	description = "Mutes jingles (level up, quest complete, etc.) to prevent in-game music from restarting",
	tags = {"sound", "music", "jingle", "volume", "mute"},
	enabledByDefault = false
)
public class MuteJinglesPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private int previousJingleVolume = -1;

	@Override
	protected void startUp()
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				muteJingles();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(this::restoreJingles);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState gameState = gameStateChanged.getGameState();
		if (gameState == GameState.LOGGED_IN)
		{
			muteJingles();
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			previousJingleVolume = -1;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (varbitChanged.getVarpId() == VarPlayerID.OPTION_JINGLES
			&& varbitChanged.getValue() != 0)
		{
			previousJingleVolume = varbitChanged.getValue();
			client.getVarps()[VarPlayerID.OPTION_JINGLES] = 0;
			client.queueChangedVarp(VarPlayerID.OPTION_JINGLES);
		}
	}

	private void muteJingles()
	{
		previousJingleVolume = client.getVarpValue(VarPlayerID.OPTION_JINGLES);
		client.getVarps()[VarPlayerID.OPTION_JINGLES] = 0;
		client.queueChangedVarp(VarPlayerID.OPTION_JINGLES);
	}

	private void restoreJingles()
	{
		if (previousJingleVolume != -1)
		{
			client.getVarps()[VarPlayerID.OPTION_JINGLES] = previousJingleVolume;
			client.queueChangedVarp(VarPlayerID.OPTION_JINGLES);
			previousJingleVolume = -1;
		}
	}
}
