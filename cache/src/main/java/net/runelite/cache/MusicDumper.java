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
package net.runelite.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.cache.definitions.TrackDefinition;
import net.runelite.cache.definitions.loaders.TrackLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;

/**
 * Dumps all music tracks from the OSRS cache as standard MIDI files,
 * named by archive ID. Output goes to ~/.runelite/osrs-music/
 */
public class MusicDumper
{
	private static final File JAGEX_CACHE = new File(System.getProperty("user.home"),
		".runelite/jagexcache/oldschool/LIVE");
	private static final Path OUTPUT_DIR = Path.of(System.getProperty("user.home"),
		".runelite", "osrs-music");

	public static void main(String[] args) throws Exception
	{
		Files.createDirectories(OUTPUT_DIR);

		int count = 0;
		try (Store store = new Store(JAGEX_CACHE))
		{
			store.load();
			Storage storage = store.getStorage();
			Index index = store.getIndex(IndexType.MUSIC_TRACKS);

			for (Archive archive : index.getArchives())
			{
				byte[] contents = archive.decompress(storage.loadArchive(archive));
				if (contents == null)
				{
					continue;
				}

				TrackLoader loader = new TrackLoader();
				TrackDefinition def = loader.load(contents);

				int archiveId = archive.getArchiveId();
				File dest = OUTPUT_DIR.resolve(archiveId + ".mid").toFile();
				com.google.common.io.Files.write(def.midi, dest);
				count++;
			}
		}

		System.out.println("Dumped " + count + " music tracks to " + OUTPUT_DIR);
	}
}
