package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.network.peer.task.RequestTransactionsTask
import io.horizontalsystems.bitcoinkit.network.peer.task.SendTransactionTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

class MempoolTransactions(var transactionSyncer: TransactionSyncer) : IPeerTaskHandler, IInventoryItemsHandler, PeerGroup.IPeerGroupListener {

    private val requestedTransactions = hashMapOf<String, MutableList<ByteArray>>()

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        return when (task) {
            is RequestTransactionsTask -> {
                transactionSyncer.handleTransactions(task.transactions)
                removeFromRequestedTransactions(peer.host, task.transactions.map { it.hash })
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
            if (item.type == InventoryItem.MSG_TX
                    && !isTransactionRequested(item.hash)
                    && transactionSyncer.shouldRequestTransaction(item.hash)) {
                transactionHashes.add(item.hash)
            }
        }

        if (transactionHashes.isNotEmpty()) {
            peer.addTask(RequestTransactionsTask(transactionHashes))

            addToRequestedTransactions(peer.host, transactionHashes)
        }
    }

    override fun onPeerDisconnect(peer: Peer, e: Exception?) {
        requestedTransactions.remove(peer.host)
    }

    private fun addToRequestedTransactions(peerHost: String, transactionHashes: List<ByteArray>) {
        if (!requestedTransactions.containsKey(peerHost)) {
            requestedTransactions[peerHost] = mutableListOf()
        }

        requestedTransactions[peerHost]?.addAll(transactionHashes)
    }

    private fun removeFromRequestedTransactions(peerHost: String, transactionHashes: List<ByteArray>) {
        transactionHashes.forEach { transactionHash ->
            val i = requestedTransactions[peerHost]?.indexOfFirst {
                it.contentEquals(transactionHash)
            }

            if (i != null && i != -1) {
                requestedTransactions[peerHost]?.removeAt(i)
            }
        }
    }

    private fun isTransactionRequested(hash: ByteArray): Boolean {
        return requestedTransactions.any { (_, inventories) ->
            inventories.any {
                it.contentEquals(hash)
            }
        }
    }
}