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
import net.runelite.api.Constants;
import net.runelite.client.plugins.gpu.DeferredUploadScheduler.PendingZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class DeferredUploadSchedulerTest
{
	private static void addSquare(DeferredUploadScheduler scheduler, int size)
	{
		for (int x = 0; x < size; ++x)
		{
			for (int z = 0; z < size; ++z)
			{
				scheduler.add(x, z, new Zone());
			}
		}
	}

	private static List<PendingZone> drain(DeferredUploadScheduler scheduler, int gen, int fx, int fz)
	{
		List<PendingZone> out = new ArrayList<>();
		PendingZone p;
		while ((p = scheduler.pollNearest(gen, fx, fz)) != null)
		{
			out.add(p);
		}
		return out;
	}

	private static int distSq(PendingZone p, int fx, int fz)
	{
		return (p.zx - fx) * (p.zx - fx) + (p.zz - fz) * (p.zz - fz);
	}

	public static class IsNear
	{
		@Test
		public void centreIsNearAtRadiusZero()
		{
			assertTrue(DeferredUploadScheduler.isNear(11, 11, 11, 11, 0));
			assertFalse(DeferredUploadScheduler.isNear(12, 11, 11, 11, 0));
			assertFalse(DeferredUploadScheduler.isNear(11, 10, 11, 11, 0));
		}

		@Test
		public void nearSetIsASquareBlock()
		{
			// the corner of the block is as near as its edge midpoint
			assertTrue(DeferredUploadScheduler.isNear(14, 14, 11, 11, 3));
			assertTrue(DeferredUploadScheduler.isNear(8, 14, 11, 11, 3));
			assertTrue(DeferredUploadScheduler.isNear(11, 8, 11, 11, 3));
			assertFalse(DeferredUploadScheduler.isNear(15, 11, 11, 11, 3));
			assertFalse(DeferredUploadScheduler.isNear(11, 7, 11, 11, 3));
		}

		@Test
		public void radiusFromTheCentreToTheEdgeCoversTheWholeScene()
		{
			final int zones = Constants.EXTENDED_SCENE_SIZE >> 3;
			final int centre = zones >> 1;
			for (int x = 0; x < zones; ++x)
			{
				for (int z = 0; z < zones; ++z)
				{
					assertTrue(DeferredUploadScheduler.isNear(x, z, centre, centre, centre));
				}
			}
		}
	}

	public static class PollNearest
	{
		@Test
		public void returnsNullWhenEmpty()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			assertNull(scheduler.pollNearest(scheduler.generation(), 0, 0));
		}

		@Test
		public void returnsZonesNearestFirst()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			addSquare(scheduler, 23);

			List<PendingZone> order = drain(scheduler, scheduler.generation(), 11, 11);

			assertEquals(23 * 23, order.size());
			assertEquals(11, order.get(0).zx);
			assertEquals(11, order.get(0).zz);
			for (int i = 1; i < order.size(); ++i)
			{
				assertTrue("zone " + i + " is farther than its predecessor",
					distSq(order.get(i - 1), 11, 11) <= distSq(order.get(i), 11, 11));
			}
		}

		@Test
		public void removesTheReturnedZone()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			Zone zone = new Zone();
			scheduler.add(3, 4, zone);

			PendingZone p = scheduler.pollNearest(scheduler.generation(), 0, 0);

			assertNotNull(p);
			assertSame(zone, p.zone);
			assertEquals(3, p.zx);
			assertEquals(4, p.zz);
			assertEquals(0, scheduler.size());
			assertNull(scheduler.pollNearest(scheduler.generation(), 0, 0));
		}

		@Test
		public void followsAMovingFocus()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			scheduler.add(0, 0, new Zone());
			scheduler.add(22, 22, new Zone());
			scheduler.add(11, 11, new Zone());

			PendingZone first = scheduler.pollNearest(scheduler.generation(), 1, 1);
			PendingZone second = scheduler.pollNearest(scheduler.generation(), 21, 21);
			PendingZone third = scheduler.pollNearest(scheduler.generation(), 21, 21);

			assertEquals(0, first.zx);
			assertEquals(22, second.zx);
			assertEquals(11, third.zx);
		}

		@Test
		public void breaksTiesInQueueOrder()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			Zone east = new Zone();
			Zone west = new Zone();
			scheduler.add(12, 11, east);
			scheduler.add(10, 11, west);

			assertSame(east, scheduler.pollNearest(scheduler.generation(), 11, 11).zone);
			assertSame(west, scheduler.pollNearest(scheduler.generation(), 11, 11).zone);
		}

		@Test
		public void rejectsAStaleGeneration()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			int stale = scheduler.generation();
			scheduler.cancel();
			scheduler.add(1, 1, new Zone());

			assertNull(scheduler.pollNearest(stale, 1, 1));
			assertEquals(1, scheduler.size());
			assertNotNull(scheduler.pollNearest(scheduler.generation(), 1, 1));
		}
	}

	public static class Cancel
	{
		@Test
		public void emptiesTheQueue()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			addSquare(scheduler, 5);

			scheduler.cancel();

			assertEquals(0, scheduler.size());
			assertNull(scheduler.pollNearest(scheduler.generation(), 2, 2));
		}

		@Test
		public void startsANewGeneration()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			int before = scheduler.generation();

			int returned = scheduler.cancel();

			assertEquals(before + 1, returned);
			assertEquals(returned, scheduler.generation());
			assertFalse(scheduler.isCurrent(before));
			assertTrue(scheduler.isCurrent(returned));
		}
	}

	/**
	 * A scene load queues zones under one generation; the next load cancels them. Completions posted by
	 * the worker for the old load must be recognisable as stale so the client thread ignores them.
	 */
	public static class Generations
	{
		@Test
		public void completionFromAPreviousLoadIsStale()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			int firstLoad = scheduler.generation();
			scheduler.add(5, 5, new Zone());
			PendingZone inFlight = scheduler.pollNearest(firstLoad, 5, 5);
			assertNotNull(inFlight);

			// the next scene load arrives while the worker is still filling inFlight
			int secondLoad = scheduler.cancel();
			scheduler.add(6, 6, new Zone());

			assertFalse("completion for the first load must be dropped", scheduler.isCurrent(firstLoad));
			assertTrue(scheduler.isCurrent(secondLoad));
			assertNull("the old worker loop must stop", scheduler.pollNearest(firstLoad, 5, 5));
			PendingZone next = scheduler.pollNearest(secondLoad, 6, 6);
			assertNotNull(next);
			assertEquals(6, next.zx);
		}

		@Test
		public void aFreshLoadAfterCancelDrainsCompletely()
		{
			DeferredUploadScheduler scheduler = new DeferredUploadScheduler();
			addSquare(scheduler, 4);
			scheduler.cancel();
			addSquare(scheduler, 3);

			assertEquals(9, drain(scheduler, scheduler.generation(), 1, 1).size());
			assertEquals(0, scheduler.size());
		}
	}
}
