package org.prebid.server.auction.versionconverter.down;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Producer;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverter;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

public class BidRequestOrtb26To25Converter implements BidRequestOrtbVersionConverter {

    private static final String PREBID_FIELD = "prebid";
    private static final String IS_REWARDED_INVENTORY_FIELD = "is_rewarded_inventory";

    private static final Producer EMPTY_PRODUCER = Producer.builder().build();
    private static final Publisher EMPTY_PUBLISHER = Publisher.builder().build();

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
                bidRequest.getCattax(),
                bidRequest.getDooh(),
                modifiedSource,
                modifiedRegs)

                ? bidRequest.toBuilder()
                .imp(modifiedImps != null ? modifiedImps : imps)
                .site(modifiedSite != null ? modifiedSite : site)
                .app(modifiedApp != null ? modifiedApp : app)
                .device(modifiedDevice != null ? modifiedDevice : device)
                .user(modifiedUser != null ? modifiedUser : user)
                .wlangb(null)
                .cattax(null)
                .dooh(null)
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
        final Video video = imp.getVideo();
        final Video modifiedVideo = modifyVideo(video);

        final Audio audio = imp.getAudio();
        final Audio modifiedAudio = modifyAudio(audio);

        final ObjectNode impExt = imp.getExt();
        final ObjectNode modifiedImpExt = modifyImpExt(impExt, imp.getRwdd());

        return ObjectUtils.anyNotNull(modifiedVideo,
                modifiedAudio,
                imp.getSsai(),
                imp.getQty(),
                imp.getDt(),
                imp.getRefresh(),
                modifiedImpExt)

                ? imp.toBuilder()
                .video(modifiedVideo != null ? modifiedVideo : video)
                .audio(modifiedAudio != null ? modifiedAudio : audio)
                .rwdd(null)
                .ssai(null)
                .qty(null)
                .dt(null)
                .refresh(null)
                .ext(modifiedImpExt != null ? modifiedImpExt : impExt)
                .build()

                : null;
    }

    private static Video modifyVideo(Video video) {
        if (video == null) {
            return null;
        }

        return ObjectUtils.anyNotNull(
                video.getMaxseq(),
                video.getPoddur(),
                video.getPodid(),
                video.getPodseq(),
                video.getRqddurs(),
                video.getSlotinpod(),
                video.getMincpmpersec())

                ? video.toBuilder()
                .maxseq(null)
                .poddur(null)
                .podid(null)
                .podseq(null)
                .rqddurs(null)
                .slotinpod(null)
                .mincpmpersec(null)
                .build()

                : null;
    }

    private static Audio modifyAudio(Audio audio) {
        if (audio == null) {
            return null;
        }

        return ObjectUtils.anyNotNull(
                audio.getPoddur(),
                audio.getRqddurs(),
                audio.getPodid(),
                audio.getPodseq(),
                audio.getSlotinpod(),
                audio.getMincpmpersec(),
                audio.getMaxseq())

                ? audio.toBuilder()
                .poddur(null)
                .rqddurs(null)
                .podid(null)
                .podseq(null)
                .slotinpod(null)
                .mincpmpersec(null)
                .maxseq(null)
                .build()

                : null;
    }

    private ObjectNode modifyImpExt(ObjectNode impExt, Integer rewarded) {
        if (rewarded == null) {
            return null;
        }

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
        if (site == null) {
            return null;
        }

        final Publisher publisher = site.getPublisher();
        final Publisher modifiedPublisher = modifyPublisher(publisher);

        final Content content = site.getContent();
        final Content modifiedContent = modifyContent(content);

        return ObjectUtils.anyNotNull(
                site.getCattax(),
                site.getInventorypartnerdomain(),
                modifiedPublisher,
                modifiedContent,
                site.getKwarray())

                ? site.toBuilder()
                .cattax(null)
                .inventorypartnerdomain(null)
                .publisher(modifiedPublisher != null ? nullIfEmpty(modifiedPublisher) : publisher)
                .content(modifiedContent != null ? nullIfEmpty(modifiedContent) : content)
                .kwarray(null)
                .build()

                : null;
    }

    private static Publisher modifyPublisher(Publisher publisher) {
        return publisher != null && publisher.getCattax() != null
                ? publisher.toBuilder()
                .cattax(null)
                .build()
                : null;
    }

    private static Content modifyContent(Content content) {
        if (content == null) {
            return null;
        }

        final Producer producer = content.getProducer();
        final Producer modifiedProducer = modifyProducer(producer);

        return ObjectUtils.anyNotNull(
                modifiedProducer,
                content.getCattax(),
                content.getKwarray(),
                content.getLangb(),
                content.getNetwork(),
                content.getChannel())

                ? content.toBuilder()
                .producer(modifiedProducer != null ? nullIfEmpty(modifiedProducer) : producer)
                .cattax(null)
                .kwarray(null)
                .langb(null)
                .network(null)
                .channel(null)
                .build()

                : null;
    }

    private static Producer modifyProducer(Producer producer) {
        return producer != null && producer.getCattax() != null
                ? producer.toBuilder()
                .cattax(null)
                .build()
                : null;
    }

    private static Producer nullIfEmpty(Producer producer) {
        return nullIfEmpty(producer, EMPTY_PRODUCER.equals(producer));
    }

    private static Publisher nullIfEmpty(Publisher publisher) {
        return nullIfEmpty(publisher, EMPTY_PUBLISHER.equals(publisher));
    }

    private static Content nullIfEmpty(Content content) {
        return nullIfEmpty(content, content.isEmpty());
    }

    private static <T> T nullIfEmpty(T object, boolean isEmpty) {
        return isEmpty ? null : object;
    }

    private static App modifyApp(App app) {
        if (app == null) {
            return null;
        }

        final Publisher publisher = app.getPublisher();
        final Publisher modifiedPublisher = modifyPublisher(publisher);

        final Content content = app.getContent();
        final Content modifiedContent = modifyContent(content);

        return ObjectUtils.anyNotNull(
                app.getCattax(),
                app.getInventorypartnerdomain(),
                modifiedPublisher,
                modifiedContent,
                app.getKwarray())

                ? app.toBuilder()
                .cattax(null)
                .inventorypartnerdomain(null)
                .publisher(modifiedPublisher != null ? nullIfEmpty(modifiedPublisher) : publisher)
                .content(modifiedContent != null ? nullIfEmpty(modifiedContent) : content)
                .kwarray(null)
                .build()

                : null;
    }

    private static Device modifyDevice(Device device) {
        if (device == null) {
            return null;
        }

        return ObjectUtils.anyNotNull(device.getSua(), device.getLangb())
                ? device.toBuilder()
                .sua(null)
                .langb(null)
                .build()
                : null;
    }

    private static User modifyUser(User user) {
        if (user == null) {
            return null;
        }

        final ExtUser extUser = user.getExt();
        final ExtUser modifiedExtUser = modifyUserExt(extUser, user.getConsent(), user.getEids());

        return ObjectUtils.anyNotNull(user.getKwarray(), modifiedExtUser)
                ? user.toBuilder()
                .kwarray(null)
                .consent(null)
                .eids(null)
                .ext(modifiedExtUser != null ? modifiedExtUser : extUser)
                .build()
                : null;
    }

    private static ExtUser modifyUserExt(ExtUser extUser, String consent, List<Eid> eids) {
        if (consent == null && CollectionUtils.isEmpty(eids)) {
            return null;
        }

        final ExtUser modifiedExtUser = Optional.ofNullable(extUser)
                .map(ExtUser::toBuilder)
                .orElseGet(ExtUser::builder)
                .consent(consent)
                .eids(eids)
                .build();
        copyProperties(extUser, modifiedExtUser);

        return modifiedExtUser;
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
        final String gpp = regs.getGpp();
        final List<Integer> gppSid = regs.getGppSid();
        if (gdpr == null && usPrivacy == null && gpp == null && gppSid == null) {
            return null;
        }

        final ExtRegs originalExtRegs = regs.getExt();
        final String gpc = originalExtRegs != null ? originalExtRegs.getGpc() : null;
        final ExtRegsDsa dsa = originalExtRegs != null ? originalExtRegs.getDsa() : null;
        final ExtRegs extRegs = ExtRegs.of(gdpr, usPrivacy, gpc, dsa);
        copyProperties(originalExtRegs, extRegs);

        return regs.toBuilder()
                .gdpr(null)
                .usPrivacy(null)
                .gpp(null)
                .gppSid(null)
                .ext(extRegs)
                .build();
    }
}
