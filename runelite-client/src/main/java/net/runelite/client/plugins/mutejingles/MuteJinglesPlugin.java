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

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Mute Jingles",
	description = "Prevents jingles from interrupting in-game music by taking over music playback",
	tags = {"sound", "music", "jingle", "volume", "mute"},
	enabledByDefault = false
)
public class MuteJinglesPlugin extends Plugin
{
	private static final String MUSIC_RESOURCE_PATH = "/net/runelite/client/plugins/mutejingles/music/";

	private static final int CURRENTLY_PLAYING_WIDGET_CHILD = 4;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private Sequencer sequencer;
	private String currentSongName;
	private int savedMusicVolume = -1;
	private final Map<String, Integer> trackNameToArchiveId = new HashMap<>();

	@Override
	protected void startUp() throws Exception
	{
		sequencer = MidiSystem.getSequencer();
		sequencer.open();

		clientThread.invokeLater(() ->
		{
			savedMusicVolume = client.getMusicVolume();
			client.setMusicVolume(0);
			log.info("Muted in-game music (was {})", savedMusicVolume);
			buildTrackMapping();
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		stopPlayback();

		if (sequencer != null)
		{
			sequencer.close();
			sequencer = null;
		}

		currentSongName = null;
		trackNameToArchiveId.clear();

		clientThread.invokeLater(() ->
		{
			if (savedMusicVolume >= 0)
			{
				client.setMusicVolume(savedMusicVolume);
				log.info("Restored in-game music volume to {}", savedMusicVolume);
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN && trackNameToArchiveId.isEmpty())
		{
			clientThread.invokeLater(this::buildTrackMapping);
		}
	}

	private void buildTrackMapping()
	{
		trackNameToArchiveId.clear();

		List<Integer> rows = client.getDBTableRows(DBTableID.Music.ID);
		if (rows == null)
		{
			return;
		}

		for (int rowId : rows)
		{
			Object[] nameField = client.getDBTableField(rowId, DBTableID.Music.COL_DISPLAYNAME, 0);
			Object[] midiField = client.getDBTableField(rowId, DBTableID.Music.COL_MIDI, 0);

			if (nameField != null && nameField.length > 0 && midiField != null && midiField.length > 0)
			{
				String name = (String) nameField[0];
				int archiveId = (int) midiField[0];
				trackNameToArchiveId.put(name, archiveId);
			}
		}

		log.info("Built track mapping: {} tracks", trackNameToArchiveId.size());
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Keep in-game music muted
		if (client.getMusicVolume() != 0)
		{
			client.setMusicVolume(0);
		}

		// Read the currently playing track name from the music tab widget
		Widget curTrackWidget = client.getWidget(InterfaceID.MUSIC, CURRENTLY_PLAYING_WIDGET_CHILD);
		if (curTrackWidget == null)
		{
			return;
		}

		String trackName = curTrackWidget.getText();
		if (trackName == null || trackName.isEmpty())
		{
			return;
		}

		if (!trackName.equals(currentSongName))
		{
			Integer archiveId = trackNameToArchiveId.get(trackName);
			if (archiveId != null)
			{
				String resourcePath = MUSIC_RESOURCE_PATH + archiveId + ".mid";
				InputStream stream = getClass().getResourceAsStream(resourcePath);
				if (stream != null)
				{
					log.info("Song changed: {} -> {} (archiveId={})", currentSongName, trackName, archiveId);
					currentSongName = trackName;
					playSong(stream);
				}
				else
				{
					log.warn("MIDI resource not found for {} (archiveId={}): {}", trackName, archiveId, resourcePath);
				}
			}
			else
			{
				log.info("No archive ID mapping for track: '{}'", trackName);
			}
		}
	}

	private void playSong(InputStream stream)
	{
		stopPlayback();

		try
		{
			sequencer.setSequence(stream);
			sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
			sequencer.start();
			log.info("Playing song");
		}
		catch (Exception e)
		{
			log.warn("Failed to play MIDI", e);
		}
		finally
		{
			try
			{
				stream.close();
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private void stopPlayback()
	{
		if (sequencer != null && sequencer.isRunning())
		{
			sequencer.stop();
		}
	}
}
