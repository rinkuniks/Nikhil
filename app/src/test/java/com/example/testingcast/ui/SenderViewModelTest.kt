package com.example.testingcast.ui

import android.app.Activity
import android.content.Intent
import com.example.testingcast.capture.CaptureServiceController
import com.example.testingcast.capture.CaptureStatus
import com.example.testingcast.capture.ResolutionOption
import com.example.testingcast.capture.StreamQuality
import com.example.testingcast.network.model.ConnectionState
import com.example.testingcast.network.model.DiscoveredDevice
import com.example.testingcast.network.protocol.StreamPacket
import com.example.testingcast.network.repository.NetworkRepository
import com.example.testingcast.ui.sender.SenderMessage
import com.example.testingcast.ui.sender.SenderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeRepository : NetworkRepository {
    override val discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val incomingPackets: Flow<StreamPacket> = emptyFlow()
    val connectRequests = mutableListOf<DiscoveredDevice>()
    var connectResult = true
    var nsdDiscovery = false
    var p2pDiscovery = false
    var disconnectCalls = 0

    override fun startAdvertising(deviceName: String, port: Int) = Unit
    override fun stopAdvertising() = Unit
    override fun startDiscovery() { nsdDiscovery = true }
    override fun stopDiscovery() { nsdDiscovery = false }
    override fun startWifiP2pDiscovery() { p2pDiscovery = true }
    override fun stopWifiP2pDiscovery() { p2pDiscovery = false }
    override suspend fun connectToDevice(device: DiscoveredDevice): Boolean {
        connectRequests += device
        return connectResult
    }
    override fun disconnect() { disconnectCalls++ }
    override fun startServer(port: Int) = Unit
    override fun stopServer() = Unit
    override suspend fun startStreaming() = true
    override suspend fun stopStreaming() = true
    override suspend fun sendPacket(packet: StreamPacket) = true
    override fun release() = Unit
}

private class FakeController : CaptureServiceController {
    val starts = mutableListOf<StreamQuality>()
    var stops = 0
    override fun start(resultCode: Int, data: Intent, quality: StreamQuality) { starts += quality }
    override fun stop() { stops++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SenderViewModelTest {

    private val repository = FakeRepository()
    private val captureStatus = MutableStateFlow<CaptureStatus>(CaptureStatus.Idle)
    private val controller = FakeController()
    private lateinit var viewModel: SenderViewModel
    private val device = DiscoveredDevice(id = "tv", name = "TV", host = "192.168.1.20")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SenderViewModel(repository, captureStatus, controller)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start discovery enables wifi direct only when permitted`() {
        viewModel.startDiscovery(includeWifiDirect = false)

        assertTrue(repository.nsdDiscovery)
        assertFalse(repository.p2pDiscovery)
        assertTrue(viewModel.uiState.value.isDiscovering)
    }

    @Test
    fun `manual connect rejects invalid host and port`() {
        viewModel.onManualHostChange("999.1.1.1")
        viewModel.connectManual()
        assertEquals(SenderMessage.INVALID_HOST, viewModel.uiState.value.message)

        viewModel.consumeMessage()
        viewModel.onManualHostChange("192.168.43.1")
        viewModel.onManualPortChange("0")
        viewModel.connectManual()
        assertEquals(SenderMessage.INVALID_PORT, viewModel.uiState.value.message)
        assertTrue(repository.connectRequests.isEmpty())
    }

    @Test
    fun `manual connect sends trimmed host and port to repository`() {
        viewModel.onManualHostChange(" 192.168.43.1 ")
        viewModel.onManualPortChange("9000")

        viewModel.connectManual()

        val request = repository.connectRequests.single()
        assertEquals("192.168.43.1", request.host)
        assertEquals(9000, request.port)
    }

    @Test
    fun `failed connection surfaces message`() {
        repository.connectResult = false

        viewModel.connect(device)

        assertEquals(SenderMessage.CONNECTION_FAILED, viewModel.uiState.value.message)
    }

    @Test
    fun `cannot start mirroring while disconnected`() {
        assertFalse(viewModel.canStartMirroring())
        assertEquals(SenderMessage.NOT_CONNECTED, viewModel.uiState.value.message)
    }

    @Test
    fun `projection result starts capture with selected quality`() {
        repository.connectionState.value = ConnectionState.Connected(device)
        val quality = StreamQuality(ResolutionOption.FHD_1080, 8_000, 60)
        viewModel.updateQuality(quality)

        assertTrue(viewModel.canStartMirroring())
        viewModel.onProjectionResult(Activity.RESULT_OK, Intent())

        assertEquals(listOf(quality), controller.starts)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `denied projection does not start capture`() {
        viewModel.onProjectionResult(Activity.RESULT_CANCELED, null)

        assertTrue(controller.starts.isEmpty())
        assertEquals(SenderMessage.CAPTURE_DENIED, viewModel.uiState.value.message)
    }

    @Test
    fun `disconnect while mirroring stops capture first`() {
        captureStatus.value = CaptureStatus.Active(720, 1600, audioEnabled = true)

        viewModel.disconnect()

        assertEquals(1, controller.stops)
        assertEquals(1, repository.disconnectCalls)
    }
}
