package org.prebid.server.protobuf.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iabtechlab.openrtb.v2.OpenRtb;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtGeo;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.Objects;

@Value
@Builder
@Accessors(fluent = true)
public class RequestExtensionMappersSpecification {

    ObjectMapper objectMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest, ExtRequest, ?> bidRequestExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Site, ExtSite, ?> siteExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.App, ExtApp, ?> appExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Device, ExtDevice, ?> deviceExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.User, ExtUser, ?> userExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Source, ExtSource, ?> sourceExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Regs, ExtRegs, ?> regsExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp, ?> impExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Metric, ?> metricExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner, ?> bannerExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Banner.Format, ?> formatExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ?> videoExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Audio, ?> audioExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Native, ?> nativeExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest, ?> nativeRequestExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset, ?> nativeAssetExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Title, ?> nativeTitleExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Image, ?> nativeImageExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Video, ?> nativeVideoExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest.Asset.Data, ?> nativeDataExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.NativeRequest.EventTrackers, ?> nativeEventTrackerExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp, ?> pmpExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Pmp.Deal, ?> dealExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data, ?> dataExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Data.Segment, ?> segmentExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Publisher, ExtPublisher, ?> publisherExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Content, ?> contentExtMapper;

    JsonProtobufExtensionMapper<OpenRtb.BidRequest.Producer, ?> producerExtMapper;

    ProtobufForwardExtensionMapper<OpenRtb.BidRequest.Geo, ExtGeo, ?> geoExtMapper;

    private static RequestExtensionMappersSpecificationBuilder builder() {
        return new RequestExtensionMappersSpecificationBuilder();
    }

    public static RequestExtensionMappersSpecificationBuilder builder(ObjectMapper objectMapper) {
        return builder().objectMapper(Objects.requireNonNull(objectMapper));
    }
}
