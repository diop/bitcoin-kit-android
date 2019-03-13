package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.network.Network

interface IMessageParser {
    var nextParser: IMessageParser?

    fun parseMessage(command: String, payload: ByteArray, network: Network): Message?
}

class BitcoinMessageParser : IMessageParser {

    override var nextParser: IMessageParser? = null

    override fun parseMessage(command: String, payload: ByteArray, network: Network): Message? {
        return when (command) {
            "merkleblock" -> MerkleBlockMessage(payload, network)
            "addr" -> AddrMessage(payload)
            "getaddr" -> GetAddrMessage(payload)
            "getblocks" -> GetBlocksMessage(payload)
            "getdata" -> GetDataMessage(payload)
            "getheaders" -> GetHeadersMessage(payload)
            "inv" -> InvMessage(payload)
            "ping" -> PingMessage(payload)
            "pong" -> PongMessage(payload)
            "verack" -> VerAckMessage(payload)
            "version" -> VersionMessage(payload)
            "tx" -> TransactionMessage(payload)
            else -> nextParser?.parseMessage(command, payload, network)
        }
    }

}
