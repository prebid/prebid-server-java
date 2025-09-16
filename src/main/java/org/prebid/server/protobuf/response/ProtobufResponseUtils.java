package org.prebid.server.protobuf.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.GeneratedMessageV3;
import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import com.iab.openrtb.response.TitleObject;
import com.iab.openrtb.response.VideoObject;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.protobuf.ProtobufMapper;

import java.math.BigDecimal;

public class ProtobufResponseUtils {

    private ProtobufResponseUtils() {
    }

    public static ProtobufMapper<OpenRtb.BidResponse, BidResponse> bidResponseMapper(
            ResponseExtensionMappersSpecification spec) {

        final ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper = linkMapper(spec.linkExtMapper());
        final ProtobufMapper<OpenRtb.NativeResponse, Response> nativeResponseMapper =
                nativeResponseMapper(
                        assetMapper(
                                titleMapper(spec.titleExtMapper()),
                                nativeImageMapper(spec.imageExtMapper()),
                                nativeVideoMapper(),
                                nativeDataMapper(spec.dataExtMapper()),
                                linkMapper,
                                spec.assetExtMapper()),
                        linkMapper,
                        eventTrackerMapper(spec.eventTrackerExtMapper()),
                        spec.nativeResponseExtMapper());

        final ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatBidMapper = seatBidMapper(
                bidMapper(nativeResponseMapper(spec.objectMapper(), nativeResponseMapper), spec.bidExtMapper()),
                spec.seatBidExtMapper());

        return bidResponseMapper(seatBidMapper, spec.bidResponseExtMapper());
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.BidResponse, BidResponse> bidResponseMapper(
            ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatBidMapper,
            ProtobufBackwardExtensionMapper<
                    OpenRtb.BidResponse,
                    ProtobufExtensionType,
                    ExtBidResponse
                    > extensionMapper) {

        return (OpenRtb.BidResponse bidResponse) ->
                BidResponse.builder()
                        .id(bidResponse.getId())
                        .seatbid(bidResponse.getSeatbidList().stream().map(seatBidMapper::map).toList())
                        .bidid(bidResponse.getBidid())
                        .cur(bidResponse.getCur())
                        .customdata(bidResponse.getCustomdata())
                        .nbr(bidResponse.getNbr())
                        .ext(extractExtension(extensionMapper, bidResponse))
                        .build();
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> assetMapper(
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> titleMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> imageMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> videoMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> dataMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper,
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.Asset asset) ->
                Asset.builder()
                        .id(asset.getId())
                        .required(BooleanUtils.toInteger(asset.getRequired()))
                        .title(titleMapper.map(asset.getTitle()))
                        .img(imageMapper.map(asset.getImg()))
                        .video(videoMapper.map(asset.getVideo()))
                        .data(dataMapper.map(asset.getData()))
                        .link(linkMapper.map(asset.getLink()))
                        .ext(extractExtension(extensionMapper, asset))
                        .build();
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> bidMapper(
            ProtobufMapper<OpenRtb.NativeResponse, String> nativeResponseMapper,
            ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid.Bid, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.BidResponse.SeatBid.Bid bid) -> {
            final String adm = bid.getAdm();
            final String resolvedAdm = adm.isEmpty() ? nativeResponseMapper.map(bid.getAdmNative()) : adm;

            return Bid.builder()
                    .id(bid.getId())
                    .impid(bid.getImpid())
                    .price(BigDecimal.valueOf(bid.getPrice()))
                    .nurl(bid.getNurl())
                    .burl(bid.getBurl())
                    .lurl(bid.getLurl())
                    .adm(resolvedAdm)
                    .adid(bid.getAdid())
                    .adomain(bid.getAdomainList())
                    .bundle(bid.getBundle())
                    .iurl(bid.getIurl())
                    .cid(bid.getCid())
                    .crid(bid.getCrid())
                    .tactic(bid.getTactic())
                    .cat(bid.getCatList())
                    .attr(bid.getAttrList())
                    .api(bid.getApi())
                    .protocol(bid.getProtocol())
                    .qagmediarating(bid.getQagmediarating())
                    .language(bid.getLanguage())
                    .dealid(bid.getDealid())
                    .w(bid.getW())
                    .h(bid.getH())
                    .wratio(bid.getWratio())
                    .hratio(bid.getHratio())
                    .exp(bid.getExp())
                    .ext(extractExtension(extensionMapper, bid))
                    .build();
        };
    }

    public static <ProtobufExtensionType>
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Data, DataObject> nativeDataMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Data, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.Asset.Data data) ->
                DataObject.builder()
                        .type(data.getType())
                        .len(data.getLen())
                        .value(data.getValue())
                        .ext(extractExtension(extensionMapper, data))
                        .build();
    }

    public static <ProtobufExtensionType>
            ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> eventTrackerMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.EventTracker, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.EventTracker eventTracker) ->
                EventTracker.builder()
                        .event(eventTracker.getEvent())
                        .method(eventTracker.getMethod())
                        .url(eventTracker.getUrl())
                        .ext(extractExtension(extensionMapper, eventTracker))
                        .build();
    }

    public static <ProtobufExtensionType>
            ProtobufMapper<OpenRtb.NativeResponse.Asset.Image, ImageObject> nativeImageMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Image, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.Asset.Image image) ->
                ImageObject.builder()
                        .type(image.getType())
                        .url(image.getUrl())
                        .w(image.getW())
                        .h(image.getH())
                        .ext(extractExtension(extensionMapper, image))
                        .build();
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Link, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.Link link) ->
                Link.of(
                        link.getUrl(),
                        link.getClicktrackersList(),
                        link.getFallback(),
                        extractExtension(extensionMapper, link));
    }

    public static ProtobufMapper<OpenRtb.NativeResponse, String> nativeResponseMapper(
            ObjectMapper objectMapper,
            ProtobufMapper<OpenRtb.NativeResponse, Response> responseMapper) {

        return (OpenRtb.NativeResponse nativeResponse) -> {
            try {
                final Response response = responseMapper.map(nativeResponse);
                return objectMapper.writeValueAsString(response);
            } catch (JsonProcessingException e) {
                return null;
            }
        };
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.NativeResponse, Response> nativeResponseMapper(
            ProtobufMapper<OpenRtb.NativeResponse.Asset, Asset> assetMapper,
            ProtobufMapper<OpenRtb.NativeResponse.Link, Link> linkMapper,
            ProtobufMapper<OpenRtb.NativeResponse.EventTracker, EventTracker> eventTrackerMapper,
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse response) ->
                Response.builder()
                        .ver(response.getVer())
                        .assets(response.getAssetsList().stream().map(assetMapper::map).toList())
                        .assetsurl(response.getAssetsurl())
                        .dcourl(response.getDcourl())
                        .link(linkMapper.map(response.getLink()))
                        .imptrackers(response.getImptrackersList())
                        .jstracker(response.getJstracker())
                        .eventtrackers(response.getEventtrackersList().stream().map(eventTrackerMapper::map).toList())
                        .privacy(response.getPrivacy())
                        .ext(extractExtension(extensionMapper, response))
                        .build();
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.BidResponse.SeatBid, SeatBid> seatBidMapper(
            ProtobufMapper<OpenRtb.BidResponse.SeatBid.Bid, Bid> bidMapper,
            ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.BidResponse.SeatBid seatBid) ->
                SeatBid.builder()
                        .bid(seatBid.getBidList().stream().map(bidMapper::map).toList())
                        .seat(seatBid.getSeat())
                        .group(BooleanUtils.toInteger(seatBid.getGroup()))
                        .ext(extractExtension(extensionMapper, seatBid))
                        .build();
    }

    public static <ProtobufExtensionType> ProtobufMapper<OpenRtb.NativeResponse.Asset.Title, TitleObject> titleMapper(
            ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Title, ProtobufExtensionType> extensionMapper) {

        return (OpenRtb.NativeResponse.Asset.Title title) ->
                TitleObject.builder()
                        .text(title.getText())
                        .len(title.getLen())
                        .ext(extractExtension(extensionMapper, title))
                        .build();
    }

    public static ProtobufMapper<OpenRtb.NativeResponse.Asset.Video, VideoObject> nativeVideoMapper() {
        return (OpenRtb.NativeResponse.Asset.Video video) ->
                VideoObject.builder()
                        .vasttag(video.getVasttag())
                        .build();
    }

    private static <ContainingType extends GeneratedMessageV3.ExtendableMessage<ContainingType>, FromType, ToType>
            ToType extractExtension(
            ProtobufBackwardExtensionMapper<ContainingType, FromType, ToType> mapper, ContainingType value) {

        if (mapper == null || value == null) {
            return null;
        }

        return mapper.map(value.getExtension(mapper.extensionDescriptor()));
    }
}
