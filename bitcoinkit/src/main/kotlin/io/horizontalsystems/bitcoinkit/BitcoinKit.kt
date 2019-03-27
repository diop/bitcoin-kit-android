package io.horizontalsystems.bitcoinkit

import android.content.Context
import io.horizontalsystems.bitcoinkit.blocks.BlockSyncer
import io.horizontalsystems.bitcoinkit.blocks.Blockchain
import io.horizontalsystems.bitcoinkit.core.DataProvider
import io.horizontalsystems.bitcoinkit.core.KitStateProvider
import io.horizontalsystems.bitcoinkit.core.RealmFactory
import io.horizontalsystems.bitcoinkit.core.Wallet
import io.horizontalsystems.bitcoinkit.managers.*
import io.horizontalsystems.bitcoinkit.models.BitcoinPaymentData
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.PublicKey
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.*
import io.horizontalsystems.bitcoinkit.network.messages.BitcoinMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.IMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.peer.*
import io.horizontalsystems.bitcoinkit.storage.KitDatabase
import io.horizontalsystems.bitcoinkit.storage.Storage
import io.horizontalsystems.bitcoinkit.transactions.*
import io.horizontalsystems.bitcoinkit.transactions.builder.TransactionBuilder
import io.horizontalsystems.bitcoinkit.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoinkit.utils.AddressConverter
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.reactivex.Single
import io.realm.Realm
import io.realm.annotations.RealmModule
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RealmModule(library = true, allClasses = true)
class BitcoinKitModule

class BitcoinKitBuilder {

    var peerGroup: PeerGroup? = null

    // required parameters
    private var context: Context? = null
    private var seed: ByteArray? = null
    private var words: List<String>? = null
    private var networkType: BitcoinKit.NetworkType? = null
    private var walletId: String? = null

    // parameters with default values
    private var confirmationsThreshold = 6
    private var newWallet = false
    private var peerSize = 10

    fun setContext(context: Context): BitcoinKitBuilder {
        this.context = context
        return this
    }

    fun setSeed(seed: ByteArray): BitcoinKitBuilder {
        this.seed = seed
        return this
    }

    fun setWords(words: List<String>): BitcoinKitBuilder {
        this.words = words
        return this
    }

    fun setNetworkType(networkType: BitcoinKit.NetworkType): BitcoinKitBuilder {
        this.networkType = networkType
        return this
    }

    fun setWalletId(walletId: String): BitcoinKitBuilder {
        this.walletId = walletId
        return this
    }

    fun setConfirmationThreshold(confirmationsThreshold: Int): BitcoinKitBuilder {
        this.confirmationsThreshold = confirmationsThreshold
        return this
    }

    fun setNewWallet(newWallet: Boolean): BitcoinKitBuilder {
        this.newWallet = newWallet
        return this
    }

    fun setPeerSize(peerSize: Int): BitcoinKitBuilder {
        this.peerSize = peerSize
        return this
    }

    var transactionSyncer: TransactionSyncer? = null

    fun build(): BitcoinKit {
        val context = this.context
        val seed = this.seed ?: words?.let { Mnemonic().toSeed(it) }
        val networkType = this.networkType
        val walletId = this.walletId

        checkNotNull(context)
        checkNotNull(seed)
        checkNotNull(networkType)
        checkNotNull(walletId)

        val network: Network = when (networkType) {
            BitcoinKit.NetworkType.MainNet -> MainNet()
            BitcoinKit.NetworkType.MainNetBitCash -> MainNetBitcoinCash()
            BitcoinKit.NetworkType.MainNetDash -> MainNetDash()
            BitcoinKit.NetworkType.TestNet -> TestNet()
            BitcoinKit.NetworkType.TestNetBitCash -> TestNetBitcoinCash()
            BitcoinKit.NetworkType.TestNetDash -> TestNetDash()
            BitcoinKit.NetworkType.RegTest -> RegTest()
        }

        val dbName = "bitcoinkit-${networkType.name}-$walletId"
        val database = KitDatabase.getInstance(context, dbName)
        val realmFactory = RealmFactory(dbName)
        val storage = Storage(database, realmFactory)

        val unspentOutputProvider = UnspentOutputProvider(realmFactory, confirmationsThreshold)

        val dataProvider = DataProvider(storage, realmFactory, unspentOutputProvider)

        val connectionManager = ConnectionManager(context)

        val hdWallet = HDWallet(seed, network.coinType)

        val addressConverter = AddressConverter(network)

        val addressManager = AddressManager.create(realmFactory, hdWallet, addressConverter)

        val transactionLinker = TransactionLinker()
        val transactionExtractor = TransactionExtractor(addressConverter)
        val transactionProcessor = TransactionProcessor(transactionExtractor, transactionLinker, addressManager, dataProvider)

        val kitStateProvider = KitStateProvider()

        val peerHostManager = PeerAddressManager(network, storage)
        val bloomFilterManager = BloomFilterManager(realmFactory)

        val transactionSender = TransactionSender()

        val peerGroup = PeerGroup(peerHostManager, bloomFilterManager, network, kitStateProvider, peerSize)
        peerGroup.blockSyncer = BlockSyncer(storage, Blockchain(network, dataProvider), transactionProcessor, addressManager, bloomFilterManager, kitStateProvider, network)
        peerGroup.connectionManager = connectionManager
        peerGroup.peersSyncedListener = SendTransactionsOnPeersSynced(transactionSender)
        peerGroup.peerTaskHandler = peerTaskHandlerChain
        peerGroup.inventoryItemsHandler = inventoryItemsHandlerChain

        val transactionBuilder = TransactionBuilder(realmFactory, addressConverter, hdWallet, network, addressManager, unspentOutputProvider)
        val transactionCreator = TransactionCreator(realmFactory, transactionBuilder, transactionProcessor, transactionSender)

        val paymentAddressParser = when (networkType) {
            BitcoinKit.NetworkType.MainNetDash,
            BitcoinKit.NetworkType.TestNetDash,
            BitcoinKit.NetworkType.MainNet,
            BitcoinKit.NetworkType.TestNet,
            BitcoinKit.NetworkType.RegTest -> {
                PaymentAddressParser("bitcoin", removeScheme = true)
            }
            BitcoinKit.NetworkType.MainNetBitCash,
            BitcoinKit.NetworkType.TestNetBitCash -> {
                PaymentAddressParser("bitcoincash", removeScheme = false)
            }
        }

        val addressSelector: IAddressSelector = when (networkType) {
            BitcoinKit.NetworkType.MainNetDash,
            BitcoinKit.NetworkType.TestNetDash,
            BitcoinKit.NetworkType.MainNet,
            BitcoinKit.NetworkType.TestNet,
            BitcoinKit.NetworkType.RegTest -> {
                BitcoinAddressSelector(addressConverter)
            }
            BitcoinKit.NetworkType.MainNetBitCash,
            BitcoinKit.NetworkType.TestNetBitCash -> {
                BitcoinCashAddressSelector(addressConverter)
            }
        }

        val feeRateSyncer = FeeRateSyncer(storage, ApiFeeRate(networkType))
        val blockHashFetcher = BlockHashFetcher(addressSelector, BCoinApi(network, HttpRequester()), BlockHashFetcherHelper())
        val blockDiscovery = BlockDiscoveryBatch(Wallet(hdWallet), blockHashFetcher, network.checkpointBlock.height)
        val stateManager = StateManager(storage, network, newWallet)
        val initialSyncer = InitialSyncer(storage, blockDiscovery, stateManager, addressManager, kitStateProvider)

        val syncManager = SyncManager(connectionManager, feeRateSyncer, peerGroup, initialSyncer)
        initialSyncer.listener = syncManager

        val bitcoinKit = BitcoinKit(
                storage,
                realmFactory,
                dataProvider,
                addressManager,
                addressConverter,
                kitStateProvider,
                transactionBuilder,
                transactionCreator,
                paymentAddressParser,
                syncManager)

        dataProvider.listener = bitcoinKit
        kitStateProvider.listener = bitcoinKit

        this.peerGroup = peerGroup


        // this part can be moved to another place

        transactionSyncer = TransactionSyncer(storage, transactionProcessor, addressManager, bloomFilterManager)

        val transactionXxx = TransactionXxx(peerGroup, transactionSyncer!!)

        peerTaskHandlerChain.addHandler(transactionXxx)
        inventoryItemsHandlerChain.addHandler(transactionXxx)



        return bitcoinKit
    }

    private val inventoryItemsHandlerChain = InventoryItemsHandlerChain()
    private val peerTaskHandlerChain = PeerTaskHandlerChain()

    fun addMessageParser(messageParser: IMessageParser) {
        messageParser.nextParser = BitcoinMessageParser()

        Message.Builder.messageParser = messageParser
    }

    fun addInventoryItemsHandler(handler: IInventoryItemsHandler) {
        inventoryItemsHandlerChain.addHandler(handler)
    }

    fun addPeerTaskHandler(handler: IPeerTaskHandler) {
        peerTaskHandlerChain.addHandler(handler)
    }

}

class BitcoinKit(private val storage: Storage, private val realmFactory: RealmFactory, private val dataProvider: DataProvider, private val addressManager: AddressManager, private val addressConverter: AddressConverter, private val kitStateProvider: KitStateProvider, private val transactionBuilder: TransactionBuilder, private val transactionCreator: TransactionCreator, private val paymentAddressParser: PaymentAddressParser, private val syncManager: SyncManager)
    : KitStateProvider.Listener, DataProvider.Listener {

    interface Listener {
        fun onTransactionsUpdate(bitcoinKit: BitcoinKit, inserted: List<TransactionInfo>, updated: List<TransactionInfo>) = Unit
        fun onTransactionsDelete(hashes: List<String>) = Unit
        fun onBalanceUpdate(bitcoinKit: BitcoinKit, balance: Long) = Unit
        fun onLastBlockInfoUpdate(bitcoinKit: BitcoinKit, blockInfo: BlockInfo) = Unit
        fun onKitStateUpdate(bitcoinKit: BitcoinKit, state: KitState) = Unit
    }

    var listener: Listener? = null
    var listenerExecutor: Executor = Executors.newSingleThreadExecutor()

    //  DataProvider getters
    val balance get() = dataProvider.balance
    val lastBlockInfo get() = dataProvider.lastBlockInfo
    val syncState get() = kitStateProvider.syncState

    //
    // API methods
    //
    fun start() {
        syncManager.start()
    }

    fun stop() {
        dataProvider.clear()
        syncManager.stop()
        storage.clear()
    }

    fun clear() {
        stop()
        realmFactory.realm.use { realm ->
            realm.executeTransaction { it.deleteAll() }
        }
    }

    fun refresh() {
        start()
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return dataProvider.transactions(fromHash, limit)
    }

    fun fee(value: Long, address: String? = null, senderPay: Boolean = true): Long {
        return transactionBuilder.fee(value, dataProvider.feeRate.medium, senderPay, address)
    }

    fun send(address: String, value: Long, senderPay: Boolean = true) {
        transactionCreator.create(address, value, dataProvider.feeRate.medium, senderPay)
    }

    fun receiveAddress(): String {
        return addressManager.receiveAddress()
    }

    fun validateAddress(address: String) {
        addressConverter.convert(address)
    }

    fun parsePaymentAddress(paymentAddress: String): BitcoinPaymentData {
        return paymentAddressParser.parse(paymentAddress)
    }

    fun showDebugInfo() {
        addressManager.fillGap()
        realmFactory.realm.use { realm ->
            realm.where(PublicKey::class.java).findAll().forEach { pubKey ->
                try {
//                    val scriptType = if (network is MainNetBitcoinCash || network is TestNetBitcoinCash)
//                        ScriptType.P2PKH else
//                        ScriptType.P2WPKH

                    val legacy = addressConverter.convert(pubKey.publicKeyHash, ScriptType.P2PKH).string
//                    val wpkh = addressConverter.convert(pubKey.scriptHashP2WPKH, ScriptType.P2SH).string
//                    val bechAddress = try {
//                        addressConverter.convert(OpCodes.push(0) + OpCodes.push(pubKey.publicKeyHash), scriptType).string
//                    } catch (e: Exception) {
//                        ""
//                    }
                    println("${pubKey.index} --- extrnl: ${pubKey.external} --- hash: ${pubKey.publicKeyHex} ---- legacy: $legacy")
//                    println("legacy: $legacy --- bech32: $bechAddress --- SH(WPKH): $wpkh")
                } catch (e: Exception) {
                    println(e.message)
                }
            }
        }
    }

    //
    // DataProvider Listener implementations
    //
    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        listenerExecutor.execute {
            listener?.onTransactionsUpdate(this, inserted, updated)
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listenerExecutor.execute {
            listener?.onTransactionsDelete(hashes)
        }
    }

    override fun onBalanceUpdate(balance: Long) {
        listenerExecutor.execute {
            listener?.onBalanceUpdate(this, balance)
        }
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        listenerExecutor.execute {
            listener?.onLastBlockInfoUpdate(this, blockInfo)
        }
    }

    //
    // KitStateProvider Listener implementations
    //
    override fun onKitStateUpdate(state: KitState) {
        listenerExecutor.execute {
            listener?.onKitStateUpdate(this, state)
        }
    }

    enum class NetworkType {
        MainNet,
        TestNet,
        RegTest,
        MainNetBitCash,
        TestNetBitCash,
        MainNetDash,
        TestNetDash
    }

    sealed class KitState {
        object Synced : KitState()
        object NotSynced : KitState()
        class Syncing(val progress: Double) : KitState()

        override fun equals(other: Any?) = when {
            this is Synced && other is Synced -> true
            this is NotSynced && other is NotSynced -> true
            this is Syncing && other is Syncing -> this.progress == other.progress
            else -> false
        }

        override fun hashCode(): Int {
            var result = javaClass.hashCode()
            if (this is Syncing) {
                result = 31 * result + progress.hashCode()
            }
            return result
        }
    }

    companion object {
        fun init(context: Context) {
            Realm.init(context)
        }
    }
}
