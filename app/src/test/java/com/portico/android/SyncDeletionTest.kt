package com.portico.android

import com.portico.android.data.staleIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a sync is allowed to delete from the server.
 *
 * This rule destroyed a live portfolio. A signed-in workspace whose cloud read
 * came back empty had no baseline, and the diff resolved that by listing the
 * whole remote collection and deleting everything the empty local copy lacked.
 * The next ordinary edit then committed it.
 *
 * The tests are here because a delete is the one operation with nothing to undo
 * it, so the condition guarding it is worth stating twice.
 */
class SyncDeletionTest {

    private fun records(vararg ids: String): Map<String, String> =
        ids.associateWith { "record-$it" }

    @Test
    fun `no baseline means nothing is deleted`() {
        // The shape of the bug: local is empty because the read failed, not
        // because the member removed anything.
        val stale = staleIds(previous = null, current = emptyMap<String, String>())
        assertTrue("an absent baseline must never authorise a delete", stale.isEmpty())
    }

    @Test
    fun `no baseline does not delete even when local holds records`() {
        // A partial load is still not evidence about what is on the server.
        val stale = staleIds(previous = null, current = records("a", "b"))
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `a baseline lets a real deletion through`() {
        val stale = staleIds(previous = records("a", "b", "c"), current = records("a", "c"))
        assertEquals(setOf("b"), stale)
    }

    @Test
    fun `emptying a workspace against a baseline is still permitted`() {
        // Removing the last property is a legitimate act, and the baseline is
        // what makes it distinguishable from a failed read.
        val stale = staleIds(previous = records("a", "b"), current = emptyMap<String, String>())
        assertEquals(setOf("a", "b"), stale)
    }

    @Test
    fun `nothing is deleted when the two sides agree`() {
        val stale = staleIds(previous = records("a", "b"), current = records("a", "b"))
        assertTrue(stale.isEmpty())
    }

    @Test
    fun `records new to the local copy are not deletions`() {
        val stale = staleIds(previous = records("a"), current = records("a", "b"))
        assertTrue(stale.isEmpty())
    }
}
