package com.sonostv.sonos

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Finds a Sonos player on the local network. SSDP multicast is tried first; if the network
 * blocks multicast (common on some TV setups) we fall back to scanning the local /24 for
 * anything listening on the Sonos control port.
 */
class SonosDiscovery(private val context: Context) {

    suspend fun findAnyPlayer(): String? =
        discoverViaSsdp() ?: scanLocalSubnet()

    suspend fun discoverViaSsdp(timeoutMs: Int = 2500): String? = withContext(Dispatchers.IO) {
        val multicastLock = runCatching {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifi?.createMulticastLock("sonos-ssdp")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                socket.broadcast = true

                val message = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 1\r\n")
                    append("ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n")
                    append("\r\n")
                }.toByteArray()

                val target = InetAddress.getByName(SSDP_ADDRESS)
                val deadline = System.currentTimeMillis() + timeoutMs
                val buffer = ByteArray(2048)

                while (System.currentTimeMillis() < deadline) {
                    runCatching {
                        socket.send(DatagramPacket(message, message.size, target, SSDP_PORT))
                    }

                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                        val response = String(packet.data, 0, packet.length)
                        if (response.contains("ZonePlayer", ignoreCase = true)) {
                            return@withContext packet.address.hostAddress
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { multicastLock?.release() }
        }
    }

    suspend fun scanLocalSubnet(): String? {
        val localAddress = localIpv4Address() ?: return null
        val prefix = localAddress.substringBeforeLast('.')

        return coroutineScope {
            // Scan in batches so we never hold hundreds of sockets open at once.
            (1..254).chunked(32).firstNotNullOfOrNull { batch ->
                batch
                    .map { suffix ->
                        async(Dispatchers.IO) {
                            val host = "$prefix.$suffix"
                            if (isSonosPlayer(host)) host else null
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .firstOrNull()
            }
        }
    }

    private fun isSonosPlayer(host: String): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, SONOS_PORT), 350)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun localIpv4Address(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
    }
}
