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
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class BidRequestOrtb25To26Converter implements BidRequestOrtbVersionConverter {

    private static final String PREBID_FIELD = "prebid";
    private static final String IS_REWARDED_INVENTORY_FIELD = "is_rewarded_inventory";
    private static final JsonPointer IMP_EXT_PREBID_REWARDED = JsonPointer.valueOf(
            "/" + PREBID_FIELD + "/" + IS_REWARDED_INVENTORY_FIELD);

    @Override
    public BidRequest convert(BidRequest bidRequest) {
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
                .toList();

        if (modifiedImps.stream().allMatch(Objects::isNull)) {
            return null;
        }

        return IntStream.range(0, imps.size())
                .mapToObj(i -> ObjectUtils.defaultIfNull(modifiedImps.get(i), imps.get(i)))
                .toList();
    }

    private static Imp moveImpData(Imp imp) {
        if (imp == null) {
            return null;
        }

        final ObjectNode extImp = imp.getExt();
        if (extImp == null) {
            return null;
        }

        final Integer rewarded = imp.getRwdd();
        final Integer resolvedRewarded = resolveImpRewarded(rewarded, extImp);

        final ObjectNode resolvedExtImp = resolveImpExt(extImp);

        return ObjectUtils.anyNotNull(resolvedRewarded, resolvedExtImp)
                ? imp.toBuilder()
                .rwdd(resolvedRewarded != null ? resolvedRewarded : rewarded)
                .ext(resolvedExtImp != null ? resolvedExtImp : extImp)
                .build()
                : null;
    }

    private static Integer resolveImpRewarded(Integer rewarded, ObjectNode extImp) {
        if (rewarded != null) {
            return null;
        }

        final JsonNode rewardedNode = extImp.at(IMP_EXT_PREBID_REWARDED);
        return rewardedNode.isIntegralNumber()
                ? rewardedNode.asInt()
                : null;
    }

    private static ObjectNode resolveImpExt(ObjectNode extImp) {
        final JsonNode rewardedNode = extImp.at(IMP_EXT_PREBID_REWARDED);
        if (!rewardedNode.isIntegralNumber()) {
            return null;
        }

        final ObjectNode modifiedExtImp = extImp.deepCopy();
        ((ObjectNode) modifiedExtImp.get(PREBID_FIELD)).remove(IS_REWARDED_INVENTORY_FIELD);

        return modifiedExtImp;
    }

    private static Source moveSourceData(Source source) {
        if (source == null) {
            return null;
        }

        final ExtSource extSource = source.getExt();
        if (extSource == null) {
            return null;
        }

        final SupplyChain supplyChain = source.getSchain();
        final SupplyChain resolvedSupplyChain = supplyChain == null ? extSource.getSchain() : null;

        final ExtSource resolvedExtSource = resolveSourceExt(extSource);

        return ObjectUtils.anyNotNull(resolvedSupplyChain, resolvedExtSource)
                ? source.toBuilder()
                .schain(resolvedSupplyChain != null ? resolvedSupplyChain : supplyChain)
                .ext(resolvedExtSource != null ? nullIfEmpty(resolvedExtSource) : extSource)
                .build()
                : null;
    }

    private static ExtSource resolveSourceExt(ExtSource extSource) {
        if (extSource.getSchain() == null) {
            return null;
        }

        final ExtSource modifiedExtSource = ExtSource.of(null);
        copyProperties(extSource, modifiedExtSource);

        return modifiedExtSource;
    }

    private static void copyProperties(FlexibleExtension source, FlexibleExtension target) {
        Optional.ofNullable(source)
                .map(FlexibleExtension::getProperties)
                .ifPresent(target::addProperties);
    }

    private static <T extends FlexibleExtension> T nullIfEmpty(T ext) {
        return nullIfEmpty(ext, MapUtils.isEmpty(ext.getProperties()));
    }

    private static <T> T nullIfEmpty(T object, boolean isEmpty) {
        return isEmpty ? null : object;
    }

    private static Regs moveRegsData(Regs regs) {
        if (regs == null) {
            return null;
        }

        final ExtRegs extRegs = regs.getExt();
        if (extRegs == null) {
            return null;
        }

        final Integer gdpr = regs.getGdpr();
        final Integer resolvedGdpr = gdpr == null
                ? extRegs.getGdpr()
                : null;

        final String usPrivacy = regs.getUsPrivacy();
        final String resolvedUsPrivacy = usPrivacy == null
                ? extRegs.getUsPrivacy()
                : null;

        final ExtRegs resolvedExtRegs = resolveRegsExt(extRegs);

        return ObjectUtils.anyNotNull(resolvedGdpr, resolvedUsPrivacy, resolvedExtRegs)
                ? regs.toBuilder()
                .gdpr(resolvedGdpr != null ? resolvedGdpr : gdpr)
                .usPrivacy(resolvedUsPrivacy != null ? resolvedUsPrivacy : usPrivacy)
                .ext(resolvedExtRegs != null ? nullIfEmpty(resolvedExtRegs) : extRegs)
                .build()
                : null;
    }

    private static ExtRegs resolveRegsExt(ExtRegs extRegs) {
        if (extRegs == null || allNull(extRegs.getGdpr(), extRegs.getUsPrivacy())) {
            return null;
        }

        final ExtRegs modifiedExtRegs = ExtRegs.of(null, null);
        copyProperties(extRegs, modifiedExtRegs);

        return modifiedExtRegs;
    }

    private static boolean allNull(Object... objects) {
        return !ObjectUtils.anyNotNull(objects);
    }

    private static User moveUserData(User user) {
        if (user == null) {
            return null;
        }

        final ExtUser extUser = user.getExt();
        if (extUser == null) {
            return null;
        }

        final String consent = user.getConsent();
        final String resolvedConsent = consent == null
                ? extUser.getConsent()
                : null;

        final List<Eid> eids = user.getEids();
        final List<Eid> resolvedEids = resolveUserEids(eids, extUser);

        final ExtUser resolvedExtUser = resolveUserExt(extUser);

        return ObjectUtils.anyNotNull(resolvedConsent, resolvedEids, resolvedExtUser)
                ? user.toBuilder()
                .consent(resolvedConsent != null ? resolvedConsent : consent)
                .eids(resolvedEids != null ? resolvedEids : eids)
                .ext(resolvedExtUser != null ? nullIfEmpty(resolvedExtUser, resolvedExtUser.isEmpty()) : extUser)
                .build()
                : null;
    }

    private static List<Eid> resolveUserEids(List<Eid> eids, ExtUser extUser) {
        return eids == null ? extUser.getEids() : null;
    }

    private static ExtUser resolveUserExt(ExtUser extUser) {
        if (extUser == null || allNull(extUser.getConsent(), extUser.getEids())) {
            return null;
        }

        final ExtUser modifiedExtUser = extUser.toBuilder()
                .consent(null)
                .eids(null)
                .build();
        copyProperties(extUser, modifiedExtUser);

        return modifiedExtUser;
    }
}
