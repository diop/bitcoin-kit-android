package io.horizontalsystems.bitcoinkit.demo

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.BitcoinKit.KitState
import io.horizontalsystems.bitcoinkit.BitcoinKitBuilder
import io.horizontalsystems.bitcoinkit.dash.DashKit
import io.horizontalsystems.bitcoinkit.managers.ApiFeeRate
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.managers.BitcoinCashAddressSelector
import io.horizontalsystems.bitcoinkit.managers.IAddressSelector
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.*
import io.horizontalsystems.bitcoinkit.utils.AddressConverter
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser
import io.reactivex.disposables.CompositeDisposable

class MainViewModel : ViewModel(), BitcoinKit.Listener {

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

    private var bitcoinKit: BitcoinKit

    init {
        val words = listOf("used", "ugly", "meat", "glad", "balance", "divorce", "inner", "artwork", "hire", "invest", "already", "piano")
        val networkType = BitcoinKit.NetworkType.TestNetDash

        val network: Network = when (networkType) {
            BitcoinKit.NetworkType.MainNet -> MainNet()
            BitcoinKit.NetworkType.MainNetBitCash -> MainNetBitcoinCash()
            BitcoinKit.NetworkType.MainNetDash -> MainNetDash()
            BitcoinKit.NetworkType.TestNet -> TestNet()
            BitcoinKit.NetworkType.TestNetBitCash -> TestNetBitcoinCash()
            BitcoinKit.NetworkType.TestNetDash -> TestNetDash()
            BitcoinKit.NetworkType.RegTest -> RegTest()
        }

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

        val addressConverter = AddressConverter(network)

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

        val apiFeeRate = ApiFeeRate(networkType)


        bitcoinKit = BitcoinKitBuilder()
                .setContext(App.instance)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressConverter(addressConverter)
                .setAddressSelector(addressSelector)
                .setApiFeeRate(apiFeeRate)
                .setWalletId("wallet-id")
                .setPeerSize(2)
                .setNewWallet(true)
                .build()

        bitcoinKit.addListener(this)

        DashKit().extendBitcoin(bitcoinKit)

        networkName = networkType.name
        balance.value = bitcoinKit.balance

        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.value = txList.sortedByDescending { it.blockHeight }
        }.let {
            disposables.add(it)
        }

        lastBlockHeight.value = bitcoinKit.lastBlockInfo?.height ?: 0
        state.value = KitState.NotSynced

        started = false
    }

    fun start() {
        if (started) return
        started = true

        bitcoinKit.start()
    }

    fun clear() {
        bitcoinKit.clear()
    }

    fun receiveAddress(): String {
        return bitcoinKit.receiveAddress()
    }

    fun send(address: String, amount: Long) {
        bitcoinKit.send(address, amount)
    }

    fun fee(value: Long, address: String? = null): Long {
        return bitcoinKit.fee(value, address)
    }

    fun showDebugInfo() {
        bitcoinKit.showDebugInfo()
    }

    //
    // BitcoinKit Listener implementations
    //
    override fun onTransactionsUpdate(bitcoinKit: BitcoinKit, inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        bitcoinKit.transactions().subscribe { txList: List<TransactionInfo> ->
            transactions.postValue(txList.sortedByDescending { it.blockHeight })
        }.let {
            disposables.add(it)
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
    }

    override fun onBalanceUpdate(bitcoinKit: BitcoinKit, balance: Long) {
        this.balance.postValue(balance)
    }

    override fun onLastBlockInfoUpdate(bitcoinKit: BitcoinKit, blockInfo: BlockInfo) {
        this.lastBlockHeight.postValue(blockInfo.height)
    }

    override fun onKitStateUpdate(bitcoinKit: BitcoinKit, state: KitState) {
        this.state.postValue(state)
    }
}
