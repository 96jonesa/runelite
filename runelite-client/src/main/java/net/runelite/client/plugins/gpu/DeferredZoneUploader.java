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
import java.nio.IntBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Scene;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.plugins.gpu.DeferredUploadScheduler.PendingZone;

/**
 * Fills the zones that the scene swap left pending, on a worker thread, after the scene has been
 * swapped in.
 * <p>
 * A pending zone has not been sized: the worker sizes it, slices staging for it from its own arena in
 * CPU memory, and fills it with its own {@link SceneUploader}, never touching GL. When a zone is
 * filled, the worker queues a completion; the client thread commits queued completions at the start of
 * every frame ({@link #commitFinished()}), creating the zone's GL buffers from the staging and marking
 * it initialized, so a zone finished during a frame is drawn by the next one.
 * <p>
 * The worker reads the live scene, which the client thread may be changing (object updates arrive
 * right after a map load), so a fill can fail. A failed zone is emptied, completed with its
 * invalidate flag set, and rebuilt from the current scene contents by the plugin's normal rebuild
 * path on the client thread.
 * <p>
 * The next scene stops this one's worker first with {@link #abandon()}, from the swap on the client
 * thread or from loadScene on the loader thread when deferral is off: it drops the queue and lets the
 * zone being filled finish on its own, since that zone leaves the live table without ever holding GL
 * objects. Only shutdown uses {@link #cancel()}, which also waits for the zone being filled, because
 * shutdown then frees every zone of the live table, pending ones included. A completion still queued
 * after either carries a stale generation and is ignored; the generation check and the commit in
 * {@link #finish} are one step under the monitor the generation is bumped under.
 */
@Slf4j
class DeferredZoneUploader
{
	private static final int MIN_ARENA_INTS = 4 << 20; // 16 MB

	private final SceneUploader uploader;
	private final DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
	private final ExecutorService executor;
	// creates a finished zone's GL objects; Zone::commit outside of tests
	private final Consumer<Zone> committer;

	// zones the worker has filled, waiting for the client thread to commit them
	private final ConcurrentLinkedQueue<Completion> completed = new ConcurrentLinkedQueue<>();

	// worker thread only. Slices already handed to zones keep an outgrown arena alive until they commit.
	private IntBuffer arena;

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

	private static final class Completion
	{
		final PendingZone zone;
		final int gen;
		final boolean uploaded;

		Completion(PendingZone zone, int gen, boolean uploaded)
		{
			this.zone = zone;
			this.gen = gen;
			this.uploaded = uploaded;
		}
	}

	DeferredZoneUploader(RenderCallbackManager renderCallbackManager)
	{
		this(new SceneUploader(renderCallbackManager), Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "GPU deferred upload");
			t.setDaemon(true);
			return t;
		}), Zone::commit);
	}

	DeferredZoneUploader(SceneUploader uploader, ExecutorService executor, Consumer<Zone> committer)
	{
		this.uploader = uploader;
		this.executor = executor;
		this.committer = committer;
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
			// an abandoned drain may still be running; the single worker thread runs this one after it, so the
			// arena reset at the top of drain() cannot clobber a slice the old one is writing
			final int gen = scheduler.generation();
			drain = executor.submit(() -> drain(scene, gen));
		}

		log.debug("Deferred upload of {} zones started", n);
	}

	/**
	 * Drop every queued zone and wait for the zone currently being filled, if any, so that the caller
	 * may free every zone afterwards, including the one the worker was writing. Completions the worker
	 * already posted are ignored because they carry the old generation. Must not be called from the
	 * worker thread.
	 */
	void cancel()
	{
		Future<?> f;
		int dropped;
		synchronized (this)
		{
			dropped = scheduler.size();
			scheduler.cancel();
			completed.clear(); // all stale now
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
				// already logged by drain(); nothing more to do than not to wait on it
				log.debug("Deferred upload worker had died", e.getCause());
			}
		}

		if (dropped > 0)
		{
			log.debug("Deferred upload cancelled with {} zones pending", dropped);
		}
	}

	/**
	 * Like {@link #cancel()} but without waiting for the zone being filled. For callers that will not touch
	 * the pending zones afterwards (the next swap drops them unfreed, since they hold no GL objects); the
	 * next drain still runs after this one on the single worker thread, so the worker's arena is not
	 * reused under it.
	 */
	void abandon()
	{
		int dropped;
		synchronized (this)
		{
			dropped = scheduler.size();
			scheduler.cancel();
			completed.clear();
			drain = null;
		}

		if (dropped > 0)
		{
			log.debug("Deferred upload abandoned with {} zones pending", dropped);
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
		try
		{
			drainZones(scene, gen);
		}
		catch (Throwable ex) // an abandoned drain has no cancel() to report through, so log here
		{
			log.error("Deferred upload worker died", ex);
			throw ex;
		}
	}

	private void drainZones(Scene scene, int gen)
	{
		// every slice of the previous drain has been committed or dropped by now: the previous drain has
		// returned (same thread) and its zones were either committed or left the live table at the swap
		if (arena != null)
		{
			arena.clear();
		}

		PendingZone p;
		while ((p = scheduler.pollNearest(gen, focusX, focusZ)) != null)
		{
			boolean ok;
			try
			{
				uploader.zoneSize(scene, p.zone, p.zx, p.zz);
				stage(p.zone);
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

			completed.add(new Completion(p, gen, ok));
		}
	}

	/**
	 * Commit every zone the worker has finished since the last call. Client thread; called at the start
	 * of each frame so finished zones are in that frame, and once per client tick as a fallback for when
	 * no frames are being drawn.
	 *
	 * @return the number of zones committed
	 */
	int commitFinished()
	{
		int n = 0;
		Completion c;
		while ((c = completed.poll()) != null)
		{
			finish(c.zone, c.gen, c.uploaded);
			++n;
		}
		return n;
	}

	// worker thread
	private void stage(Zone zone)
	{
		int ints = zone.stagingInts();
		if (arena == null || arena.remaining() < ints)
		{
			int capacity = arena == null ? MIN_ARENA_INTS : arena.capacity() + arena.capacity() / 4;
			capacity = Math.max(capacity, ints);
			log.debug("Deferred upload arena {}kb -> {}kb", arena == null ? 0 : arena.capacity() * Integer.BYTES / 1024, capacity * Integer.BYTES / 1024);
			arena = GpuIntBuffer.allocateDirect(capacity);
		}
		zone.stage(arena);
	}

	// client thread
	private void finish(PendingZone p, int gen, boolean uploaded)
	{
		Zone zone = p.zone;
		// the generation check and the commit are one step under the lock the generation is bumped under,
		// so a bump from another thread cannot land between them
		synchronized (this)
		{
			if (!scheduler.isCurrent(gen) || !zone.pending)
			{
				// cancelled after the fill; the zone is freed with the scene it belongs to
				return;
			}

			committer.accept(zone);
		}
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
