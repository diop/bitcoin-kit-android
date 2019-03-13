package io.horizontalsystems.bitcoinkit.dash.messages

import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.models.Transaction
import io.horizontalsystems.bitcoinkit.network.messages.Message
import java.io.ByteArrayInputStream

class TransactionLockMessage() : Message("ix") {

    lateinit var transaction: Transaction

    constructor(payload: ByteArray) : this() {
        BitcoinInput(ByteArrayInputStream(payload)).use { input ->
            transaction = Transaction(input)
        }
    }

    override fun getPayload(): ByteArray {
        return transaction.toByteArray()
    }

    override fun toString(): String {
        return "TransactionLockMessage(${transaction.hashHexReversed})"
    }

}
