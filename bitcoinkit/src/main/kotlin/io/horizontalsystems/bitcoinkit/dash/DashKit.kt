package io.horizontalsystems.bitcoinkit.dash

import io.horizontalsystems.bitcoinkit.BitcoinCore
import io.horizontalsystems.bitcoinkit.core.hexStringToByteArray
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.messages.DashMessageParser
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.models.BlockInfo

class DashKit : BitcoinCore.Listener {

    private var masterNodeSyncer: MasternodeListSyncer? = null
    private lateinit var bitcoinCore: BitcoinCore

    fun extendBitcoin(bitcoinCore: BitcoinCore) {
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
