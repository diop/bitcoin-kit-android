package io.horizontalsystems.bitcoinkit.network

import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class PeerManagerTest {
    private val peer1 = mock(Peer::class.java)
    private val peer2 = mock(Peer::class.java)
    private val peer3 = mock(Peer::class.java)

    private lateinit var peerManager: PeerManager

    @Before
    fun setup() {
        whenever(peer1.host).thenReturn("8.8.8.8")
        whenever(peer2.host).thenReturn("9.9.9.9")
        whenever(peer3.host).thenReturn("0.0.0.0")

        peerManager = PeerManager()
    }

    @Test
    fun add() {
        peerManager.add(peer1)
        assertEquals(1, peerManager.peersCount())
    }

    @Test
    fun remove() {
        peerManager.add(peer1)
        assertEquals(1, peerManager.peersCount())

        peerManager.add(peer2)
        assertEquals(2, peerManager.peersCount())

        peerManager.remove(peer1)
        assertEquals(1, peerManager.peersCount())
    }

    @Test
    fun disconnectAll() {
        peerManager.add(peer1)
        peerManager.add(peer2)
        assertEquals(2, peerManager.peersCount())

        peerManager.disconnectAll()
        assertEquals(0, peerManager.peersCount())
    }

    @Test
    fun someReadyPeers() {
        whenever(peer3.ready).thenReturn(true)

        peerManager.add(peer1)
        peerManager.add(peer2)
        peerManager.add(peer3)
        assertEquals(3, peerManager.peersCount())

        val somePeers = peerManager.someReadyPeers()
        assertEquals(1, somePeers.size)
        assertEquals(peer3.host, somePeers[0].host)
    }

    @Test
    fun connected() {
        peerManager.add(peer1)
        peerManager.add(peer2)
        peerManager.add(peer3)
        assertEquals(listOf<Peer>(), peerManager.connected())

        whenever(peer2.connected).thenReturn(true)
        whenever(peer3.connected).thenReturn(true)
        assertEquals(listOf(peer2, peer3), peerManager.connected())
    }

    @Test
    fun nonSyncedPeer() {
        peerManager.add(peer1)
        peerManager.add(peer2)
        peerManager.add(peer3)

        assertEquals(null, peerManager.nonSyncedPeer())

        whenever(peer1.synced).thenReturn(false)
        whenever(peer1.connected).thenReturn(true)
        assertEquals(peer1, peerManager.nonSyncedPeer())
    }

    @Test
    fun isHalfSynced_moreThanHalf() {
        addPeer(host = "0.0.0.1", connected = true, synced = true)
        addPeer(host = "0.0.0.2", connected = true, synced = true)
        addPeer(host = "0.0.0.3", connected = false, synced = false)
        addPeer(host = "0.0.0.4", connected = false, synced = false)

        assertEquals(true, peerManager.isHalfSynced())
    }

    @Test
    fun isHalfSynced_lessThanHalf() {
        addPeer(host = "0.0.0.1", connected = true, synced = true)
        addPeer(host = "0.0.0.2", connected = true, synced = false)
        addPeer(host = "0.0.0.3", connected = false, synced = false)
        addPeer(host = "0.0.0.4", connected = false, synced = true)

        assertEquals(false, peerManager.isHalfSynced())
    }

    private fun addPeer(connected: Boolean, synced: Boolean, host: String = "0.0.0.0"): Peer {
        val peer = mock(Peer::class.java)

        whenever(peer.connected).thenReturn(connected)
        whenever(peer.synced).thenReturn(synced)
        whenever(peer.host).thenReturn(host)

        peerManager.add(peer)

        return peer
    }
}
