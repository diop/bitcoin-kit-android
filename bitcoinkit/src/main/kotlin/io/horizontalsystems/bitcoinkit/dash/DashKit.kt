package io.horizontalsystems.bitcoinkit.dash

import android.content.Context
import android.util.Log
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.BitcoinKitBuilder
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
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.peer.IInventoryItemsHandler
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.transactions.TransactionSyncer
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.reactivex.Single

class DashKit(context: Context, seed: ByteArray, networkType: BitcoinKit.NetworkType, walletId: String, peerSize: Int = 10, newWallet: Boolean = false, confirmationsThreshold: Int = 6) : BitcoinKit.Listener {

    interface Listener {
        fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>)
        fun onTransactionsDelete(hashes: List<String>)
        fun onBalanceUpdate(balance: Long)
        fun onLastBlockInfoUpdate(blockInfo: BlockInfo)
        fun onKitStateUpdate(state: BitcoinKit.KitState)
    }

    private val bitcoinKit: BitcoinKit
    var listener: Listener? = null
    val balance get() = bitcoinKit.balance
    val lastBlockInfo get() = bitcoinKit.lastBlockInfo
    private var masterNodeSyncer: MasternodeListSyncer? = null

    constructor(context: Context, words: List<String>, networkType: BitcoinKit.NetworkType, walletId: String, peerSize: Int = 10, newWallet: Boolean = false, confirmationsThreshold: Int = 6) :
            this(context, Mnemonic().toSeed(words), networkType, walletId, peerSize, newWallet, confirmationsThreshold)

    init {
        bitcoinKit = BitcoinKitBuilder()
                .setContext(context)
                .setSeed(seed)
                .setNetworkType(networkType)
                .setWalletId(walletId)
                .setPeerSize(2)
                .setNewWallet(newWallet)
                .setConfirmationThreshold(confirmationsThreshold)
                .build()

        bitcoinKit.addMessageParser(DashMessageParser())

        val masterNodeSyncer = MasternodeListSyncer(bitcoinKit.peerGroup, PeerTaskFactory(), MasternodeListManager())
        bitcoinKit.addPeerTaskHandler(masterNodeSyncer)

        val instantSend = InstantSend(bitcoinKit.transactionSyncer)
        bitcoinKit.addInventoryItemsHandler(instantSend)
        bitcoinKit.addPeerTaskHandler(instantSend)

        bitcoinKit.listener = this
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return bitcoinKit.transactions(fromHash, limit)
    }

    fun start() {
        bitcoinKit.start()
    }

    fun clear() {
        bitcoinKit.clear()
    }

    fun receiveAddress(): String {
        return bitcoinKit.receiveAddress()
    }

    fun send(address: String, value: Long, senderPay: Boolean = true) {
        bitcoinKit.send(address, value, senderPay)
    }

    fun fee(value: Long, address: String? = null, senderPay: Boolean = true): Long {
        return bitcoinKit.fee(value, address, senderPay)
    }

    fun showDebugInfo() {
        bitcoinKit.showDebugInfo()
    }

    override fun onTransactionsUpdate(bitcoinKit: BitcoinKit, inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        listener?.onTransactionsUpdate(inserted, updated)
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listener?.onTransactionsDelete(hashes)
    }

    override fun onBalanceUpdate(bitcoinKit: BitcoinKit, balance: Long) {
        listener?.onBalanceUpdate(balance)
    }

    override fun onLastBlockInfoUpdate(bitcoinKit: BitcoinKit, blockInfo: BlockInfo) {
        if (bitcoinKit.syncState == BitcoinKit.KitState.Synced) {
            masterNodeSyncer?.sync(blockInfo.headerHash.hexStringToByteArray().reversedArray())
        }

        listener?.onLastBlockInfoUpdate(blockInfo)
    }

    override fun onKitStateUpdate(bitcoinKit: BitcoinKit, state: BitcoinKit.KitState) {
        if (state == BitcoinKit.KitState.Synced) {
            bitcoinKit.lastBlockInfo?.let {
                masterNodeSyncer?.sync(it.headerHash.hexStringToByteArray().reversedArray())
            }
        }

        listener?.onKitStateUpdate(state)
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
