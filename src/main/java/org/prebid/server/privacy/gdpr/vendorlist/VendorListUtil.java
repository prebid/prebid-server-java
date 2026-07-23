package org.prebid.server.privacy.gdpr.vendorlist;

import org.apache.commons.collections4.MapUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;

public class VendorListUtil {

    private static final Logger logger = LoggerFactory.getLogger(VendorListUtil.class);

    private VendorListUtil() {
    }

    public static VendorList parseVendorList(String content, JacksonMapper mapper) {
        try {
            return mapper.mapper().readValue(content, VendorList.class);
        } catch (IOException e) {
            final String message = "Cannot parse vendor list from: " + content;

            logger.error(message, e);
            throw new PreBidException(message, e);
        }
    }

    public static boolean vendorListIsValid(VendorList vendorList) {
        return vendorList.getVendorListVersion() != null
                && vendorList.getLastUpdated() != null
                && MapUtils.isNotEmpty(vendorList.getVendors())
                && vendorsAreValid(vendorList.getVendors().values());
    }

    private static boolean vendorsAreValid(Collection<Vendor> vendors) {
        return vendors.stream()
                .allMatch(vendor -> vendor != null
                        && vendor.getId() != null
                        && vendor.getPurposes() != null
                        && vendor.getLegIntPurposes() != null
                        && vendor.getFlexiblePurposes() != null
                        && vendor.getSpecialPurposes() != null
                        && vendor.getFeatures() != null
                        && vendor.getSpecialFeatures() != null);
    }

    public static boolean vendorIsDeletedAt(Vendor vendor, Instant now) {
        final Instant deletedDate = vendor.getDeletedDate();
        return deletedDate != null && deletedDate.isBefore(now);
    }
}
