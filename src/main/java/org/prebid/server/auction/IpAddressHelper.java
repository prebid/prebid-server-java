package org.prebid.server.auction;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.auction.model.IpAddress;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IpAddressHelper {

    private static final Logger logger = LoggerFactory.getLogger(IpAddressHelper.class);

    public String maskIpv6(String ip) {
        // FIXME
        final int alwaysMaskBits = 64;

        try {
            final IPAddress maskAddress = new IPAddressString(String.format("::/%s", alwaysMaskBits))
                    .toAddress()
                    .getNetworkMask();
            return new IPAddressString(ip).toAddress().mask(maskAddress).toCanonicalString();
        } catch (AddressStringException e) {
            logger.debug("Exception occurred while masking IPv6 address: {0}", e.getMessage());
            return null;
        }
    }

    public IpAddress toIpAddress(String ip) {
        final InetAddress inetAddress = inetAddressByIp(ip);

        final IpAddress.IP version;
        if (inetAddress instanceof Inet4Address) {
            version = IpAddress.IP.v4;
        } else if (inetAddress instanceof Inet6Address) {
            version = IpAddress.IP.v6;
        } else {
            return null;
        }

        if (isIpPublic(inetAddress, ip, version)) {
            final String sanitizedIp = version == IpAddress.IP.v6 ? maskIpv6(ip) : ip;
            return IpAddress.of(sanitizedIp, version);
        }

        return null;
    }

    private static InetAddress inetAddressByIp(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private boolean isIpPublic(InetAddress inetAddress, String ip, IpAddress.IP version) {
        switch (version) {
            case v4:
                return inetAddress != null
                        && !inetAddress.isSiteLocalAddress()
                        && !inetAddress.isLinkLocalAddress()
                        && !inetAddress.isLoopbackAddress();
            case v6:
                // FIXME
                final List<IPAddress> localNetworks = Stream.of("::1/128", "fc00::/7", "fe80::/10")
                        .map(network -> {
                            try {
                                return new IPAddressString(network).toAddress();
                            } catch (AddressStringException e) {
                                throw new IllegalArgumentException("Could not parse IPv6 network address");
                            }
                        })
                        .collect(Collectors.toList());

                try {
                    final IPAddress ipAddress = new IPAddressString(ip).toAddress();
                    return localNetworks.stream().noneMatch(network -> network.contains(ipAddress));
                } catch (AddressStringException e) {
                    logger.debug(
                            "Exception occurred while checking IPv6 address belongs to a local network: {0}",
                            e.getMessage());
                    return false;
                }
            default:
                return false;
        }
    }
}
