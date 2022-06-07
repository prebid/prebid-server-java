package org.prebid.server.auction.versionconverter.up;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BidRequestOrtb25To26Converter implements BidRequestOrtbVersionConverter {

    private static final JsonPointer IMP_EXT_PREBID_REWARDED = JsonPointer.valueOf("/prebid/is_rewarded_inventory");

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        return moveBidRequestData(bidRequest);
    }

    private static BidRequest moveBidRequestData(BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        final List<Imp> modifiedImps = moveImpsData(imps);

        final Source source = bidRequest.getSource();
        final Source modifiedSource = moveSourceData(source);

        final Regs regs = bidRequest.getRegs();
        final Regs modifiedRegs = moveRegsData(regs);

        final User user = bidRequest.getUser();
        final User modifiedUser = moveUserData(user);

        return ObjectUtils.anyNotNull(modifiedImps, modifiedSource, modifiedRegs, modifiedUser)
                ? bidRequest.toBuilder()
                .imp(modifiedImps != null ? modifiedImps : imps)
                .source(modifiedSource != null ? modifiedSource : source)
                .regs(modifiedRegs != null ? modifiedRegs : regs)
                .user(modifiedUser != null ? modifiedUser : user)
                .build()
                : bidRequest;
    }

    private static List<Imp> moveImpsData(List<Imp> imps) {
        final List<Imp> modifiedImps = CollectionUtils.emptyIfNull(imps).stream()
                .map(BidRequestOrtb25To26Converter::moveImpData)
                .collect(Collectors.toList());

        if (modifiedImps.stream().allMatch(Objects::isNull)) {
            return null;
        }

        return IntStream.range(0, imps.size())
                .mapToObj(i -> ObjectUtils.defaultIfNull(modifiedImps.get(i), imps.get(i)))
                .collect(Collectors.toList());
    }

    private static Imp moveImpData(Imp imp) {
        if (imp == null) {
            return null;
        }

        final Integer rewarded = resolveImpRewarded(imp);

        return rewarded != null
                ? imp.toBuilder()
                .rwdd(rewarded)
                .build()
                : null;
    }

    private static Integer resolveImpRewarded(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        if (imp.getRwdd() != null || impExt == null || impExt.isEmpty()) {
            return null;
        }

        final JsonNode rewardedNode = impExt.at(IMP_EXT_PREBID_REWARDED);
        return !rewardedNode.isMissingNode()
                ? rewardedNode.asInt()
                : null;
    }

    private static Source moveSourceData(Source source) {
        if (source == null || source.getSchain() != null) {
            return null;
        }

        final SupplyChain extSupplyChain = ObjectUtil.getIfNotNull(source.getExt(), ExtSource::getSchain);
        return extSupplyChain != null
                ? source.toBuilder()
                .schain(extSupplyChain)
                .build()
                : null;
    }

    private static Regs moveRegsData(Regs regs) {
        if (regs == null) {
            return null;
        }

        final ExtRegs regsExt = regs.getExt();
        final boolean regsExtIsNotNull = regsExt != null;

        final Integer gdpr = regs.getGdpr();
        final Integer resolvedGdpr = gdpr == null && regsExtIsNotNull
                ? regsExt.getGdpr()
                : null;

        final String usPrivacy = regs.getUsPrivacy();
        final String resolvedUsPrivacy = usPrivacy == null && regsExtIsNotNull
                ? regsExt.getUsPrivacy()
                : null;

        return ObjectUtils.anyNotNull(resolvedGdpr, resolvedUsPrivacy)
                ? regs.toBuilder()
                .gdpr(resolvedGdpr != null ? resolvedGdpr : gdpr)
                .usPrivacy(resolvedUsPrivacy != null ? resolvedUsPrivacy : usPrivacy)
                .build()
                : null;
    }

    private static User moveUserData(User user) {
        if (user == null) {
            return null;
        }

        final ExtUser userExt = user.getExt();
        final boolean userExtIsNotNull = userExt != null;

        final String consent = user.getConsent();
        final String resolvedConsent = consent == null && userExtIsNotNull
                ? userExt.getConsent()
                : null;

        final List<Eid> eids = user.getEids();
        final List<Eid> resolvedEids = CollectionUtils.isEmpty(eids) && userExtIsNotNull
                ? userExt.getEids()
                : null;

        return ObjectUtils.anyNotNull(resolvedConsent, resolvedEids)
                ? user.toBuilder()
                .consent(resolvedConsent != null ? resolvedConsent : consent)
                .eids(resolvedEids != null ? resolvedEids : eids)
                .build()
                : null;
    }
}
