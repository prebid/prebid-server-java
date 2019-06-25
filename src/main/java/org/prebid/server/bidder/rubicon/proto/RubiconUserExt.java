package org.prebid.server.bidder.rubicon.proto;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;

import java.util.List;
import java.util.function.Function;

@Builder
@Value
public class RubiconUserExt {

    String consent;

    ExtUserDigiTrust digitrust;

    List<ExtUserEid> eids;

    List<ExtUserTpIdRubicon> tpid;

    RubiconUserExtRp rp;

    public static RubiconUserExt.RubiconUserExtBuilder builderFrom(ExtUser extUser) {
        return RubiconUserExt.builder()
                .consent(getIfNotNull(extUser, ExtUser::getConsent))
                .digitrust(getIfNotNull(extUser, ExtUser::getDigitrust))
                .eids(getIfNotNull(extUser, ExtUser::getEids));
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
