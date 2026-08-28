package `in`.pcncloud.hotel.tailscale.embed

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import java.net.InetAddress
import libtailscale.ParcelFileDescriptor

class TailscaleVpnBuilderAdapter(
    private val builder: VpnService.Builder,
) : libtailscale.VPNServiceBuilder {

    override fun addAddress(address: String, prefix: Int) {
        builder.addAddress(address, prefix)
    }

    override fun addDNSServer(server: String) {
        builder.addDnsServer(server)
    }

    override fun addRoute(route: String, prefix: Int) {
        builder.addRoute(route, prefix)
    }

    override fun excludeRoute(route: String, prefix: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inetAddress = InetAddress.getByName(route)
            builder.excludeRoute(IpPrefix(inetAddress, prefix))
        }
    }

    override fun addSearchDomain(domain: String) {
        builder.addSearchDomain(domain)
    }

    override fun establish(): ParcelFileDescriptor? =
        builder.establish()?.let { TailscaleParcelFileDescriptor(it) }

    override fun setMTU(mtu: Int) {
        builder.setMtu(mtu)
    }
}

class TailscaleParcelFileDescriptor(
    private val descriptor: android.os.ParcelFileDescriptor,
) : ParcelFileDescriptor {
    override fun detach(): Int = descriptor.detachFd()
}
