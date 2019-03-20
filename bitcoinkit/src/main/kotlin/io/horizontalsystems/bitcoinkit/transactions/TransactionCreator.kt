package io.horizontalsystems.bitcoinkit.transactions

import io.horizontalsystems.bitcoinkit.core.RealmFactory
import io.horizontalsystems.bitcoinkit.models.Transaction
import io.horizontalsystems.bitcoinkit.network.peer.TransactionSender
import io.horizontalsystems.bitcoinkit.transactions.builder.TransactionBuilder

class TransactionCreator(
        private val realmFactory: RealmFactory,
        private val builder: TransactionBuilder,
        private val processor: TransactionProcessor,
        private val transactionSender: TransactionSender) {

    @Throws
    fun create(address: String, value: Long, feeRate: Int, senderPay: Boolean) {
        transactionSender.canSendTransaction()

        realmFactory.realm.use { realm ->
            val transaction = builder.buildTransaction(value, address, feeRate, senderPay, realm)

            check(realm.where(Transaction::class.java).equalTo("hashHexReversed", transaction.hashHexReversed).findFirst() == null) {
                throw TransactionAlreadyExists("hashHexReversed = ${transaction.hashHexReversed}")
            }

            processor.processOutgoing(transaction, realm)
        }

        transactionSender.sendPendingTransactions()
    }

    open class TransactionCreationException(msg: String) : Exception(msg)
    class TransactionAlreadyExists(msg: String) : TransactionCreationException(msg)

}
