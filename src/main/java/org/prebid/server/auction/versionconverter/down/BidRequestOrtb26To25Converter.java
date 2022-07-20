package org.prebid.server.auction.versionconverter.down;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class BidRequestOrtb26To25Converter implements BidRequestOrtbVersionConverter {

    private static final String PREBID_FIELD = "prebid";
    private static final String IS_REWARDED_INVENTORY_FIELD = "is_rewarded_inventory";

    private final JacksonMapper mapper;

    public BidRequestOrtb26To25Converter(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public BidRequest convert(BidRequest bidRequest) {
        final List<Imp> imps = bidRequest.getImp();
        final List<Imp> modifiedImps = modifyImps(imps);

        final Site site = bidRequest.getSite();
        final Site modifiedSite = modifySite(site);

        final App app = bidRequest.getApp();
        final App modifiedApp = modifyApp(app);

        final Device device = bidRequest.getDevice();
        final Device modifiedDevice = modifyDevice(device);

        final User user = bidRequest.getUser();
        final User modifiedUser = modifyUser(user);

        final Source source = bidRequest.getSource();
        final Source modifiedSource = modifySource(source);

        final Regs regs = bidRequest.getRegs();
        final Regs modifiedRegs = modifyRegs(regs);

        return ObjectUtils.anyNotNull(
                modifiedImps,
                modifiedSite,
                modifiedApp,
                modifiedDevice,
                modifiedUser,
                bidRequest.getWlangb(),
                modifiedSource,
                modifiedRegs)

                ? bidRequest.toBuilder()
                .imp(modifiedImps != null ? modifiedImps : imps)
                .site(modifiedSite != null ? modifiedSite : site)
                .app(modifiedApp != null ? modifiedApp : app)
                .device(modifiedDevice != null ? modifiedDevice : device)
                .user(modifiedUser != null ? modifiedUser : user)
                .wlangb(null)
                .source(modifiedSource != null ? modifiedSource : source)
                .regs(modifiedRegs != null ? modifiedRegs : regs)
                .build()

                : bidRequest;
    }

    private List<Imp> modifyImps(List<Imp> imps) {
        final List<Imp> modifiedImps = imps.stream()
                .map(this::modifyImp)
                .toList();

        if (modifiedImps.stream().allMatch(Objects::isNull)) {
            return null;
        }

        return IntStream.range(0, imps.size())
                .mapToObj(i -> ObjectUtils.defaultIfNull(modifiedImps.get(i), imps.get(i)))
                .toList();
    }

    private Imp modifyImp(Imp imp) {
        final Integer rewarded = imp.getRwdd();
        if (rewarded == null) {
            return null;
        }

        return imp.toBuilder()
                .rwdd(null)
                .ext(modifyImpExt(imp.getExt(), rewarded))
                .build();
    }

    private ObjectNode modifyImpExt(ObjectNode impExt, Integer rewarded) {
        final ObjectNode copy = Optional.ofNullable(impExt)
                .map(ObjectNode::deepCopy)
                .orElseGet(mapper.mapper()::createObjectNode);

        JsonNode prebidNode = copy.get(PREBID_FIELD);
        if (prebidNode == null || !prebidNode.isObject()) {
            prebidNode = mapper.mapper().createObjectNode();
            copy.set(PREBID_FIELD, prebidNode);
        }
        ((ObjectNode) prebidNode).put(IS_REWARDED_INVENTORY_FIELD, rewarded);

        return copy;
    }

    private static Site modifySite(Site site) {
        final Content content = ObjectUtil.getIfNotNull(site, Site::getContent);
        final Content clearedContent = modifyContent(content);

        return ObjectUtils.anyNotNull(clearedContent)
                ? site.toBuilder()
                .content(nullIfContentEmpty(clearedContent))
                .build()
                : null;
    }

    private static Content nullIfContentEmpty(Content content) {
        return nullIfEmpty(content, content.isEmpty());
    }

    private static <T> T nullIfEmpty(T object, boolean isEmpty) {
        return isEmpty ? null : object;
    }

    private static Content modifyContent(Content content) {
        return content != null && content.getLangb() != null
                ? content.toBuilder()
                .langb(null)
                .build()
                : null;
    }

    private static App modifyApp(App app) {
        final Content content = ObjectUtil.getIfNotNull(app, App::getContent);
        final Content clearedContent = modifyContent(content);

        return ObjectUtils.anyNotNull(clearedContent)
                ? app.toBuilder()
                .content(nullIfContentEmpty(clearedContent))
                .build()
                : null;
    }

    private static Device modifyDevice(Device device) {
        return device != null && device.getLangb() != null
                ? device.toBuilder()
                .langb(null)
                .build()
                : null;
    }

    private static User modifyUser(User user) {
        if (user == null) {
            return null;
        }

        final String consent = user.getConsent();
        final List<Eid> eids = user.getEids();
        if (consent == null && CollectionUtils.isEmpty(eids)) {
            return null;
        }

        final ExtUser extUser = user.getExt();
        final ExtUser modifiedExtUser = Optional.ofNullable(extUser)
                .map(ExtUser::toBuilder)
                .orElseGet(ExtUser::builder)
                .consent(consent)
                .eids(eids)
                .build();
        copyProperties(extUser, modifiedExtUser);

        return user.toBuilder()
                .consent(null)
                .eids(null)
                .ext(modifiedExtUser)
                .build();
    }

    private static void copyProperties(FlexibleExtension source, FlexibleExtension target) {
        Optional.ofNullable(source)
                .map(FlexibleExtension::getProperties)
                .ifPresent(target::addProperties);
    }

    private static Source modifySource(Source source) {
        if (source == null) {
            return null;
        }

        final SupplyChain supplyChain = source.getSchain();
        if (supplyChain == null) {
            return null;
        }

        final ExtSource extSource = ExtSource.of(supplyChain);
        copyProperties(source.getExt(), extSource);

        return source.toBuilder()
                .schain(null)
                .ext(extSource)
                .build();
    }

    private static Regs modifyRegs(Regs regs) {
        if (regs == null) {
            return null;
        }

        final Integer gdpr = regs.getGdpr();
        final String usPrivacy = regs.getUsPrivacy();
        if (gdpr == null && usPrivacy == null) {
            return null;
        }

        final ExtRegs extRegs = ExtRegs.of(gdpr, usPrivacy);
        copyProperties(regs.getExt(), extRegs);

        return regs.toBuilder()
                .gdpr(null)
                .usPrivacy(null)
                .ext(extRegs)
                .build();
    }
}
