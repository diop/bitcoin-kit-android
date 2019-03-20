package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.network.peer.task.SendTransactionTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

interface IAllPeersSyncedListener {
    fun onAllPeersSynced()
}

class SendTransactionsOnPeersSynced(var transactionSender: TransactionSender) : IAllPeersSyncedListener {

    override fun onAllPeersSynced() {
        transactionSender.sendPendingTransactions()
    }

}

class TransactionSender {
    var transactionSyncer: TransactionSyncer? = null
    var peerGroup: PeerGroup? = null

    fun sendPendingTransactions() {
        try {
            peerGroup?.checkPeersSynced()

            peerGroup?.someReadyPeers()?.forEach { peer ->
                transactionSyncer?.getPendingTransactions()?.forEach { pendingTransaction ->
                    peer.addTask(SendTransactionTask(pendingTransaction))
                }
            }


        } catch (e: PeerGroup.Error) {
//            logger.warning("Handling pending transactions failed with: ${e.message}")
        }

    }

    fun canSendTransaction() {
        peerGroup?.checkPeersSynced()
    }

}
