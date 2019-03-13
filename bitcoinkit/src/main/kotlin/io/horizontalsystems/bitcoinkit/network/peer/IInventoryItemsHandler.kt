package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.models.InventoryItem

interface IInventoryItemsHandler {
    fun handleInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>)
}
