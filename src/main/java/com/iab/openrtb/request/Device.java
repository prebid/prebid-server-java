package com.iab.openrtb.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;

/**
 * This object provides information pertaining to the device through which the
 * user is interacting. Device information includes its hardware, platform,
 * location, and carrier data. The device can refer to a mobile handset,
 * a desktop computer, set top box, or other digital device.
 * <p><em>BEST PRACTICE</em>: There are currently no prominent open source lists
 * for device makes, models, operating systems, or carriers. Exchanges typically
 * use commercial products or other proprietary lists for these attributes.
 * Until suitable open standards are available, exchanges are highly encouraged
 * to publish lists of their device make, model, operating system, and carrier
 * values to bidders.
 * <p><em>BEST PRACTICE</em>: Proper device IP detection in mobile is not
 * straightforward. Typically it involves starting at the left of the
 * {@code x-forwarded-for} header, skipping private carrier networks
 * (e.g., 10.x.x.x or 192.x.x.x), and possibly scanning for known carrier IP
 * ranges. Exchanges are urged to research and implement this feature carefully
 * when presenting device IP values to bidders.
 */
@Builder(toBuilder = true)
@Value
public class Device {

    /** Browser user agent string. (recommended) */
    String ua;

    /**
     * Location of the device assumed to be the user’s current location defined
     * by a Geo object (Section 3.2.19).
     * (recommended)
     */
    Geo geo;

    /**
     * Standard “Do Not Track” flag as set in the header by the browser,
     * where 0 = tracking is unrestricted, 1 = do not track.
     * (recommended)
     */
    Integer dnt;

    /**
     * “Limit Ad Tracking” signal commercially endorsed (e.g., iOS, Android),
     * where 0 = tracking is unrestricted, 1 = tracking must be limited per
     * commercial guidelines.
     * (recommended)
     */
    Integer lmt;

    /**
     * IPv4 address closest to device.
     * (recommended)
     */
    String ip;

    /** IP address closest to device as IPv6. */
    String ipv6;

    /** The general type of device. Refer to List 5.21. */
    Integer devicetype;

    /** Device make (e.g., “Apple”). */
    String make;

    /** Device model (e.g., “iPhone”). */
    String model;

    /** Device operating system (e.g., “iOS”). */
    String os;

    /** Device operating system version (e.g., “3.1.2”). */
    String osv;

    /** Hardware version of the device (e.g., “5S” for iPhone 5S). */
    String hwv;

    /** Physical height of the screen in pixels. */
    Integer h;

    /** Physical width of the screen in pixels. */
    Integer w;

    /** Screen size as pixels per linear inch. */
    Integer ppi;

    /** The ratio of physical pixels to device independent pixels. */
    BigDecimal pxratio;

    /** Support for JavaScript, where 0 = no, 1 = yes. */
    Integer js;

    /**
     * Indicates if the geolocation API will be available to JavaScript code
     * running in the banner, where 0 = no, 1 = yes.
     */
    Integer geofetch;

    /** Version of Flash supported by the browser. */
    String flashver;

    /** Browser language using ISO-639-1-alpha-2. */
    String language;

    /**
     * Carrier or ISP (e.g., “VERIZON”) using exchange curated string names
     * which should be published to bidders a priori.
     */
    String carrier;

    /**
     * Mobile carrier as the concatenated MCC-MNC code (e.g., “310-005”
     * identifies Verizon Wireless CDMA in the USA). Refer to
     * https://en.wikipedia.org/wiki/Mobile_country_code for further examples.
     * Note that the dash between the MCC and MNC parts is required to remove
     * parsing ambiguity.
     */
    String mccmnc;

    /** Network connection type. Refer to List 5.22. */
    Integer connectiontype;

    /** ID sanctioned for advertiser use in the clear (i.e., not hashed). */
    String ifa;

    /** Hardware device ID (e.g., IMEI); hashed via SHA1. */
    String didsha1;

    /** Hardware device ID (e.g., IMEI); hashed via MD5. */
    String didmd5;

    /** Platform device ID (e.g., Android ID); hashed via SHA1. */
    String dpidsha1;

    /** Platform device ID (e.g., Android ID); hashed via MD5. */
    String dpidmd5;

    /** MAC address of the device; hashed via SHA1. */
    String macsha1;

    /** MAC address of the device; hashed via MD5. */
    String macmd5;

    /** Placeholder for exchange-specific extensions to OpenRTB. */
    ExtDevice ext;
}
