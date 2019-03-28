package io.horizontalsystems.bitcoinkit.dash

import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListManager
import io.horizontalsystems.bitcoinkit.dash.managers.MasternodeListSyncer
import io.horizontalsystems.bitcoinkit.dash.messages.MasternodeListDiffMessage
import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestMasternodeListDiffTask
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerGroup
import org.junit.Test
import org.mockito.Mockito.mock

class MasterNodeListSyncerTest {

    private val peerGroup = mock(PeerGroup::class.java)
    private val peerTaskFactory = mock(PeerTaskFactory::class.java)
    private val masterNodeListManager = mock(MasternodeListManager::class.java)
    private val syncer = MasternodeListSyncer(peerGroup, peerTaskFactory, masterNodeListManager)

    @Test
    fun sync() {
        val baseBlockHash = byteArrayOf(0, 0, 0)
        val blockHash = byteArrayOf(1, 2, 3)
        val task = mock(RequestMasternodeListDiffTask::class.java)

        whenever(masterNodeListManager.baseBlockHash).thenReturn(baseBlockHash)
        whenever(peerTaskFactory.createRequestMasternodeListDiffTask(baseBlockHash, blockHash)).thenReturn(task)

        syncer.sync(blockHash)

        verify(peerGroup).addTask(task)
    }

    @Test
    fun handleCompletedTask() {
        val task = mock(RequestMasternodeListDiffTask::class.java)
        val peer = mock(Peer::class.java)
        val masternodeListDiffMessage = mock(MasternodeListDiffMessage::class.java)

        whenever(task.masternodeListDiffMessage).thenReturn(masternodeListDiffMessage)

        syncer.handleCompletedTask(peer, task)

        verify(masterNodeListManager).updateList(masternodeListDiffMessage)
    }

    @Test
    fun handleCompletedTask_verificationFails() {
        val task = mock(RequestMasternodeListDiffTask::class.java)
        val newTask = mock(RequestMasternodeListDiffTask::class.java)
        val peer = mock(Peer::class.java)
        val masternodeListDiffMessage = mock(MasternodeListDiffMessage::class.java)
        val verificationException = MasternodeListManager.ValidationError()
        val baseBlockHash = byteArrayOf(1, 2, 3)
        val blockHash = byteArrayOf(4, 5, 6)

        whenever(task.masternodeListDiffMessage).thenReturn(masternodeListDiffMessage)
        whenever(masterNodeListManager.updateList(masternodeListDiffMessage)).thenThrow(verificationException)
        whenever(masternodeListDiffMessage.baseBlockHash).thenReturn(baseBlockHash)
        whenever(masternodeListDiffMessage.blockHash).thenReturn(blockHash)

        whenever(peerTaskFactory.createRequestMasternodeListDiffTask(baseBlockHash, blockHash)).thenReturn(newTask)

        syncer.handleCompletedTask(peer, task)

        verify(peer).close(verificationException)
        verify(peerGroup).addTask(newTask)
    }

}
