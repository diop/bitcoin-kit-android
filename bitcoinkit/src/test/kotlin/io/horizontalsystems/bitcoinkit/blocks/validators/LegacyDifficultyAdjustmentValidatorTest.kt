package io.horizontalsystems.bitcoinkit.blocks.validators

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.bitcoinkit.core.IStorage
import io.horizontalsystems.bitcoinkit.managers.BlockValidatorHelper
import io.horizontalsystems.bitcoinkit.models.Block
import io.horizontalsystems.bitcoinkit.network.TestNet
import io.horizontalsystems.bitcoinkit.storage.BlockHeader
import io.horizontalsystems.bitcoinkit.utils.HashUtils
import org.junit.Assert
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LegacyDifficultyAdjustmentValidatorTest : Spek({
    lateinit var validator: LegacyDifficultyAdjustmentValidator
    val storage = mock<IStorage>()
    val network = TestNet()
    val blockHelper = BlockValidatorHelper(storage)

    beforeEachTest {
        validator = LegacyDifficultyAdjustmentValidator(network, blockHelper)
    }

    describe("#isBlockValidatable") {
        val block = mock<Block>()
        val previousBlock = mock<Block>()

        it("is true when block is div by 2016") {
            whenever(block.height).thenReturn(4032)
            Assert.assertTrue(validator.isBlockValidatable(block, previousBlock))
        }

        it("is false when block is not div by 2016") {
            whenever(block.height).thenReturn(4033)
            Assert.assertFalse(validator.isBlockValidatable(block, previousBlock))
        }
    }

    describe("#validate") {
        it("passes") {
            val check1 = Block(
                    height = 0, // 536256
                    header = BlockHeader(
                            version = 536870912,
                            previousBlockHeaderHash = HashUtils.toBytesAsLE("00000000000000000000943de85f4495f053ff55f27d135edc61c27990c2eec5"),
                            merkleRoot = HashUtils.toBytesAsLE("167bf70981d49388d07881b1a448ff9b79cf2a32716e45c535345823d8cdd541"),
                            timestamp = 1533980459,
                            bits = 388763047,
                            nonce = 1545867530,
                            hash = byteArrayOf(1)
                    )
            )

            var prevBlock = check1
            val blockHead = BlockHeader(
                    version = 536870912,
                    previousBlockHeaderHash = HashUtils.toBytesAsLE("000000000000000000124a73e879fd66a1b29d1b4b3f1a81de3cbcbe579e21a8"),
                    merkleRoot = HashUtils.toBytesAsLE("7904930640df999005df3b57f9c6f542088af33c3d773dcec2939f55ced359b8"),
                    timestamp = 1535129301,
                    bits = 388763047,
                    nonce = 59591417,
                    hash = byteArrayOf(1)
            )

            for (i in 1 until 2016) {
                prevBlock = Block(blockHead, prevBlock)
            }

            val check2Head = BlockHeader(
                    version = 536870912,
                    previousBlockHeaderHash = HashUtils.toBytesAsLE("0000000000000000001d9d48d93793aaa85b5f6d17c176d4ef905c7e7112b1cf"),
                    merkleRoot = HashUtils.toBytesAsLE("3ad0fa0e8c100db5831ebea7cabf6addae2c372e6e1d84f6243555df5bbfa351"),
                    timestamp = 1535129431,
                    bits = 388618029,
                    nonce = 2367954839,
                    hash = byteArrayOf(1)
            )

            val check2 = Block(check2Head, prevBlock)

            whenever(storage.getBlock(hashHex = any())).thenReturn(check1)
            whenever(storage.getBlock(hashHex = check2.previousBlockHashReversedHex)).thenReturn(prevBlock)

            try {
                validator.validate(check2, prevBlock)
            } catch (e: Exception) {
                Assert.fail(e.message)
            }

        }
    }
})
