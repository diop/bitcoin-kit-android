package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask

interface IPeerTaskHandler {
    var nextHandler: IPeerTaskHandler?

    fun handleCompletedTask(peer: Peer, task: PeerTask)
}
