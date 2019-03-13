package io.horizontalsystems.bitcoinkit.dash

import io.horizontalsystems.bitcoinkit.dash.messages.TransactionLockMessage
import io.horizontalsystems.bitcoinkit.dash.messages.TransactionLockVoteMessage
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.messages.IMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.Message

class DashMessageParser : IMessageParser {
    override var nextParser: IMessageParser? = null

    override fun parseMessage(command: String, payload: ByteArray, network: Network): Message? {
        return when (command) {
            "ix" -> TransactionLockMessage(payload)
            "txlvote" -> TransactionLockVoteMessage(payload)
            else -> nextParser?.parseMessage(command, payload, network)
        }
    }
}
