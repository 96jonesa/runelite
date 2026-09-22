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

import java.util.ArrayList;
import java.util.List;

/**
 * The zones of a scene whose upload was deferred past the scene swap, handed out nearest-first
 * relative to a focus zone so the geometry closest to the camera fills in first.
 * <p>
 * A generation counter ties every queued zone to the scene load that queued it: {@link #cancel()}
 * empties the queue and starts a new generation, so a poll or a completion that still carries an
 * older generation is rejected instead of touching zones that belong to a dropped scene.
 * <p>
 * All methods are thread safe; the scheduler is shared by the map loader thread, the client thread
 * and the deferred upload worker.
 */
class DeferredUploadScheduler
{
	static final class PendingZone
	{
		final int zx;
		final int zz;
		final Zone zone;

		PendingZone(int zx, int zz, Zone zone)
		{
			this.zx = zx;
			this.zz = zz;
			this.zone = zone;
		}
	}

	private final List<PendingZone> pending = new ArrayList<>();
	private int generation;

	/**
	 * Whether zone (zx, zz) lies within {@code radius} zones of (cx, cz), measured as the larger of the
	 * two axis distances so that the near set is a square block of zones around the centre.
	 */
	static boolean isNear(int zx, int zz, int cx, int cz, int radius)
	{
		return Math.max(Math.abs(zx - cx), Math.abs(zz - cz)) <= radius;
	}

	synchronized int generation()
	{
		return generation;
	}

	synchronized boolean isCurrent(int gen)
	{
		return gen == generation;
	}

	synchronized void add(int zx, int zz, Zone zone)
	{
		pending.add(new PendingZone(zx, zz, zone));
	}

	synchronized int size()
	{
		return pending.size();
	}

	/**
	 * Remove and return the queued zone closest to the focus zone (fx, fz), or null when the queue is
	 * empty or {@code gen} is no longer the current generation. Ties go to the zone queued first.
	 */
	synchronized PendingZone pollNearest(int gen, int fx, int fz)
	{
		if (gen != generation || pending.isEmpty())
		{
			return null;
		}

		int best = 0;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < pending.size(); ++i)
		{
			PendingZone p = pending.get(i);
			int dx = p.zx - fx;
			int dz = p.zz - fz;
			int dist = dx * dx + dz * dz;
			if (dist < bestDist)
			{
				bestDist = dist;
				best = i;
			}
		}

		return pending.remove(best);
	}

	/**
	 * Drop every queued zone and start a new generation.
	 *
	 * @return the new generation
	 */
	synchronized int cancel()
	{
		pending.clear();
		return ++generation;
	}
}
