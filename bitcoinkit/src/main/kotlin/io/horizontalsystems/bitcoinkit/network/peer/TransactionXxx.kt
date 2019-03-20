package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.network.peer.task.RequestTransactionsTask
import io.horizontalsystems.bitcoinkit.network.peer.task.SendTransactionTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

class TransactionXxx(private val peerGroup: PeerGroup, var transactionSyncer: TransactionSyncer) : IPeerTaskHandler, IInventoryItemsHandler {

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        return when (task) {
            is RequestTransactionsTask -> {
                transactionSyncer.handleTransactions(task.transactions)
                true
            }
            is SendTransactionTask -> {
                transactionSyncer.handleTransaction(task.transaction)
                true
            }
            else -> false
        }

    }

    override fun handleInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>) {
        val transactionHashes = mutableListOf<ByteArray>()

        inventoryItems.forEach { item ->
            when (item.type) {
                InventoryItem.MSG_TX -> {
                    if (!peerGroup.isRequestingInventory(item.hash) && transactionSyncer.shouldRequestTransaction(item.hash)) {
                        transactionHashes.add(item.hash)
                    }
                }
            }
        }

        if (transactionHashes.isNotEmpty()) {
            peer.addTask(RequestTransactionsTask(transactionHashes))
        }

    }
}
