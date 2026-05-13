package me.noukakis.re_do.task.http_fetch

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

private const val ULA_MASK = 0xFE
private const  val ULA_PREFIX = 0xFC

internal object HostValidator {

    fun resolve(host: String): Array<InetAddress> = InetAddress.getAllByName(host)

    fun isPrivate(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return true
        if (address.isAnyLocalAddress) return true
        if (address.isLinkLocalAddress) return true
        if (address.isSiteLocalAddress) return true
        if (address.isMulticastAddress) return true
        if (address is Inet6Address) {
            val raw = address.address
            val first = raw[0].toInt() and 0xFF
            // Java's isSiteLocalAddress() only covers the deprecated fec0::/10 range, not fc00::/7.
            // Unique Local Addresses (RFC 4193) use the binary prefix 1111 110x, covering fc00:: through fdff::.
            // The mask 0xFE isolates the top 7 bits, and 0xFC is the expected value for that prefix.
            if ((first and ULA_MASK) == ULA_PREFIX) return true
        }
        return false
    }

    fun firstPrivateAddress(uri: URI): InetAddress? {
        val host = uri.host ?: return null
        val addresses = try {
            resolve(host)
        } catch (_: UnknownHostException) {
            return null
        }
        return addresses.firstOrNull { isPrivate(it) }
    }
}
