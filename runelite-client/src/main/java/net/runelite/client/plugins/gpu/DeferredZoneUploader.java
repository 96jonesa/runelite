/*
 * Copyright (c) 2026, Andrew Jones
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
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THE
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.gpu;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Scene;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.plugins.gpu.DeferredUploadScheduler.PendingZone;

/**
 * Fills the zones that {@link GpuPlugin#loadScene} left pending, on a worker thread, after the scene
 * has been swapped in.
 * <p>
 * A pending zone already has its buffers allocated and mapped (that happened on the client thread
 * during the load), so the worker only writes vertex data into mapped memory with its own
 * {@link SceneUploader} and never touches GL. When a zone is filled, the worker posts a completion to
 * the client thread, which unmaps the zone and marks it initialized so the next frame draws it.
 * <p>
 * The worker reads the live scene, which the client thread may be changing (object updates arrive
 * right after a map load), so a fill can fail. A failed zone is emptied, completed with its
 * invalidate flag set, and rebuilt from the current scene contents by the plugin's normal rebuild
 * path on the client thread.
 * <p>
 * Freeing a pending zone while the worker writes to it would be a use after unmap, so every path that
 * frees zones (the next scene load, plugin shutdown) calls {@link #cancel()} first, which drops the
 * queue and waits for the zone being filled.
 */
@Slf4j
class DeferredZoneUploader
{
	private final ClientThread clientThread;
	private final SceneUploader uploader;
	private final DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
	private final ExecutorService executor;

	// zone nearest the camera, in extended zone coordinates; steers which pending zone is filled next
	private volatile int focusX;
	private volatile int focusZ;

	// guarded by this
	private Future<?> drain;

	// client thread only
	private int total;
	private int finished;
	private int failed;
	private Stopwatch stopwatch;

	DeferredZoneUploader(ClientThread clientThread, RenderCallbackManager renderCallbackManager)
	{
		this.clientThread = clientThread;
		this.uploader = new SceneUploader(renderCallbackManager);
		this.executor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "GPU deferred upload");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Update the zone the worker should fill towards. Client thread, once per frame.
	 */
	void setFocus(int zx, int zz)
	{
		focusX = zx;
		focusZ = zz;
	}

	/**
	 * Queue every pending zone of the freshly swapped scene and start filling them. Client thread.
	 */
	void start(Scene scene, Zone[][] zones)
	{
		int n = 0;
		for (int x = 0; x < zones.length; ++x)
		{
			for (int z = 0; z < zones[x].length; ++z)
			{
				Zone zone = zones[x][z];
				if (zone.pending)
				{
					assert !zone.initialized;
					scheduler.add(x, z, zone);
					++n;
				}
			}
		}

		if (n == 0)
		{
			return;
		}

		total = n;
		finished = 0;
		failed = 0;
		stopwatch = Stopwatch.createStarted();
		// the client builds every scene around the player's zone, so until the first frame reports the
		// camera the scene centre is the best guess
		setFocus(zones.length >> 1, zones[0].length >> 1);

		synchronized (this)
		{
			assert drain == null || drain.isDone() : "deferred upload started while the previous one is running";
			final int gen = scheduler.generation();
			drain = executor.submit(() -> drain(scene, gen));
		}

		log.debug("Deferred upload of {} zones started", n);
	}

	/**
	 * Drop every queued zone and wait for the zone currently being filled, if any, so that the caller
	 * may free pending zones afterwards. Completions the worker already posted are ignored by the client
	 * thread because they carry the old generation. Must not be called from the worker thread.
	 */
	void cancel()
	{
		Future<?> f;
		int dropped;
		synchronized (this)
		{
			dropped = scheduler.size();
			scheduler.cancel();
			f = drain;
			drain = null;
		}

		if (f != null)
		{
			try
			{
				// an interrupted wait must not return early: the caller frees the zones next
				Uninterruptibles.getUninterruptibly(f);
			}
			catch (ExecutionException e)
			{
				log.error("Deferred upload worker died", e.getCause());
			}
		}

		if (dropped > 0)
		{
			log.debug("Deferred upload cancelled with {} zones pending", dropped);
		}
	}

	void shutdown()
	{
		cancel();
		executor.shutdownNow();
	}

	// worker thread
	private void drain(Scene scene, int gen)
	{
		PendingZone p;
		while ((p = scheduler.pollNearest(gen, focusX, focusZ)) != null)
		{
			boolean ok;
			try
			{
				uploader.uploadZone(scene, p.zone, p.zx, p.zz);
				ok = true;
			}
			catch (Throwable ex) // AssertionError too, so one bad zone under -ea does not stop the drain
			{
				// e.g. an object added by the client after the zone was sized overflows the buffer
				log.warn("Error uploading deferred zone x={} z={}, it will be rebuilt", p.zx, p.zz, ex);
				p.zone.discardUpload();
				ok = false;
			}

			final PendingZone done = p;
			final boolean uploaded = ok;
			clientThread.invoke(() -> finish(done, gen, uploaded));
		}
	}

	// client thread
	private void finish(PendingZone p, int gen, boolean uploaded)
	{
		Zone zone = p.zone;
		if (!scheduler.isCurrent(gen) || !zone.pending)
		{
			// cancelled after the fill; the zone is freed with the scene it belongs to
			return;
		}

		zone.unmap();
		zone.initialized = true;
		zone.pending = false;
		if (!uploaded)
		{
			// the zone is empty; the next PostClientTick rebuilds it from the live scene
			zone.invalidate = true;
			++failed;
		}

		++finished;
		log.trace("Deferred zone ready x={} z={} ({} of {})", p.zx, p.zz, finished, total);
		if (finished == total)
		{
			log.debug("Deferred upload complete: {} zones in {}, {} failed and queued for rebuild", total, stopwatch, failed);
		}
	}
}
