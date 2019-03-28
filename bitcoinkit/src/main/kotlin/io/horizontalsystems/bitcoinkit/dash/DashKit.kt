package io.horizontalsystems.bitcoinkit.dash

import android.arch.persistence.room.Room
import android.content.Context
import io.horizontalsystems.bitcoinkit.AbstractKit
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.core.hexStringToByteArray
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeSortedList
import io.horizontalsystems.bitcoinkit.dash.masternodelist.MasternodeCbTxHasher
import io.horizontalsystems.bitcoinkit.dash.masternodelist.MasternodeListMerkleRootCalculator
import io.horizontalsystems.bitcoinkit.dash.masternodelist.MerkleRootCreator
import io.horizontalsystems.bitcoinkit.dash.masternodelist.MerkleRootHasher
import io.horizontalsystems.bitcoinkit.dash.messages.DashMessageParser
import io.horizontalsystems.bitcoinkit.dash.models.CoinbaseTransactionSerializer
import io.horizontalsystems.bitcoinkit.dash.models.MasternodeSerializer
import io.horizontalsystems.bitcoinkit.dash.storage.DashKitDatabase
import io.horizontalsystems.bitcoinkit.dash.storage.DashStorage
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.network.MainNetDash
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.TestNetDash
import io.horizontalsystems.bitcoinkit.utils.MerkleBranch
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser

class DashKit : AbstractKit, BitcoinCore.Listener {

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            value?.let { bitcoinCore.addListener(it) }
        }

    private val database: DashKitDatabase
    private val storage: DashStorage
    private var masterNodeSyncer: MasternodeListSyncer? = null

    constructor(context: Context, words: List<String>, walletId: String = "wallet-id", testMode: Boolean = false) {

        network = if (testMode) TestNetDash() else MainNetDash()

        val databaseName = "bitcoinkit-${network.javaClass}-$walletId"

        database = Room.databaseBuilder(context, DashKitDatabase::class.java, databaseName)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .addMigrations()
                .build()

        storage = DashStorage(database, databaseName)

        val paymentAddressParser = PaymentAddressParser("bitcoin", removeScheme = true)

        val addressSelector = BitcoinAddressSelector()

        val apiFeeRateResource = if (testMode) "DASH/testnet" else "DASH"

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(context)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setApiFeeRateResource(apiFeeRateResource)
                .setWalletId(walletId)
                .setPeerSize(2)
                .setStorage(storage)
                .setNewWallet(true)
                .build()

        extendBitcoin(bitcoinCore)
    }

    private fun extendBitcoin(bitcoinCore: BitcoinCore) {
        bitcoinCore.addListener(this)

        bitcoinCore.addMessageParser(DashMessageParser())

        val merkleRootHasher = MerkleRootHasher()
        val merkleRootCreator = MerkleRootCreator(merkleRootHasher)
        val masternodeListMerkleRootCalculator = MasternodeListMerkleRootCalculator(MasternodeSerializer(), merkleRootHasher, merkleRootCreator)
        val masternodeCbTxHasher = MasternodeCbTxHasher(CoinbaseTransactionSerializer(), merkleRootHasher)

        val masternodeListManager = MasternodeListManager(storage, masternodeListMerkleRootCalculator, masternodeCbTxHasher, MerkleBranch(), MasternodeSortedList())
        val masterNodeSyncer = MasternodeListSyncer(bitcoinCore.peerGroup, PeerTaskFactory(), masternodeListManager)
        bitcoinCore.addPeerTaskHandler(masterNodeSyncer)

        this.masterNodeSyncer = masterNodeSyncer

        val instantSend = InstantSend(bitcoinCore.transactionSyncer)
        bitcoinCore.addInventoryItemsHandler(instantSend)
        bitcoinCore.addPeerTaskHandler(instantSend)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        if (bitcoinCore.syncState == BitcoinCore.KitState.Synced) {
            masterNodeSyncer?.sync(blockInfo.headerHash.hexStringToByteArray().reversedArray())
        }
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        if (state == BitcoinCore.KitState.Synced) {
            bitcoinCore.lastBlockInfo?.let {
                masterNodeSyncer?.sync(it.headerHash.hexStringToByteArray().reversedArray())
            }
        }
    }
}
