package org.prebid.server.functional.model.request.auction;

import groovy.transform.ToString
import lombok.EqualsAndHashCode
import org.prebid.server.functional.util.PBSUtils;

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class DsaTransparency {

    String domain;
    List<Integer> params;

    static DsaTransparency getDefaultRegsDsaTransparency() {
        new DsaTransparency("domain": PBSUtils.randomString);
    }

}

