package io.horizontalsystems.bitcoinkit.bitcoin

import android.arch.persistence.room.Room
import android.content.Context
import io.horizontalsystems.bitcoinkit.AbstractKit
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.dash.storage.DashKitDatabase
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.network.MainNet
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.TestNet
import io.horizontalsystems.bitcoinkit.storage.Storage
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

        val databaseName = "bitcoinkit-${network.javaClass}-$walletId"

        val database = Room.databaseBuilder(context, DashKitDatabase::class.java, databaseName)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .addMigrations()
                .build()

        val storage = Storage(database, databaseName)

        val paymentAddressParser = PaymentAddressParser("bitcoin", removeScheme = true)

        val addressSelector = BitcoinAddressSelector()

        val apiFeeRateResource = if (testMode) "BTC/testnet" else "BTC"

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(context)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setApiFeeRateResource(apiFeeRateResource)
                .setWalletId(walletId)
                .setPeerSize(2)
                .setNewWallet(true)
                .setStorage(storage)
                .build()

        extendBitcoin(bitcoinCore, network)
    }

    private fun extendBitcoin(bitcoinCore: BitcoinCore, network: Network) {
        val bech32 = SegwitAddressConverter(network.addressSegwitHrp)
        bitcoinCore.prependAddressConverter(bech32)
    }
}
