package io.horizontalsystems.bitcoinkit.bitcoin

import android.content.Context
import io.horizontalsystems.bitcoinkit.AbstractKit
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.managers.ApiFeeRate
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.network.MainNet
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.TestNet
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser
import io.horizontalsystems.bitcoinkit.utils.SegwitAddressConverter

class BitcoinKit : AbstractKit {

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            value?.let { bitcoinCore.addListener(it) }
        }

    constructor(context: Context, words: List<String>, walletId: String = "wallet-id", testMode: Boolean = false) {

        network = if (testMode) TestNet() else MainNet()

        val paymentAddressParser = PaymentAddressParser("bitcoin", removeScheme = true)

        val addressSelector = BitcoinAddressSelector()

        val resource = if (testMode) "BTC/testnet" else "BTC"

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
        val bech32 = SegwitAddressConverter(network.addressSegwitHrp)
        bitcoinCore.prependAddressConverter(bech32)
    }
}
