package io.horizontalsystems.bitcoinkit.dash

import android.content.Context
import io.horizontalsystems.bitcoinkit.AbstractKit
import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.BitcoinCoreBuilder
import io.horizontalsystems.bitcoinkit.core.hexStringToByteArray
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.messages.DashMessageParser
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.managers.ApiFeeRate
import io.horizontalsystems.bitcoinkit.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.network.MainNetDash
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.TestNetDash
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser

class DashKit : AbstractKit, BitcoinCore.Listener {

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            value?.let { bitcoinCore.addListener(it) }
        }

    private var masterNodeSyncer: MasternodeListSyncer? = null

    constructor(context: Context, words: List<String>, walletId: String = "wallet-id", testMode: Boolean = false) {

        network = if (testMode) TestNetDash() else MainNetDash()

        val paymentAddressParser = PaymentAddressParser("bitcoin", removeScheme = true)

        val addressSelector = BitcoinAddressSelector()

        val resource = if (testMode) "DASH/testnet" else "DASH"

        val apiFeeRate = ApiFeeRate(resource)

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(context)
                .setWords(words)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setApiFeeRate(apiFeeRate)
                .setWalletId(walletId)
                .setPeerSize(2)
                .setNewWallet(true)
                .build()

        extendBitcoin(bitcoinCore)
    }

    private fun extendBitcoin(bitcoinCore: BitcoinCore) {
        bitcoinCore.addListener(this)

        bitcoinCore.addMessageParser(DashMessageParser())

        val masterNodeSyncer = MasternodeListSyncer(bitcoinCore.peerGroup, PeerTaskFactory(), MasternodeListManager())
        bitcoinCore.addPeerTaskHandler(masterNodeSyncer)

        this.masterNodeSyncer = masterNodeSyncer

        val instantSend = InstantSend(bitcoinCore.transactionSyncer)
        bitcoinCore.addInventoryItemsHandler(instantSend)
        bitcoinCore.addPeerTaskHandler(instantSend)
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        if (bitcoinCore.syncState == BitcoinCore.KitState.Synced) {
            masterNodeSyncer?.sync(blockInfo.headerHash.hexStringToByteArray().reversedArray())
        }
    }

    override fun onKitStateUpdate(state: BitcoinCore.KitState) {
        if (state == BitcoinCore.KitState.Synced) {
            bitcoinCore.lastBlockInfo?.let {
                masterNodeSyncer?.sync(it.headerHash.hexStringToByteArray().reversedArray())
            }
        }
    }
}
