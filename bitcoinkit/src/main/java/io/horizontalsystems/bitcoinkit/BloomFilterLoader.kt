package io.horizontalsystems.bitcoinkit

import io.horizontalsystems.bitcoinkit.crypto.BloomFilter
import io.horizontalsystems.bitcoinkit.managers.BloomFilterManager
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerGroup.IPeerGroupListener

class BloomFilterLoader(private val bloomFilterManager: BloomFilterManager) : IPeerGroupListener, BloomFilterManager.Listener {
    private val peers = mutableListOf<Peer>()

    override fun onPeerConnect(peer: Peer) {
        bloomFilterManager.bloomFilter?.let {
            peer.filterLoad(it)
        }
    }

    override fun onPeerDisconnect(peer: Peer, e: Exception?) {
        peers.remove(peer)
    }

    override fun onFilterUpdated(bloomFilter: BloomFilter) {
        peers.forEach {
            it.filterLoad(bloomFilter)
        }
    }
}
