package io.horizontalsystems.bitcoinkit.dash.managers

import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestMasternodeListDiffTask
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerGroup
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask

class MasternodeListSyncer(private val peerGroup: PeerGroup, val peerTaskFactory: PeerTaskFactory, private val masternodeListManager: MasternodeListManager) : IPeerTaskHandler {

    override var nextHandler: IPeerTaskHandler? = null

    fun sync(blockHash: ByteArray) {
        addTask(masternodeListManager.getBaseBlockHash(), blockHash)
    }

    override fun handleCompletedTask(peer: Peer, task: PeerTask) {
        when (task) {
            is RequestMasternodeListDiffTask -> {
                task.masternodeListDiffMessage?.let { masternodeListDiffMessage ->
                    try {
                        masternodeListManager.updateList(masternodeListDiffMessage)
                    } catch (e: InvalidMasternodeListException) {
                        peer.close(e)

                        addTask(masternodeListDiffMessage.baseBlockHash, masternodeListDiffMessage.blockHash)
                    }
                }
            }
            else -> nextHandler?.handleCompletedTask(peer, task)
        }
    }

    private fun addTask(baseBlockHash: ByteArray, blockHash: ByteArray) {
        val task = peerTaskFactory.createRequestMasternodeListDiffTask(baseBlockHash, blockHash)
        peerGroup.addTask(task)
    }
}
