package io.horizontalsystems.bitcoinkit.dash

import android.util.Log
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.core.hexStringToByteArray
import io.horizontalsystems.bitcoinkit.core.toHexString
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.messages.DashMessageParser
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockRequestsTask
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockVotesTask
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.peer.IInventoryItemsHandler
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

class DashKit : BitcoinKit.Listener {

    private var masterNodeSyncer: MasternodeListSyncer? = null

    fun extendBitcoin(bitcoinKit: BitcoinKit) {
        bitcoinKit.addListener(this)

        bitcoinKit.addMessageParser(DashMessageParser())

        val masterNodeSyncer = MasternodeListSyncer(bitcoinKit.peerGroup, PeerTaskFactory(), MasternodeListManager())
        bitcoinKit.addPeerTaskHandler(masterNodeSyncer)

        this.masterNodeSyncer = masterNodeSyncer

        val instantSend = InstantSend(bitcoinKit.transactionSyncer)
        bitcoinKit.addInventoryItemsHandler(instantSend)
        bitcoinKit.addPeerTaskHandler(instantSend)
    }

    override fun onLastBlockInfoUpdate(bitcoinKit: BitcoinKit, blockInfo: BlockInfo) {
        if (bitcoinKit.syncState == BitcoinKit.KitState.Synced) {
            masterNodeSyncer?.sync(blockInfo.headerHash.hexStringToByteArray().reversedArray())
        }
    }

    override fun onKitStateUpdate(bitcoinKit: BitcoinKit, state: BitcoinKit.KitState) {
        if (state == BitcoinKit.KitState.Synced) {
            bitcoinKit.lastBlockInfo?.let {
                masterNodeSyncer?.sync(it.headerHash.hexStringToByteArray().reversedArray())
            }
        }
    }
}

class InstantSend(private val transactionSyncer: TransactionSyncer?) : IInventoryItemsHandler, IPeerTaskHandler {

    override fun handleInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>) {
        val transactionLockRequests = mutableListOf<ByteArray>()
        val transactionLockVotes = mutableListOf<ByteArray>()

        inventoryItems.forEach { item ->
            when (item.type) {
                InventoryType.MSG_TXLOCK_REQUEST -> {
                    transactionLockRequests.add(item.hash)
                }
                InventoryType.MSG_TXLOCK_VOTE -> {
                    transactionLockVotes.add(item.hash)
                }
            }
        }

        if (transactionLockRequests.isNotEmpty()) {
            peer.addTask(RequestTransactionLockRequestsTask(transactionLockRequests))
        }

        if (transactionLockVotes.isNotEmpty()) {
            peer.addTask(RequestTransactionLockVotesTask(transactionLockVotes))
        }

    }

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        return when (task) {
            is RequestTransactionLockRequestsTask -> {
                transactionSyncer?.handleTransactions(task.transactions)
                true
            }
            is RequestTransactionLockVotesTask -> {
                task.transactionLockVotes.forEach {
                    Log.e("AAA", "Received tx lock vote for tx: ${it.txHash.reversedArray().toHexString()}")
                }
                true
            }
            else -> false
        }
    }

}

object InventoryType {
    const val MSG_TXLOCK_REQUEST = 4

    const val MSG_TXLOCK_VOTE = 5
}
