package io.horizontalsystems.bitcoinkit.dash.models

import io.horizontalsystems.bitcoinkit.io.BitcoinOutput

class CoinbaseTransactionSerializer {

    fun serialize(coinbaseTransaction: CoinbaseTransaction): ByteArray {
        return BitcoinOutput()
                .write(coinbaseTransaction.transaction.toByteArray())
                .writeVarInt(coinbaseTransaction.coinbaseTransactionSize)
                .writeUnsignedShort(coinbaseTransaction.version)
                .writeUnsignedInt(coinbaseTransaction.height)
                .write(coinbaseTransaction.merkleRootMNList)
                .toByteArray()
    }

}
