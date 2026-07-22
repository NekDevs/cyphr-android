package org.cyphr.app.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
class CounterPersistenceTest {

    private lateinit var context: Context
    private lateinit var store: ReplayProtectionStore
    private val profileUuid = "test-profile-uuid"
    private val contactUuid = "test-contact-uuid"

    @Before
    fun setUp() {
        CryptoFeatureFlag.enable()
        context = ApplicationProvider.getApplicationContext()
        store = ReplayProtectionStore(context)
    }

    @After
    fun tearDown() {
        CryptoFeatureFlag.disable()
        context.filesDir.deleteRecursively()
    }

    @Test
    fun countersAbsentByDefault() {
        val counters = store.loadCounters(profileUuid, contactUuid)
        assertEquals(0, counters.sendCounter)
        assertEquals(0, counters.receiveCounter)
    }

    @Test
    fun saveAndLoadCountersRoundTrip() {
        store.saveCounters(profileUuid, contactUuid, SendReceiveCounters(5, 3))
        val counters = store.loadCounters(profileUuid, contactUuid)
        assertEquals(5, counters.sendCounter)
        assertEquals(3, counters.receiveCounter)
    }

    @Test
    fun saveIncrementsSendCounter() {
        for (i in 1..5) {
            val current = store.loadCounters(profileUuid, contactUuid)
            store.saveCounters(profileUuid, contactUuid, current.copy(sendCounter = current.sendCounter + 1))
        }
        val counters = store.loadCounters(profileUuid, contactUuid)
        assertEquals(5, counters.sendCounter)
    }

    @Test
    fun receiveCounterRejectsReplay() {
        fun receive(counter: Int): Boolean {
            val current = store.loadCounters(profileUuid, contactUuid)
            if (counter <= current.receiveCounter) return false
            store.saveCounters(profileUuid, contactUuid, current.copy(receiveCounter = counter))
            return true
        }

        assertTrue(receive(1))
        assertFalse(receive(1))
        assertTrue(receive(5))
        assertFalse(receive(3))
        assertFalse(receive(5))
        assertTrue(receive(6))
    }

    @Test
    fun acceptReceivedCounterRejectsReplayAndUpdatesStoredValue() {
        assertTrue(store.acceptReceivedCounter(profileUuid, contactUuid, 1))
        assertFalse(store.acceptReceivedCounter(profileUuid, contactUuid, 1))
        assertTrue(store.acceptReceivedCounter(profileUuid, contactUuid, 5))
        assertFalse(store.acceptReceivedCounter(profileUuid, contactUuid, 3))
        val counters = store.loadCounters(profileUuid, contactUuid)
        assertEquals(5, counters.receiveCounter)
    }

    @Test
    fun acceptReceivedCounterRejectsNegativeCounter() {
        assertFalse(store.acceptReceivedCounter(profileUuid, contactUuid, -1))
    }

    @Test
    fun countersAreIndependentPerContact() {
        val otherContactUuid = "other-contact-uuid"
        store.saveCounters(profileUuid, contactUuid, SendReceiveCounters(7, 2))
        store.saveCounters(profileUuid, otherContactUuid, SendReceiveCounters(3, 9))

        val first = store.loadCounters(profileUuid, contactUuid)
        assertEquals(7, first.sendCounter)
        assertEquals(2, first.receiveCounter)

        val second = store.loadCounters(profileUuid, otherContactUuid)
        assertEquals(3, second.sendCounter)
        assertEquals(9, second.receiveCounter)
    }
}
