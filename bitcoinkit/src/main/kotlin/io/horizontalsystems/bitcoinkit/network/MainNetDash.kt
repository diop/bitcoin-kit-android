package io.horizontalsystems.bitcoinkit.network

import io.horizontalsystems.bitcoinkit.blocks.validators.BlockValidator
import io.horizontalsystems.bitcoinkit.models.Block
import io.horizontalsystems.bitcoinkit.models.Header
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import io.horizontalsystems.bitcoinkit.utils.X11Hash

class MainNetDash : Network() {

    override val protocolVersion = 70213

    override var port: Int = 9999

    override var magic: Long = 0xbd6b0cbf
    override var bip32HeaderPub: Int = 0x0488B21E   // The 4 byte header that serializes in base58 to "xpub".
    override var bip32HeaderPriv: Int = 0x0488ADE4  // The 4 byte header that serializes in base58 to "xprv"
    override var addressVersion: Int = 76
    override var addressSegwitHrp: String = "bc"
    override var addressScriptVersion: Int = 16
    override var coinType: Int = 5

    override val maxBlockSize = 1_000_000

    override var dnsSeeds: Array<String> = arrayOf(
            "dnsseed.dash.org",
            "dnsseed.dashdot.io",
            "dnsseed.masternode.io"
    )

    override val checkpointBlock = Block(Header(
            536870912,
            HashUtils.toBytesAsLE("000000000000000992e45d7b6d5204e40b24474db7c107e7b1e4884f3e76462c"),
            HashUtils.toBytesAsLE("61694834cfd431c70975645849caff2e1bfb4c487706cf217129fd4371cd7a79"),
            1551689319L,
            0x193f7bf8,
            2813674015,
            this
    ), 1030968)

    override val blockValidator = BlockValidator(this)

    override fun validateBlock(block: Block, previousBlock: Block) {
//        TODO
//        blockValidator.validate(block, previousBlock)
    }

    override fun generateBlockHeaderHash(data: ByteArray): ByteArray {
        return X11Hash.x11(data)
    }
}
