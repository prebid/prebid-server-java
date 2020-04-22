package org.prebid.server.privacy.gdpr.model;

import com.iabtcf.utils.BitSetIntIterable;
import com.iabtcf.v2.PublisherRestriction;
import com.iabtcf.v2.RestrictionType;

public class PublisherRestrictionEmpty extends PublisherRestriction {

    public PublisherRestrictionEmpty(int purposeId) {
        super(purposeId, RestrictionType.UNDEFINED, BitSetIntIterable.EMPTY);
    }
}
