package io.horizontalsystems.bitcoinkit

import io.horizontalsystems.bitcoinkit.models.BitcoinPaymentData
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.Network
import io.reactivex.Single

abstract class AbstractKit {

    protected abstract var bitcoinCore: BitcoinCore
    protected abstract var network: Network

    val balance
        get() = bitcoinCore.balance

    val lastBlockInfo
        get() = bitcoinCore.lastBlockInfo

    val networkName: String
        get() = network.javaClass.simpleName

    fun start() {
        bitcoinCore.start()
    }

    fun stop() {
        bitcoinCore.stop()
    }

    fun clear() {
        bitcoinCore.clear()
    }

    fun refresh() {
        bitcoinCore.refresh()
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return bitcoinCore.transactions(fromHash, limit)
    }

    fun fee(value: Long, address: String? = null, senderPay: Boolean = true): Long {
        return bitcoinCore.fee(value, address, senderPay)
    }

    fun send(address: String, value: Long, senderPay: Boolean = true) {
        bitcoinCore.send(address, value, senderPay)
    }

    fun receiveAddress(): String {
        return bitcoinCore.receiveAddress()
    }

    fun validateAddress(address: String) {
        bitcoinCore.validateAddress(address)
    }

    fun parsePaymentAddress(paymentAddress: String): BitcoinPaymentData {
        return bitcoinCore.parsePaymentAddress(paymentAddress)
    }

    fun showDebugInfo() {
        bitcoinCore.showDebugInfo()
    }
}