package io.horizontalsystems.bitcoinkit.dash

import android.util.Log
import io.horizontalsystems.bitcoinkit.core.toHexString
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockRequestsTask
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockVotesTask
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.peer.IInventoryItemsHandler
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

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