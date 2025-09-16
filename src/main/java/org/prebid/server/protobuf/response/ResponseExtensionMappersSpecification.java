package org.prebid.server.protobuf.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iabtechlab.openrtb.v2.OpenRtb;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.Objects;

@Value
@Builder
@Accessors(fluent = true)
public class ResponseExtensionMappersSpecification {

    ObjectMapper objectMapper;

    ProtobufBackwardExtensionMapper<OpenRtb.BidResponse, ?, ExtBidResponse> bidResponseExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid, ?> seatBidExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.BidResponse.SeatBid.Bid, ?> bidExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse, ?> nativeResponseExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset, ?> assetExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Title, ?> titleExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Video, ?> videoExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Image, ?> imageExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Asset.Data, ?> dataExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.Link, ?> linkExtMapper;

    ProtobufJsonExtensionMapper<OpenRtb.NativeResponse.EventTracker, ?> eventTrackerExtMapper;

    private static ResponseExtensionMappersSpecification.ResponseExtensionMappersSpecificationBuilder builder() {
        return new ResponseExtensionMappersSpecification.ResponseExtensionMappersSpecificationBuilder();
    }

    public static ResponseExtensionMappersSpecification.ResponseExtensionMappersSpecificationBuilder builder(
            ObjectMapper objectMapper) {

        return builder().objectMapper(Objects.requireNonNull(objectMapper));
    }
}
