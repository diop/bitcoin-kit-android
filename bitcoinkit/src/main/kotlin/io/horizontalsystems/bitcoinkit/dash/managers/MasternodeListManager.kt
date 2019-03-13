package io.horizontalsystems.bitcoinkit.dash.managers

import io.horizontalsystems.bitcoinkit.dash.messages.MasternodeListDiffMessage
import io.horizontalsystems.bitcoinkit.utils.HashUtils

class MasternodeListManager {

    fun getBaseBlockHash(): ByteArray {
        return HashUtils.toBytesAsLE("0000000000000000000000000000000000000000000000000000000000000000")
    }

    @Throws(InvalidMasternodeListException::class)
    fun updateList(masternodeListDiffMessage: MasternodeListDiffMessage) {
    }

}

class InvalidMasternodeListException : Exception()
