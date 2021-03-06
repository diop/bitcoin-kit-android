import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.bitcoinkit.core.toHexString
import io.horizontalsystems.bitcoinkit.extensions.hexToByteArray
import io.horizontalsystems.bitcoinkit.models.PublicKey
import io.horizontalsystems.bitcoinkit.models.Transaction
import io.horizontalsystems.bitcoinkit.models.TransactionInput
import io.horizontalsystems.bitcoinkit.models.TransactionOutput
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.storage.InputToSign
import io.horizontalsystems.bitcoinkit.transactions.builder.InputSigner
import io.horizontalsystems.bitcoinkit.transactions.scripts.ScriptType
import io.horizontalsystems.hdwalletkit.HDKey
import io.horizontalsystems.hdwalletkit.HDWallet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InputSignerTest : Spek({

    lateinit var inputSigner: InputSigner

    val publicKey = mock(PublicKey::class.java)
    val inputToSign = mock(InputToSign::class.java)
    val transactionOutput = mock(TransactionOutput::class.java)
    val transactionInput = mock(TransactionInput::class.java)
    val transaction = mock(Transaction::class.java)

    val network = mock(Network::class.java)
    val hdWallet = mock(HDWallet::class.java)
    val privateKey = mock(HDKey::class.java)

    val derEncodedSignature = "abc".hexToByteArray()

    beforeEachTest {
        whenever(inputToSign.previousOutputPublicKey).thenReturn(publicKey)

        whenever(publicKey.publicKey).thenReturn(byteArrayOf(1, 2, 3))
        whenever(privateKey.createSignature(any())).thenReturn(derEncodedSignature)
        whenever(hdWallet.privateKey(any(), any(), anyBoolean())).thenReturn(privateKey)

        inputSigner = InputSigner(hdWallet, network)
    }

    describe("when no private key") {
        beforeEach {
            whenever(hdWallet.privateKey(any(), any(), anyBoolean())).thenReturn(null)
        }

        it("throws an exception NoPrivateKey") {
            assertThrows<InputSigner.Error.NoPrivateKey> {
                inputSigner.sigScriptData(transaction, listOf(inputToSign), listOf(transactionOutput), 0)
            }
        }
    }

    describe("when private key exist") {
        val lockingScript = "76a914e4de5d630c5cacd7af96418a8f35c411c8ff3c0688ac".hexToByteArray()
        val expectedSignature = derEncodedSignature.toHexString() + "01"

        beforeEach {
            whenever(hdWallet.privateKey(any(), any(), anyBoolean())).thenReturn(privateKey)

            whenever(transactionOutput.lockingScript).thenReturn(lockingScript)
            whenever(transactionOutput.transactionHashReversedHex).thenReturn("abc")

            whenever(inputToSign.previousOutput).thenReturn(transactionOutput)
            whenever(inputToSign.input).thenReturn(transactionInput)
        }

        it("signs data") {
            val resultSignature = inputSigner.sigScriptData(transaction, listOf(inputToSign), listOf(transactionOutput), 0)

            assertEquals(2, resultSignature.size)
            assertEquals(expectedSignature, resultSignature[0].toHexString())
            assertEquals(inputToSign.previousOutputPublicKey.publicKey, resultSignature[1])
        }

        it("signs P2PK") {
            whenever(transactionOutput.scriptType).thenReturn(ScriptType.P2PK)
            val resultSignature = inputSigner.sigScriptData(transaction, listOf(inputToSign), listOf(transactionOutput), 0)

            assertEquals(1, resultSignature.size)
            assertEquals(expectedSignature, resultSignature[0].toHexString())
        }

        it("signs P2WPKH") {
            whenever(transactionOutput.scriptType).thenReturn(ScriptType.P2WPKH)
            whenever(transactionOutput.keyHash).thenReturn(byteArrayOf(1, 2, 3))

            val resultSignature = inputSigner.sigScriptData(transaction, listOf(inputToSign), listOf(transactionOutput), 0)

            assertEquals(2, resultSignature.size)
            assertEquals(expectedSignature, resultSignature[0].toHexString())
            assertEquals(inputToSign.previousOutputPublicKey.publicKey, resultSignature[1])
        }
    }
})
