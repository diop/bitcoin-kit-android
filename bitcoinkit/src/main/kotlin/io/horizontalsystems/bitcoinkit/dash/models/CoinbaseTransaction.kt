package io.horizontalsystems.bitcoinkit.dash.models

import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.models.Transaction

class CoinbaseTransaction(input: BitcoinInput) {
    val transaction = Transaction(input)
    val coinbaseTransactionSize = input.readVarInt()
    val version = input.readUnsignedShort()
    val height = input.readUnsignedInt()
    val merkleRootMNList: ByteArray = input.readBytes(32)
}
