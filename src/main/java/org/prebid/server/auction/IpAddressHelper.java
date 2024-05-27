package org.prebid.server.auction;

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringParameters;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.List;

public class IpAddressHelper {

    private static final Logger logger = LoggerFactory.getLogger(IpAddressHelper.class);

    private static final IPAddressStringParameters IP_ADDRESS_VALIDATION_OPTIONS =
            IPAddressString.DEFAULT_VALIDATION_OPTIONS.toBuilder()
                    .allowSingleSegment(false)
                    .allowEmpty(false)
                    .toParams();

    private final IPAddress ipv6AlwaysMaskAddress;
    private final IPAddress ipv6AnonLeftMaskAddress;
    private final List<IPAddress> ipv6LocalNetworkMaskAddresses;

    public IpAddressHelper(int ipv6AlwaysMaskBits, int ipv6AnonLeftMaskBits, List<String> ipv6LocalNetworks) {
        ipv6AlwaysMaskAddress =
                toAddress("::/" + validateIpv6AlwaysMaskBits(ipv6AlwaysMaskBits)).getNetworkMask();
        ipv6AnonLeftMaskAddress =
                toAddress("::/" + validateIpv6AnonLeftMaskBits(ipv6AnonLeftMaskBits)).getNetworkMask();
        ipv6LocalNetworkMaskAddresses = ipv6LocalNetworks.stream()
                .map(this::toAddress)
                .toList();
    }

    public String anonymizeIpv6(String ip) {
        try {
            final IPAddressString ipAddressString = new IPAddressString(ip);
            return ipAddressString.isIPv6()
                    ? ipAddressString.toAddress().mask(ipv6AnonLeftMaskAddress).toCanonicalString()
                    : null;
        } catch (AddressStringException e) {
            logger.debug("Exception occurred while anonymizing IPv6 address: {}", e.getMessage());
            return null;
        }
    }

    public IpAddress toIpAddress(String ip) {
        final IPAddress ipAddress = toIpAddressInternal(ip);

        if (ipAddress == null) {
            return null;
        }

        final IpAddress.IP version;
        if (ipAddress.isIPv4()) {
            version = IpAddress.IP.v4;
        } else if (ipAddress.isIPv6()) {
            version = IpAddress.IP.v6;
        } else {
            return null;
        }

        if (isIpPublic(ipAddress)) {
            final String sanitizedIp = version == IpAddress.IP.v6 ? maskIpv6(ipAddress) : ip;
            return IpAddress.of(sanitizedIp, version);
        }

        return null;
    }

    public String maskIpv4(String ip) {
        if (StringUtils.isBlank(ip) || !InetAddressUtils.isIPv4Address(ip)) {
            return ip;
        }

        String maskedIp = ip;
        final int lastDotIndex = maskedIp.lastIndexOf(".");
        if (lastDotIndex != -1) {
            maskedIp = maskedIp.substring(0, lastDotIndex);
        } else {
            // ip is malformed
            return ip;
        }

        return maskedIp + ".0";
    }

    private String maskIpv6(IPAddress ipAddress) {
        return ipAddress.mask(ipv6AlwaysMaskAddress).toCanonicalString();
    }

    private static int validateIpv6AlwaysMaskBits(int ipv6AlwaysMaskBits) {
        if (ipv6AlwaysMaskBits < 1 || ipv6AlwaysMaskBits > 128) {
            throw new IllegalArgumentException("IPv6 always mask bits should be between 1 and 128 inclusive");
        }

        return ipv6AlwaysMaskBits;
    }

    private static int validateIpv6AnonLeftMaskBits(int ipv6AnonLeftMaskBits) {
        if (ipv6AnonLeftMaskBits < 1
                || ipv6AnonLeftMaskBits > 128
                || (ipv6AnonLeftMaskBits > 32 && ipv6AnonLeftMaskBits < 56)) {

            throw new IllegalArgumentException(
                    "IPv6 anonymize mask bits should be between 1 and 32 or 56 and 128 inclusive");
        }

        return ipv6AnonLeftMaskBits;
    }

    private IPAddress toAddress(String address) {
        try {
            return new IPAddressString(address).toAddress();
        } catch (AddressStringException e) {
            throw new IllegalArgumentException("Unable to process IPv6-related configuration", e);
        }
    }

    private static IPAddress toIpAddressInternal(String ip) {
        try {
            return new IPAddressString(ip, IP_ADDRESS_VALIDATION_OPTIONS).toAddress();
        } catch (AddressStringException e) {
            return null;
        }
    }

    private boolean isIpPublic(IPAddress ipAddress) {
        return ipAddress != null
                && !ipAddress.isLocal()
                && !ipAddress.isLoopback()
                && !ipAddress.isMulticast()
                && !ipAddress.isMax()
                && ipv6LocalNetworkMaskAddresses.stream().noneMatch(network -> network.contains(ipAddress));
    }
}
