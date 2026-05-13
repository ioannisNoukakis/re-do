package me.noukakis.re_do.task.http_fetch

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

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
            if ((first and 0xFE) == 0xFC) return true
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
