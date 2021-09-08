package org.prebid.server.bidder.adform;

import com.iab.openrtb.request.Regs;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} to retrieve data from request.
 */
class AdformRequestUtil {

    /**
     * Retrieves gdpr from regs.ext.gdpr and in case of any exception or invalid values returns empty string.
     */
    String getGdprApplies(Regs regs) {
        final ExtRegs extRegs = regs != null ? regs.getExt() : null;
        final String gdpr = extRegs != null ? Integer.toString(extRegs.getGdpr()) : "";

        return ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0") ? "" : gdpr;
    }

    /**
     * Retrieves consent from user.ext.consent and in case of any exception or invalid values return empty string.
     */
    String getConsent(ExtUser extUser) {
        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.defaultIfNull(gdprConsent, "");
    }

    /**
     * Retrieves eids from user.ext.eids and in case of any exception or invalid values return empty collection.
     */
    String getEids(ExtUser extUser, JacksonMapper mapper) {
        final List<ExtUserEid> eids = extUser != null ? extUser.getEids() : null;
        final Map<String, Map<String, List<Integer>>> eidsMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(eids)) {
            for (ExtUserEid eid : eids) {
                final Map<String, List<Integer>> uidMap = eidsMap.computeIfAbsent(eid.getSource(),
                        ignored -> new HashMap<>());
                for (ExtUserEidUid uid : eid.getUids()) {
                    uidMap.putIfAbsent(uid.getId(), new ArrayList<Integer>());
                    uidMap.get(uid.getId()).add(uid.getAtype());
                }
            }
        }

        final String encodedEids = mapper.encode(eidsMap);

        return ObjectUtils
                .defaultIfNull(Base64.getUrlEncoder().withoutPadding().encodeToString(encodedEids.getBytes()),
                        "");
    }
}
