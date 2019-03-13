package io.horizontalsystems.bitcoinkit.dash.models

import io.horizontalsystems.bitcoinkit.io.BitcoinInput

class Masternode(input: BitcoinInput) {
    val proRegTxHash = input.readBytes(32)
    val confirmedHash = input.readBytes(32)
    val ipAddress = input.readBytes(16)
    val port = input.readUnsignedShort()
    val pubKeyOperator = input.readBytes(48)
    val keyIDVoting = input.readBytes(20)
    val isValid = input.readByte().toInt() != 0
}
