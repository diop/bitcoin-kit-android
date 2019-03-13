package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask

interface IPeerTaskHandler {
    fun handleCompletedTask(peer: Peer, task: PeerTask)
}
