package io.horizontalsystems.bitcoinkit.demo

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCore.KitState
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.dash.DashKit
import io.horizontalsystems.bitcoinkit.managers.ApiFeeRate
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.managers.BitcoinCashAddressSelector
import io.horizontalsystems.bitcoinkit.managers.IAddressSelector
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.*
import io.horizontalsystems.bitcoinkit.utils.Bech32AddressConverter
import io.horizontalsystems.bitcoinkit.utils.CashAddressConverter
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser
import io.horizontalsystems.bitcoinkit.utils.SegwitAddressConverter
import io.reactivex.disposables.CompositeDisposable

class MainViewModel : ViewModel(), BitcoinCore.Listener {

    enum class State {
        STARTED, STOPPED
    }

    val transactions = MutableLiveData<List<TransactionInfo>>()
    val balance = MutableLiveData<Long>()
    val lastBlockHeight = MutableLiveData<Int>()
    val state = MutableLiveData<KitState>()
    val status = MutableLiveData<State>()
    val networkName: String
    private val disposables = CompositeDisposable()

    private var started = false
        set(value) {
            field = value
            status.value = (if (value) State.STARTED else State.STOPPED)
        }

    private var bitcoinCore: BitcoinCore

    init {
        val words = listOf("used", "ugly", "meat", "glad", "balance", "divorce", "inner", "artwork", "hire", "invest", "already", "piano")
        val networkType = BitcoinCore.NetworkType.TestNetDash

        val network: Network = when (networkType) {
            BitcoinCore.NetworkType.MainNet -> MainNet()
            BitcoinCore.NetworkType.MainNetBitCash -> MainNetBitcoinCash()
            BitcoinCore.NetworkType.MainNetDash -> MainNetDash()
            BitcoinCore.NetworkType.TestNet -> TestNet()
            BitcoinCore.NetworkType.TestNetBitCash -> TestNetBitcoinCash()
            BitcoinCore.NetworkType.TestNetDash -> TestNetDash()
            BitcoinCore.NetworkType.RegTest -> RegTest()
        }

        val paymentAddressParser = when (networkType) {
            BitcoinCore.NetworkType.MainNetDash,
            BitcoinCore.NetworkType.TestNetDash,
            BitcoinCore.NetworkType.MainNet,
            BitcoinCore.NetworkType.TestNet,
            BitcoinCore.NetworkType.RegTest -> {
                PaymentAddressParser("bitcoin", removeScheme = true)
            }
            BitcoinCore.NetworkType.MainNetBitCash,
            BitcoinCore.NetworkType.TestNetBitCash -> {
                PaymentAddressParser("bitcoincash", removeScheme = false)
            }
        }

        val addressSelector: IAddressSelector = when (networkType) {
            BitcoinCore.NetworkType.MainNetDash,
            BitcoinCore.NetworkType.TestNetDash,
            BitcoinCore.NetworkType.MainNet,
            BitcoinCore.NetworkType.TestNet,
            BitcoinCore.NetworkType.RegTest -> {
                BitcoinAddressSelector()
            }
            BitcoinCore.NetworkType.MainNetBitCash,
            BitcoinCore.NetworkType.TestNetBitCash -> {
                BitcoinCashAddressSelector()
            }
        }

        val apiFeeRate = ApiFeeRate(networkType)

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(App.instance)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setApiFeeRate(apiFeeRate)
                .setWalletId("wallet-id")
                .setPeerSize(2)
                .setNewWallet(true)
                .build()

        bitcoinCore.addListener(this)


        val bech32: Bech32AddressConverter = when (network) {
            is MainNetBitcoinCash,
            is TestNetBitcoinCash -> CashAddressConverter(network.addressSegwitHrp)
            // MainNet, TestNet, RegTest
            else -> SegwitAddressConverter(network.addressSegwitHrp)
        }

        bitcoinCore.prependAddressConverter(bech32)

        DashKit().extendBitcoin(bitcoinCore)

        networkName = networkType.name
        balance.value = bitcoinCore.balance

        bitcoinCore.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.value = txList.sortedByDescending { it.blockHeight }
        }.let {
            disposables.add(it)
        }

        lastBlockHeight.value = bitcoinCore.lastBlockInfo?.height ?: 0
        state.value = KitState.NotSynced

        started = false
    }

    fun start() {
        if (started) return
        started = true

        bitcoinCore.start()
    }

    fun clear() {
        bitcoinCore.clear()
    }

    fun receiveAddress(): String {
        return bitcoinCore.receiveAddress()
    }

    fun send(address: String, amount: Long) {
        bitcoinCore.send(address, amount)
    }

    fun fee(value: Long, address: String? = null): Long {
        return bitcoinCore.fee(value, address)
    }

    fun showDebugInfo() {
        bitcoinCore.showDebugInfo()
    }

    //
    // BitcoinKit Listener implementations
    //
    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        bitcoinCore.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.postValue(txList.sortedByDescending { it.blockHeight })
        }.let {
            disposables.add(it)
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
    }

    override fun onBalanceUpdate(balance: Long) {
        this.balance.postValue(balance)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        this.lastBlockHeight.postValue(blockInfo.height)
    }

    override fun onKitStateUpdate(state: KitState) {
        this.state.postValue(state)
    }
}
