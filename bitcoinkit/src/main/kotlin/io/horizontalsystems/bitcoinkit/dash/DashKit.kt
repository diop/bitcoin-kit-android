package io.horizontalsystems.bitcoinkit.dash

import android.util.Log
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.core.toHexString
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockRequestsTask
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestTransactionLockVotesTask
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.messages.BitcoinMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.IMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.peer.IInventoryItemsHandler
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer

class DashKit {

    fun init(bitcoinKit: BitcoinKit) {
        val configurator = DashConfigurator()
        val instantSend = InstantSend(bitcoinKit.peerGroup.transactionSyncer)

        Message.Builder.messageParser = configurator.getMessageParser()
        bitcoinKit.peerGroup.inventoryItemsHandler = instantSend
        bitcoinKit.peerGroup.peerTaskHandler = instantSend
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

    override fun handleCompletedTask(peer: Peer, task: PeerTask) {
        when (task) {
            is RequestTransactionLockRequestsTask -> {
                transactionSyncer?.handleTransactions(task.transactions)
            }
            is RequestTransactionLockVotesTask -> {
                task.transactionLockVotes.forEach {
                    Log.e("AAA", "Received tx lock vote for tx: ${it.txHash.reversedArray().toHexString()}")
                }
            }
        }
    }

}

class DashConfigurator {

    fun getMessageParser(): IMessageParser {
        val dashMessageParser = DashMessageParser()
        dashMessageParser.nextParser = BitcoinMessageParser()

        return dashMessageParser
    }

}

object InventoryType {
    const val MSG_TXLOCK_REQUEST = 4

    const val MSG_TXLOCK_VOTE = 5
}
