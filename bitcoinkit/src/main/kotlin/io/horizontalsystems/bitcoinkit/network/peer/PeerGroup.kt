package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.blocks.BlockSyncer
import io.horizontalsystems.bitcoinkit.core.ISyncStateListener
import io.horizontalsystems.bitcoinkit.crypto.BloomFilter
import io.horizontalsystems.bitcoinkit.managers.BloomFilterManager
import io.horizontalsystems.bitcoinkit.managers.ConnectionManager
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.models.MerkleBlock
import io.horizontalsystems.bitcoinkit.models.NetworkAddress
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.peer.task.GetBlockHashesTask
import io.horizontalsystems.bitcoinkit.network.peer.task.GetMerkleBlocksTask
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.logging.Logger

class PeerGroup(
        private val hostManager: PeerAddressManager,
        private val bloomFilterManager: BloomFilterManager,
        private val network: Network,
        private val syncStateListener: ISyncStateListener,
        private val peerSize: Int) : Thread(), Peer.Listener, BloomFilterManager.Listener {

    var blockSyncer: BlockSyncer? = null
    var connectionManager: ConnectionManager? = null

    var inventoryItemsHandler: IInventoryItemsHandler? = null
    var peerTaskHandler: IPeerTaskHandler? = null

    @Volatile
    private var running = false
    private val logger = Logger.getLogger("PeerGroup")
    private val peersQueue = Executors.newSingleThreadExecutor()
    private val peerManager = PeerManager()

    private val taskQueue: BlockingQueue<PeerTask> = ArrayBlockingQueue(10)

    init {
        bloomFilterManager.listener = this
    }

    fun someReadyPeers(): List<Peer> {
        return peerManager.someReadyPeers()
    }
    @Throws
    fun checkPeersSynced() {
        if (peerManager.peersCount() < 1) {
            throw Error("No peers connected")
        }

        if (peerManager.nonSyncedPeer() != null) {
            throw Error("Peers not synced yet")
        }
    }

    fun close() {
        running = false

        interrupt()
        try {
            join(5000)
        } catch (e: InterruptedException) {
        }
    }

    //
    // Thread implementations
    //
    override fun run() {
        running = true

        syncStateListener.onSyncStart()
        blockSyncer?.prepareForDownload()

        while (running) {
            if (connectionManager?.isOnline == true && peerManager.peersCount() < peerSize) {
                startConnection()
            }

            try {
                Thread.sleep(2000L)
            } catch (e: InterruptedException) {
                break
            }
        }

        syncStateListener.onSyncStop()
        blockSyncer = null
        logger.info("Closing all peer connections...")

        peerManager.disconnectAll()
    }

    //
    // PeerListener implementations
    //
    override fun onConnect(peer: Peer) {
        peerManager.add(peer)

        bloomFilterManager.bloomFilter?.let {
            peer.filterLoad(it)
        }

        assignNextSyncPeer()
    }

    override fun onReady(peer: Peer) {
        peersQueue.execute {
            downloadBlockchain()

//            todo check if peer is not syncPeer
            taskQueue.poll()?.let {
                peer.addTask(it)
            }
        }
    }

    override fun onDisconnect(peer: Peer, e: Exception?) {
        peerManager.remove(peer)

        if (e == null) {
            logger.info("Peer ${peer.host} disconnected.")
            hostManager.markSuccess(peer.host)
        } else {
            logger.warning("Peer ${peer.host} disconnected with error ${e.message}.")
            hostManager.markFailed(peer.host)
        }

        if (peerManager.isSyncPeer(peer)) {
            peerManager.syncPeer = null
            blockSyncer?.downloadFailed()
            assignNextSyncPeer()
        }
    }

    override fun onReceiveInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>) {
        val blockHashes = mutableListOf<ByteArray>()

        inventoryItems.forEach { item ->
            when (item.type) {
                InventoryItem.MSG_BLOCK -> if (blockSyncer?.shouldRequest(item.hash) == true) {
                    blockHashes.add(item.hash)
                }
            }
        }

        if (blockHashes.isNotEmpty() && peer.synced) {
            peer.synced = false
            peer.blockHashesSynced = false
            assignNextSyncPeer()
        }

        inventoryItemsHandler?.handleInventoryItems(peer, inventoryItems)
    }

    override fun onReceiveMerkleBlock(peer: Peer, merkleBlock: MerkleBlock) {
        try {
            blockSyncer?.handleMerkleBlock(merkleBlock, peer.announcedLastBlockHeight)
        } catch (e: Exception) {
            peer.close(e)
        }
    }

    override fun onReceiveAddress(addrs: Array<NetworkAddress>) {
        val peerIps = mutableListOf<String>()
        for (address in addrs) {
            val addr = InetAddress.getByAddress(address.address)
            peerIps.add(addr.hostAddress)
        }

        hostManager.addIps(peerIps.toTypedArray())
    }

    override fun onTaskComplete(peer: Peer, task: PeerTask) {
        when (task) {
            is GetBlockHashesTask -> {
                if (task.blockHashes.isEmpty()) {
                    peer.blockHashesSynced = true
                } else {
                    blockSyncer?.addBlockHashes(task.blockHashes)
                }
            }
            is GetMerkleBlocksTask -> {
                blockSyncer?.downloadIterationCompleted()
            }
            else -> peerTaskHandler?.handleCompletedTask(peer, task)
        }
    }

    //
    // BloomFilterManager implementations
    //
    override fun onFilterUpdated(bloomFilter: BloomFilter) {
        peerManager.connected().forEach { peer ->
            peer.filterLoad(bloomFilter)
        }
    }

    //
    // Private methods
    //
    private fun startConnection() {
        logger.info("Try open new peer connection...")
        val ip = hostManager.getIp()
        if (ip != null) {
            logger.info("Try open new peer connection to $ip...")
            val peer = Peer(ip, network, this)
            peer.localBestBlockHeight = blockSyncer?.localDownloadedBestBlockHeight ?: 0
            peer.start()
        } else {
            logger.info("No peers found yet.")
        }
    }

    var peersSyncedListener: IAllPeersSyncedListener? = null

    private fun assignNextSyncPeer() {
        peersQueue.execute {
            if (peerManager.syncPeer == null) {
                val nonSyncedPeer = peerManager.nonSyncedPeer()
                if (nonSyncedPeer == null) {
                    peersSyncedListener?.onAllPeersSynced()
                } else {
                    peerManager.syncPeer = nonSyncedPeer
                    blockSyncer?.downloadStarted()

                    logger.info("Start syncing peer ${nonSyncedPeer.host}")

                    downloadBlockchain()
                }
            }
        }
    }

    private fun downloadBlockchain() {
        peerManager.syncPeer?.let { syncPeer ->
            blockSyncer?.let { blockSyncer ->

                val blockHashes = blockSyncer.getBlockHashes()
                if (blockHashes.isEmpty()) {
                    syncPeer.synced = syncPeer.blockHashesSynced
                } else {
                    syncPeer.addTask(GetMerkleBlocksTask(blockHashes))
                }

                if (!syncPeer.blockHashesSynced) {
                    val expectedHashesMinCount = Math.max(syncPeer.announcedLastBlockHeight - blockSyncer.localKnownBestBlockHeight, 0)
                    syncPeer.addTask(GetBlockHashesTask(blockSyncer.getBlockLocatorHashes(syncPeer.announcedLastBlockHeight), expectedHashesMinCount))
                }

                if (syncPeer.synced) {
                    blockSyncer.downloadCompleted()
                    syncStateListener.onSyncFinish()
                    syncPeer.sendMempoolMessage()
                    logger.info("Peer synced ${syncPeer.host}")
                    peerManager.syncPeer = null
                    assignNextSyncPeer()
                }
            }
        }
    }

    fun isRequestingInventory(hash: ByteArray): Boolean {
        return peerManager.connected().any { peer -> peer.isRequestingInventory(hash) }
    }

    fun addTask(peerTask: PeerTask) {
        // todo find better solution
        val peer = peerManager.someReadyPeers().firstOrNull()

        if (peer == null) {
            taskQueue.add(peerTask)
        } else {
            peer.addTask(peerTask)
        }

    }

    //
    // PeerGroup Exceptions
    //
    class Error(message: String) : Exception(message)
}
