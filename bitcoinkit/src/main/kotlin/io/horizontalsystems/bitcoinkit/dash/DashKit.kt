package io.horizontalsystems.bitcoinkit.dash

import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.bitcoinkit.core.hexStringToByteArray
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.messages.DashMessageParser
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.models.BlockInfo

class DashKit : BitcoinKit.Listener {

    private var masterNodeSyncer: MasternodeListSyncer? = null

    fun extendBitcoin(bitcoinKit: BitcoinKit) {
        bitcoinKit.addListener(this)

        bitcoinKit.addMessageParser(DashMessageParser())

        val masterNodeSyncer = MasternodeListSyncer(bitcoinKit.peerGroup, PeerTaskFactory(), MasternodeListManager())
        bitcoinKit.addPeerTaskHandler(masterNodeSyncer)

        this.masterNodeSyncer = masterNodeSyncer

        val instantSend = InstantSend(bitcoinKit.transactionSyncer)
        bitcoinKit.addInventoryItemsHandler(instantSend)
        bitcoinKit.addPeerTaskHandler(instantSend)
    }

    override fun onLastBlockInfoUpdate(bitcoinKit: BitcoinKit, blockInfo: BlockInfo) {
        if (bitcoinKit.syncState == BitcoinKit.KitState.Synced) {
            masterNodeSyncer?.sync(blockInfo.headerHash.hexStringToByteArray().reversedArray())
        }
    }

    override fun onKitStateUpdate(bitcoinKit: BitcoinKit, state: BitcoinKit.KitState) {
        if (state == BitcoinKit.KitState.Synced) {
            bitcoinKit.lastBlockInfo?.let {
                masterNodeSyncer?.sync(it.headerHash.hexStringToByteArray().reversedArray())
            }
        }
    }
}
