package io.horizontalsystems.bitcoinkit.network.messages

import io.horizontalsystems.bitcoinkit.io.BitcoinInput
import io.horizontalsystems.bitcoinkit.models.Header
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import java.io.ByteArrayInputStream

/**
 * MerkleBlock Message
 *
 *  Size        Field           Description
 *  ====        =====           ===========
 *  80 bytes    Header          Consists of 6 fields that are hashed to calculate the block hash
 *  VarInt      hashCount       Number of hashes
 *  Variable    hashes          Hashes in depth-first order
 *  VarInt      flagsCount      Number of bytes of flag bits
 *  Variable    flagsBits       Flag bits packed 8 per byte, least significant bit first
 */
class MerkleBlockMessage(payload: ByteArray, network: Network) : Message("merkleblock") {

    lateinit var header: Header
    var txCount: Int = 0

    var hashCount: Int = 0
    var hashes: MutableList<ByteArray> = mutableListOf()

    var flagsCount: Int = 0
    var flags: ByteArray = byteArrayOf()

    private val blockHash: String by lazy {
        HashUtils.toHexStringAsLE(header.hash)
    }

    init {
        BitcoinInput(ByteArrayInputStream(payload)).use { input ->
            header = Header(input, network)
            txCount = input.readInt()

            hashCount = input.readVarInt().toInt()
            repeat(hashCount) {
                hashes.add(input.readBytes(32))
            }

            flagsCount = input.readVarInt().toInt()
            flags = input.readBytes(flagsCount)
        }
    }

    override fun getPayload(): ByteArray {
        return byteArrayOf()
    }

    override fun toString(): String {
        return "MerkleBlockMessage(blockHash=$blockHash, hashesSize=${hashes.size})"
    }
}
