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

import java.nio.IntBuffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

/**
 * The staging side of Zone, which has no GL in it, and the two commit() paths that make no GL call:
 * an empty zone and a discarded one. A commit with data creates GL objects and is covered in game.
 */
@RunWith(Enclosed.class)
public class ZoneTest
{
	private static final int INTS_PER_FACE = 3 * (Zone.VERT_SIZE / 4);

	private static Zone zoneOf(int sizeO, int sizeA)
	{
		Zone z = new Zone();
		z.sizeO = sizeO;
		z.sizeA = sizeA;
		return z;
	}

	public static class StagingInts
	{
		@Test
		public void countsThreeVerticesPerFaceForBothBuffers()
		{
			assertEquals(0, zoneOf(0, 0).stagingInts());
			assertEquals(INTS_PER_FACE, zoneOf(1, 0).stagingInts());
			assertEquals(7 * INTS_PER_FACE, zoneOf(4, 3).stagingInts());
		}
	}

	public static class Stage
	{
		@Test
		public void allocatesExactlySizedBuffers()
		{
			Zone z = zoneOf(4, 3);
			z.stage();

			assertEquals(4 * INTS_PER_FACE, z.stageO.capacity());
			assertEquals(3 * INTS_PER_FACE, z.stageA.capacity());
			assertEquals(0, z.stageO.position());
		}

		@Test
		public void leavesAnEmptySideNull()
		{
			Zone z = zoneOf(2, 0);
			z.stage();

			assertNotNull(z.stageO);
			assertNull(z.stageA);

			Zone empty = zoneOf(0, 0);
			empty.stage();
			assertNull(empty.stageO);
			assertNull(empty.stageA);
		}

		@Test
		public void slicesConsecutiveZonesFromAnArena()
		{
			IntBuffer arena = GpuIntBuffer.allocateDirect(100 * INTS_PER_FACE);
			Zone a = zoneOf(4, 3);
			Zone b = zoneOf(1, 0);
			a.stage(arena);
			b.stage(arena);

			assertEquals(4 * INTS_PER_FACE, a.stageO.capacity());
			assertEquals(3 * INTS_PER_FACE, a.stageA.capacity());
			assertEquals(INTS_PER_FACE, b.stageO.capacity());
			assertNull(b.stageA);
			assertEquals(8 * INTS_PER_FACE, arena.position());
			assertEquals(arena.capacity(), arena.limit());
		}

		@Test
		public void slicesDoNotOverlap()
		{
			IntBuffer arena = GpuIntBuffer.allocateDirect(10 * INTS_PER_FACE);
			Zone a = zoneOf(1, 1);
			Zone b = zoneOf(1, 1);
			a.stage(arena);
			b.stage(arena);

			a.stageO.put(0, 0xA0);
			a.stageA.put(0, 0xA1);
			b.stageO.put(0, 0xB0);
			b.stageA.put(0, 0xB1);

			assertEquals(0xA0, arena.get(0));
			assertEquals(0xA1, arena.get(INTS_PER_FACE));
			assertEquals(0xB0, arena.get(2 * INTS_PER_FACE));
			assertEquals(0xB1, arena.get(3 * INTS_PER_FACE));
		}
	}

	public static class DiscardUpload
	{
		@Test
		public void emptiesAPartlyWrittenZone()
		{
			Zone z = zoneOf(2, 1);
			z.stage();
			z.stageO.put(1).put(2).put(3);
			z.stageA.put(4);
			z.rids = new int[][]{{7}, {}, {}, {}};
			z.roofStart = new int[][]{{0}, {}, {}, {}};
			z.roofEnd = new int[][]{{3}, {}, {}, {}};
			z.levelOffsets[0] = 3;
			z.alphaModels.add(new Zone.AlphaModel());

			z.discardUpload();

			assertEquals(0, z.stageO.position());
			assertEquals(0, z.stageA.position());
			assertEquals(0, z.rids[0].length);
			assertEquals(0, z.roofStart[0].length);
			assertEquals(0, z.roofEnd[0].length);
			assertEquals(0, z.levelOffsets[0]);
			assertTrue(z.alphaModels.isEmpty());
		}

		@Test
		public void toleratesAnUnstagedZone()
		{
			Zone z = zoneOf(0, 0);
			z.discardUpload();
			assertNull(z.stageO);
			assertNull(z.stageA);
		}
	}

	public static class Commit
	{
		@Test
		public void anEmptyZoneCommitsToNothing()
		{
			Zone z = zoneOf(0, 0);
			z.stage();

			z.commit();

			assertEquals(0, z.glVao);
			assertEquals(0, z.glVaoA);
			assertNull(z.vboO);
			assertNull(z.vboA);
			assertEquals(0, z.bufLen);
			assertEquals(0, z.bufLenA);
		}

		@Test
		public void aDiscardedZoneCommitsToNothing()
		{
			// the guarantee a failed deferred fill relies on: it is drawn as nothing until rebuilt
			Zone z = zoneOf(2, 1);
			z.stage();
			z.stageO.put(1).put(2).put(3);
			z.stageA.put(4);
			z.discardUpload();

			z.commit();

			assertEquals(0, z.glVao);
			assertEquals(0, z.glVaoA);
			assertNull(z.vboO);
			assertNull(z.vboA);
			assertEquals(0, z.bufLen);
			assertNull(z.stageO);
			assertNull(z.stageA);
		}
	}

	public static class DropStaging
	{
		@Test
		public void forgetsBothBuffers()
		{
			Zone z = zoneOf(1, 1);
			z.stage();
			z.dropStaging();
			assertNull(z.stageO);
			assertNull(z.stageA);
		}
	}

	public static class FreeInto
	{
		@Test
		public void handsEveryGlObjectToTheBatchAndForgetsIt()
		{
			Zone z = zoneOf(1, 1);
			z.stage();
			z.vboO = new VBO(4);
			z.vboO.bufId = 11;
			z.vboA = new VBO(4);
			z.vboA.bufId = 12;
			z.glVao = 21;
			z.glVaoA = 22;
			z.alphaModels.add(new Zone.AlphaModel());
			IntBuffer buffers = IntBuffer.allocate(4);
			IntBuffer vertexArrays = IntBuffer.allocate(4);

			z.freeInto(buffers, vertexArrays);

			buffers.flip();
			vertexArrays.flip();
			assertEquals(2, buffers.remaining());
			assertEquals(11, buffers.get());
			assertEquals(12, buffers.get());
			assertEquals(2, vertexArrays.remaining());
			assertEquals(21, vertexArrays.get());
			assertEquals(22, vertexArrays.get());
			assertNull(z.vboO);
			assertNull(z.vboA);
			assertEquals(0, z.glVao);
			assertEquals(0, z.glVaoA);
			assertNull(z.stageO);
			assertTrue(z.alphaModels.isEmpty());
		}

		@Test
		public void addsNothingForAZoneWithoutGlObjects()
		{
			Zone z = zoneOf(0, 0);
			IntBuffer buffers = IntBuffer.allocate(4);
			IntBuffer vertexArrays = IntBuffer.allocate(4);

			z.freeInto(buffers, vertexArrays);

			assertEquals(0, buffers.position());
			assertEquals(0, vertexArrays.position());
		}
	}
}
