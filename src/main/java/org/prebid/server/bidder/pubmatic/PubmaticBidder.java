package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.bidder.pubmatic.proto.PubmaticRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PubmaticBidder extends OpenrtbBidder<ExtImpPubmatic> {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticBidder.class);

    private static final TypeReference<Map<String, Integer>> WRAPPER_VALIDATION =
            new TypeReference<Map<String, Integer>>() {
            };

    public PubmaticBidder(String endpointUrl) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpPubmatic.class);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpPubmatic extImpPubmatic) throws PreBidException {
        // validate Impression
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException(String.format("Invalid MediaType. PubMatic only supports Banner and Video. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }

        // impression extension validation
        final ObjectNode wrapExt = extImpPubmatic.getWrapper();
        if (wrapExt != null) {
            try {
                Json.mapper.convertValue(wrapExt, WRAPPER_VALIDATION);
            } catch (IllegalArgumentException e) {
                throw new PreBidException(
                        String.format("Error in Wrapper Parameters = %s  for ImpID = %s WrapperExt = %s",
                                e.getMessage(), imp.getId(), wrapExt.toString()));
            }
        }

        // impression changes and additional validation
        final Imp.ImpBuilder modifiedImp = imp.toBuilder();
        if (imp.getAudio() != null) {
            modifiedImp.audio(null);
        }
        final Banner banner = imp.getBanner();
        if (banner != null) {
            final String adSlotString = StringUtils.trimToNull(extImpPubmatic.getAdSlot());
            Integer width = null;
            Integer height = null;
            if (!StringUtils.isEmpty(adSlotString)) {
                if (!adSlotString.contains("@")) {
                    modifiedImp.tagid(adSlotString);
                } else {
                    final String[] adSlot = adSlotString.split("@");
                    if (adSlot.length != 2 || StringUtils.isEmpty(adSlot[0].trim())
                            || StringUtils.isEmpty(adSlot[1].trim())) {
                        throw new PreBidException("Invalid adSlot provided");
                    }
                    modifiedImp.tagid(adSlot[0].trim());
                    final String[] adSize = adSlot[1].toLowerCase().split("x");
                    if (adSize.length != 2) {
                        throw new PreBidException("Invalid size provided in adSlot");
                    }
                    final String[] heightStr = adSize[1].split(":");
                    try {
                        width = Integer.valueOf(adSize[0].trim());
                        height = Integer.valueOf(heightStr[0].trim());
                    } catch (NumberFormatException e) {
                        throw new PreBidException("Invalid size provided in adSlot");
                    }
                }
            }
            if (width == null && height == null) {
                final boolean isFormatsPresent = CollectionUtils.isNotEmpty(banner.getFormat());
                width = isFormatsPresent && banner.getW() == null && banner.getH() == null
                         ? banner.getFormat().get(0).getW() : banner.getW();

                height = isFormatsPresent && banner.getH() == null && banner.getW() == null
                         ? banner.getFormat().get(0).getH() : banner.getH();
            }
            final Banner updatedBanner = banner.toBuilder().w(width).h(height).build();
            modifiedImp.banner(updatedBanner);
        }

        if (CollectionUtils.isNotEmpty(extImpPubmatic.getKeywords())) {
            modifiedImp.ext(makeKeywords(extImpPubmatic.getKeywords()));
        } else {
            modifiedImp.ext(null);
        }
        return modifiedImp.build();
    }

    private static ObjectNode makeKeywords(List<ExtImpPubmaticKeyVal> keywords) {
        final List<String> eachKv = new ArrayList<>();
        for (ExtImpPubmaticKeyVal keyVal : keywords) {
            if (CollectionUtils.isEmpty(keyVal.getValue())) {
                logger.error(String.format("No values present for key = %s", keyVal.getKey()));
            } else {
                eachKv.add(String.format("\"%s\":\"%s\"", keyVal.getKey(),
                        String.join(",", keyVal.getValue())));
            }
        }
        final String keywordsString = "{" + String.join(",", eachKv) + "}";
        try {
            return Json.mapper.readValue(keywordsString, ObjectNode.class);
        } catch (IOException e) {
            throw new PreBidException(String.format("Failed to create keywords with error: %s", e.getMessage()), e);
        }
    }

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder,
                                 List<ImpWithExt<ExtImpPubmatic>> impsWithExts) {
        impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpPubmatic::getWrapper)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(wrapExt -> requestBuilder.ext(Json.mapper.valueToTree(PubmaticRequestExt.of(wrapExt))));

        final String pubId = impsWithExts.stream()
                .map(ImpWithExt::getImpExt)
                .map(ExtImpPubmatic::getPublisherId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (bidRequest.getSite() != null) {
            modifySite(pubId, bidRequest, requestBuilder);
        } else if (bidRequest.getApp() != null) {
            modifyApp(pubId, bidRequest, requestBuilder);
        }
    }

    private static void modifySite(String pubId, BidRequest bidRequest,
                                   BidRequest.BidRequestBuilder bidRequestBuilder) {
        final Site site = bidRequest.getSite();
        if (site.getPublisher() != null) {
            final Publisher modifiedPublisher = site.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.site(site.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.site(site.toBuilder()
                    .publisher(Publisher.builder().id(pubId).build())
                    .build());
        }
    }

    private static void modifyApp(String pubId, BidRequest bidRequest,
                                  BidRequest.BidRequestBuilder bidRequestBuilder) {
        final App app = bidRequest.getApp();
        if (app.getPublisher() != null) {
            final Publisher modifiedPublisher = app.getPublisher().toBuilder().id(pubId).build();
            bidRequestBuilder.app(app.toBuilder().publisher(modifiedPublisher).build());
        } else {
            bidRequestBuilder.app(app.toBuilder()
                    .publisher(Publisher.builder().id(pubId).build())
                    .build());
        }
    }

    @Override
    protected BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }
}
