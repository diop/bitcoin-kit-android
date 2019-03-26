package io.horizontalsystems.bitcoinkit.bitcoincash

import android.content.Context
import io.horizontalsystems.bitcoinkit.AbstractKit
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.managers.ApiFeeRate
import io.horizontalsystems.bitcoinkit.managers.BitcoinCashAddressSelector
import io.horizontalsystems.bitcoinkit.network.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.TestNetBitcoinCash
import io.horizontalsystems.bitcoinkit.utils.CashAddressConverter
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser

class BitcoinCashKit : AbstractKit {

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            value?.let { bitcoinCore.addListener(it) }
        }

    constructor(context: Context, words: List<String>, walletId: String = "wallet-id", testMode: Boolean = false) {

        network = if (testMode) TestNetBitcoinCash() else MainNetBitcoinCash()

        val paymentAddressParser = PaymentAddressParser("bitcoincash", removeScheme = false)

        val addressSelector = BitcoinCashAddressSelector()

        val resource = if (testMode) "BCH/testnet" else "BCH"

        val apiFeeRate = ApiFeeRate(resource)

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(context)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setApiFeeRate(apiFeeRate)
                .setWalletId(walletId)
                .setPeerSize(2)
                .setNewWallet(true)
                .build()

        extendBitcoin(bitcoinCore, network)
    }

    private fun extendBitcoin(bitcoinCore: BitcoinCore, network: Network) {
        val bech32 = CashAddressConverter(network.addressSegwitHrp)
        bitcoinCore.prependAddressConverter(bech32)
    }
}
