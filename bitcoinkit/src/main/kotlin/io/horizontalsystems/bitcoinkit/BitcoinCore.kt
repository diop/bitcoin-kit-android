package io.horizontalsystems.bitcoinkit

import android.content.Context
import io.horizontalsystems.bitcoinkit.blocks.BlockSyncer
import io.horizontalsystems.bitcoinkit.blocks.Blockchain
import io.horizontalsystems.bitcoinkit.blocks.InitialBlockDownload
import io.horizontalsystems.bitcoinkit.core.DataProvider
import io.horizontalsystems.bitcoinkit.core.KitStateProvider
import io.horizontalsystems.bitcoinkit.core.RealmFactory
import io.horizontalsystems.bitcoinkit.core.Wallet
import io.horizontalsystems.bitcoinkit.managers.*
import io.horizontalsystems.bitcoinkit.models.BitcoinPaymentData
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.PublicKey
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.messages.BitcoinMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.IMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.messages.MessageParserChain
import io.horizontalsystems.bitcoinkit.network.peer.*
import io.horizontalsystems.bitcoinkit.storage.KitDatabase
import io.horizontalsystems.bitcoinkit.storage.Storage
import io.horizontalsystems.bitcoinkit.transactions.*
import io.horizontalsystems.bitcoinkit.transactions.builder.TransactionBuilder
import io.horizontalsystems.bitcoinkit.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoinkit.utils.AddressConverterChain
import io.horizontalsystems.bitcoinkit.utils.Base58AddressConverter
import io.horizontalsystems.bitcoinkit.utils.IAddressConverter
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

class BitcoinCoreBuilder {

    // required parameters
    private var context: Context? = null
    private var seed: ByteArray? = null
    private var words: List<String>? = null
    private var network: Network? = null
    private var paymentAddressParser: PaymentAddressParser? = null
    private var addressSelector: IAddressSelector? = null
    private var apiFeeRate: ApiFeeRate? = null
    private var walletId: String? = null

    // parameters with default values
    private var confirmationsThreshold = 6
    private var newWallet = false
    private var peerSize = 10

    fun setContext(context: Context): BitcoinCoreBuilder {
        this.context = context
        return this
    }

    fun setSeed(seed: ByteArray): BitcoinCoreBuilder {
        this.seed = seed
        return this
    }

    fun setWords(words: List<String>): BitcoinCoreBuilder {
        this.words = words
        return this
    }

    fun setNetwork(network: Network): BitcoinCoreBuilder {
        this.network = network
        return this
    }

    fun setPaymentAddressParser(paymentAddressParser: PaymentAddressParser): BitcoinCoreBuilder {
        this.paymentAddressParser = paymentAddressParser
        return this
    }

    fun setAddressSelector(addressSelector: IAddressSelector): BitcoinCoreBuilder {
        this.addressSelector = addressSelector
        return this
    }

    fun setApiFeeRate(apiFeeRate: ApiFeeRate): BitcoinCoreBuilder {
        this.apiFeeRate = apiFeeRate
        return this
    }

    fun setWalletId(walletId: String): BitcoinCoreBuilder {
        this.walletId = walletId
        return this
    }

    fun setConfirmationThreshold(confirmationsThreshold: Int): BitcoinCoreBuilder {
        this.confirmationsThreshold = confirmationsThreshold
        return this
    }

    fun setNewWallet(newWallet: Boolean): BitcoinCoreBuilder {
        this.newWallet = newWallet
        return this
    }

    fun setPeerSize(peerSize: Int): BitcoinCoreBuilder {
        this.peerSize = peerSize
        return this
    }

    fun build(): BitcoinCore {
        val context = this.context
        val seed = this.seed ?: words?.let { Mnemonic().toSeed(it) }
        val walletId = this.walletId
        val network = this.network
        val paymentAddressParser = this.paymentAddressParser
        val addressSelector = this.addressSelector
        val apiFeeRate = this.apiFeeRate

        checkNotNull(context)
        checkNotNull(seed)
        checkNotNull(network)
        checkNotNull(paymentAddressParser)
        checkNotNull(addressSelector)
        checkNotNull(apiFeeRate)
        checkNotNull(walletId)

        val addressConverter = AddressConverterChain()

        val dbName = "bitcoinkit-${network.javaClass}-$walletId"
        val database = KitDatabase.getInstance(context, dbName)
        val realmFactory = RealmFactory(dbName)
        val storage = Storage(database, realmFactory)

        val unspentOutputProvider = UnspentOutputProvider(realmFactory, confirmationsThreshold)

        val dataProvider = DataProvider(storage, realmFactory, unspentOutputProvider)

        val connectionManager = ConnectionManager(context)

        val hdWallet = HDWallet(seed, network.coinType)

        val addressManager = AddressManager.create(realmFactory, hdWallet, addressConverter)

        val transactionLinker = TransactionLinker()
        val transactionExtractor = TransactionExtractor(addressConverter)
        val transactionProcessor = TransactionProcessor(transactionExtractor, transactionLinker, addressManager, dataProvider)

        val kitStateProvider = KitStateProvider()

        val peerHostManager = PeerAddressManager(network, storage)
        val bloomFilterManager = BloomFilterManager(realmFactory)

        val peerManager = PeerManager()

        val peerGroup = PeerGroup(peerHostManager, network, peerManager, peerSize)
        peerGroup.connectionManager = connectionManager

        val transactionSyncer = TransactionSyncer(storage, transactionProcessor, addressManager, bloomFilterManager)

        val transactionSender = TransactionSender()
        transactionSender.peerGroup = peerGroup
        transactionSender.transactionSyncer = transactionSyncer

        val transactionBuilder = TransactionBuilder(realmFactory, addressConverter, hdWallet, network, addressManager, unspentOutputProvider)
        val transactionCreator = TransactionCreator(realmFactory, transactionBuilder, transactionProcessor, transactionSender)

        val feeRateSyncer = FeeRateSyncer(storage, apiFeeRate)
        val blockHashFetcher = BlockHashFetcher(addressSelector, addressConverter, BCoinApi(network, HttpRequester()), BlockHashFetcherHelper())
        val blockDiscovery = BlockDiscoveryBatch(Wallet(hdWallet), blockHashFetcher, network.checkpointBlock.height)
        val stateManager = StateManager(storage, network, newWallet)
        val initialSyncer = InitialSyncer(storage, blockDiscovery, stateManager, addressManager, kitStateProvider)

        val syncManager = SyncManager(connectionManager, feeRateSyncer, peerGroup, initialSyncer)
        initialSyncer.listener = syncManager

        val bitcoinCore = BitcoinCore(
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

        dataProvider.listener = bitcoinCore
        kitStateProvider.listener = bitcoinCore

        bitcoinCore.peerGroup = peerGroup
        bitcoinCore.transactionSyncer = transactionSyncer

        peerGroup.peerTaskHandler = bitcoinCore.peerTaskHandlerChain
        peerGroup.inventoryItemsHandler = bitcoinCore.inventoryItemsHandlerChain
        Message.Builder.messageParser = bitcoinCore.messageParserChain

        bitcoinCore.prependAddressConverter(Base58AddressConverter(network.addressVersion, network.addressScriptVersion))

        // this part can be moved to another place

        bitcoinCore.addMessageParser(BitcoinMessageParser())

        val bloomFilterLoader = BloomFilterLoader(bloomFilterManager)
        bloomFilterManager.listener = bloomFilterLoader
        bitcoinCore.addPeerGroupListener(bloomFilterLoader)

        val initialBlockDownload = InitialBlockDownload(BlockSyncer(storage, Blockchain(network, dataProvider), transactionProcessor, addressManager, bloomFilterManager, kitStateProvider, network), peerManager, kitStateProvider)
        bitcoinCore.addPeerTaskHandler(initialBlockDownload)
        bitcoinCore.addInventoryItemsHandler(initialBlockDownload)
        bitcoinCore.addPeerGroupListener(initialBlockDownload)
        initialBlockDownload.peersSyncedListener = SendTransactionsOnPeersSynced(transactionSender)

        val mempoolTransactions = MempoolTransactions(transactionSyncer)
        bitcoinCore.addPeerTaskHandler(mempoolTransactions)
        bitcoinCore.addInventoryItemsHandler(mempoolTransactions)
        bitcoinCore.addPeerGroupListener(mempoolTransactions)

        return bitcoinCore
    }

}

class BitcoinCore(private val storage: Storage, private val realmFactory: RealmFactory, private val dataProvider: DataProvider, private val addressManager: AddressManager, private val addressConverter: AddressConverterChain, private val kitStateProvider: KitStateProvider, private val transactionBuilder: TransactionBuilder, private val transactionCreator: TransactionCreator, private val paymentAddressParser: PaymentAddressParser, private val syncManager: SyncManager)
    : KitStateProvider.Listener, DataProvider.Listener {

    interface Listener {
        fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) = Unit
        fun onTransactionsDelete(hashes: List<String>) = Unit
        fun onBalanceUpdate(balance: Long) = Unit
        fun onLastBlockInfoUpdate(blockInfo: BlockInfo) = Unit
        fun onKitStateUpdate(state: KitState) = Unit
    }

    // START: Extending
    lateinit var peerGroup: PeerGroup
    lateinit var transactionSyncer: TransactionSyncer

    val inventoryItemsHandlerChain = InventoryItemsHandlerChain()
    val peerTaskHandlerChain = PeerTaskHandlerChain()
    val messageParserChain = MessageParserChain()

    fun addMessageParser(messageParser: IMessageParser) {
        messageParserChain.addParser(messageParser)
    }

    fun addInventoryItemsHandler(handler: IInventoryItemsHandler) {
        inventoryItemsHandlerChain.addHandler(handler)
    }

    fun addPeerTaskHandler(handler: IPeerTaskHandler) {
        peerTaskHandlerChain.addHandler(handler)
    }

    fun addPeerGroupListener(listener: PeerGroup.IPeerGroupListener) {
        peerGroup.addPeerGroupListener(listener)
    }

    fun prependAddressConverter(converter: IAddressConverter) {
        addressConverter.prependConverter(converter)
    }

    // END: Extending

    var listenerExecutor: Executor = Executors.newSingleThreadExecutor()

    //  DataProvider getters
    val balance get() = dataProvider.balance
    val lastBlockInfo get() = dataProvider.lastBlockInfo
    val syncState get() = kitStateProvider.syncState

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

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
            listeners.forEach {
                it.onTransactionsUpdate(inserted, updated)
            }
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onTransactionsDelete(hashes)
            }
        }
    }

    override fun onBalanceUpdate(balance: Long) {
        listenerExecutor.execute {
            listeners.forEach { it ->
                it.onBalanceUpdate(balance)
            }

        }
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onLastBlockInfoUpdate(blockInfo)
            }
        }
    }

    //
    // KitStateProvider Listener implementations
    //
    override fun onKitStateUpdate(state: KitState) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onKitStateUpdate(state)
            }
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
