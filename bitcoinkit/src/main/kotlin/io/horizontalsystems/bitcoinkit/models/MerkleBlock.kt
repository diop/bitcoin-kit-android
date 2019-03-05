package io.horizontalsystems.bitcoinkit.models

class MerkleBlock(val header: Header, val associatedTransactionHexes: List<String>) {

    var height: Int? = null
    var associatedTransactions = mutableListOf<Transaction>()
    val blockHash = header.hash

    val complete: Boolean
        get() = associatedTransactionHexes.size == associatedTransactions.size

}
