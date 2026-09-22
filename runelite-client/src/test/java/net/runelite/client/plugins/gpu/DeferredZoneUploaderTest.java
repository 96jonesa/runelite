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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Scene;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Drives the worker on a same-thread executor with a mocked SceneUploader and a recording committer,
 * so a drain runs to completion inside start() and every step is observable without GL.
 */
@RunWith(Enclosed.class)
public class DeferredZoneUploaderTest
{
	private static final int SIZE = 5;

	static class Harness
	{
		final SceneUploader uploader = mock(SceneUploader.class);
		final Scene scene = mock(Scene.class);
		final List<Zone> committed = new ArrayList<>();
		final ExecutorService executor = MoreExecutors.newDirectExecutorService();
		final DeferredZoneUploader deferred = new DeferredZoneUploader(uploader, executor, committed::add);
		final Zone[][] zones = new Zone[SIZE][SIZE];

		Harness()
		{
			for (int x = 0; x < SIZE; ++x)
			{
				for (int z = 0; z < SIZE; ++z)
				{
					zones[x][z] = new Zone();
				}
			}
		}

		Harness pending(int x, int z)
		{
			zones[x][z].pending = true;
			return this;
		}

		int pendingCount()
		{
			int n = 0;
			for (Zone[] column : zones)
			{
				for (Zone zone : column)
				{
					if (zone.pending)
					{
						++n;
					}
				}
			}
			return n;
		}
	}

	public static class Start
	{
		@Test
		public void fillsEveryPendingZoneAndNothingElse()
		{
			Harness h = new Harness().pending(0, 0).pending(2, 2).pending(4, 4);

			h.deferred.start(h.scene, h.zones);

			verify(h.uploader).uploadZone(h.scene, h.zones[0][0], 0, 0);
			verify(h.uploader).uploadZone(h.scene, h.zones[2][2], 2, 2);
			verify(h.uploader).uploadZone(h.scene, h.zones[4][4], 4, 4);
			verify(h.uploader, never()).uploadZone(eq(h.scene), eq(h.zones[1][1]), anyInt(), anyInt());
		}

		@Test
		public void sizesBeforeFilling()
		{
			Harness h = new Harness().pending(1, 3);

			h.deferred.start(h.scene, h.zones);

			InOrder order = inOrder(h.uploader);
			order.verify(h.uploader).zoneSize(h.scene, h.zones[1][3], 1, 3);
			order.verify(h.uploader).uploadZone(h.scene, h.zones[1][3], 1, 3);
		}

		@Test
		public void doesNothingWithoutPendingZones()
		{
			Harness h = new Harness();

			h.deferred.start(h.scene, h.zones);

			verify(h.uploader, never()).uploadZone(any(), any(), anyInt(), anyInt());
			assertEquals(0, h.deferred.commitFinished());
		}

		@Test
		public void fillsNearestTheCentreFirst()
		{
			Harness h = new Harness().pending(0, 0).pending(2, 2).pending(4, 4).pending(2, 3);

			h.deferred.start(h.scene, h.zones);
			h.deferred.commitFinished();

			assertSame(h.zones[2][2], h.committed.get(0));
			assertSame(h.zones[2][3], h.committed.get(1));
		}
	}

	public static class CommitFinished
	{
		@Test
		public void commitsFilledZonesOnTheCallingThreadAndMarksThemReady()
		{
			Harness h = new Harness().pending(1, 1).pending(3, 3);
			h.deferred.start(h.scene, h.zones);
			assertTrue("nothing is committed until the client thread asks", h.committed.isEmpty());
			assertEquals(2, h.pendingCount());

			int n = h.deferred.commitFinished();

			assertEquals(2, n);
			assertEquals(2, h.committed.size());
			assertTrue(h.zones[1][1].initialized);
			assertFalse(h.zones[1][1].pending);
			assertFalse(h.zones[1][1].invalidate);
			assertEquals(0, h.deferred.commitFinished());
		}

		@Test
		public void aFailedFillIsCommittedEmptyAndInvalidated()
		{
			Harness h = new Harness().pending(1, 1).pending(3, 3);
			doThrow(new IllegalStateException("buffer overflow")).when(h.uploader).uploadZone(h.scene, h.zones[1][1], 1, 1);

			h.deferred.start(h.scene, h.zones);
			h.deferred.commitFinished();

			assertTrue(h.zones[1][1].initialized);
			assertFalse(h.zones[1][1].pending);
			assertTrue("rebuilt from the live scene by the client thread", h.zones[1][1].invalidate);
			assertTrue(h.zones[3][3].initialized);
			assertFalse("the failure did not stop the drain", h.zones[3][3].invalidate);
		}
	}

	public static class Abandon
	{
		@Test
		public void dropsFinishedButUncommittedZones()
		{
			Harness h = new Harness().pending(1, 1);
			h.deferred.start(h.scene, h.zones);

			h.deferred.abandon();

			assertEquals(0, h.deferred.commitFinished());
			assertTrue("left pending, to be dropped with its table", h.zones[1][1].pending);
			assertFalse(h.zones[1][1].initialized);
		}
	}

	public static class Cancel
	{
		@Test
		public void dropsFinishedButUncommittedZones()
		{
			Harness h = new Harness().pending(2, 0);
			h.deferred.start(h.scene, h.zones);

			h.deferred.cancel();

			assertEquals(0, h.deferred.commitFinished());
			assertTrue(h.zones[2][0].pending);
		}

		/**
		 * The difference between cancel() and abandon(): with a real worker thread stuck inside a fill,
		 * abandon() returns at once and cancel() only after the fill has finished.
		 */
		@Test(timeout = 10_000)
		public void waitsForTheFillInFlightWhereAbandonDoesNot() throws Exception
		{
			SceneUploader uploader = mock(SceneUploader.class);
			Scene scene = mock(Scene.class);
			ExecutorService worker = Executors.newSingleThreadExecutor();
			DeferredZoneUploader deferred = new DeferredZoneUploader(uploader, worker, zone ->
			{
			});
			Zone[][] zones = new Zone[1][1];
			zones[0][0] = new Zone();
			zones[0][0].pending = true;
			CountDownLatch fillStarted = new CountDownLatch(1);
			CountDownLatch releaseFill = new CountDownLatch(1);
			doAnswer(invocation ->
			{
				fillStarted.countDown();
				releaseFill.await();
				return null;
			}).when(uploader).uploadZone(any(), any(), anyInt(), anyInt());
			try
			{
				deferred.start(scene, zones);
				assertTrue(fillStarted.await(5, TimeUnit.SECONDS));

				deferred.abandon(); // returns while the fill is still blocked

				zones[0][0].pending = true;
				deferred.start(scene, zones); // queued behind the blocked fill on the single worker thread
				CountDownLatch cancelReturned = new CountDownLatch(1);
				Thread canceller = new Thread(() ->
				{
					deferred.cancel();
					cancelReturned.countDown();
				});
				canceller.start();
				assertFalse("cancel must not return while the worker is inside a fill", cancelReturned.await(300, TimeUnit.MILLISECONDS));

				releaseFill.countDown();
				assertTrue(cancelReturned.await(5, TimeUnit.SECONDS));
				canceller.join();
			}
			finally
			{
				releaseFill.countDown();
				worker.shutdownNow();
			}
		}
	}

	/**
	 * One scene load's zones must never be committed under the next one's generation.
	 */
	public static class Generations
	{
		@Test
		public void aNewStartAfterAbandonCommitsOnlyItsOwnZones()
		{
			Harness h = new Harness().pending(0, 0);
			h.deferred.start(h.scene, h.zones);
			h.deferred.abandon();

			Zone[][] next = new Zone[SIZE][SIZE];
			for (int x = 0; x < SIZE; ++x)
			{
				for (int z = 0; z < SIZE; ++z)
				{
					next[x][z] = new Zone();
				}
			}
			next[4][4].pending = true;
			h.deferred.start(h.scene, next);
			h.deferred.commitFinished();

			assertEquals(1, h.committed.size());
			assertSame(next[4][4], h.committed.get(0));
			assertTrue(h.zones[0][0].pending);
		}
	}
}
