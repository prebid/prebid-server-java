package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Metric;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufMetricMapper<ProtobufExtensionType>
        implements ProtobufMapper<Metric, OpenRtb.BidRequest.Imp.Metric> {

    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Metric, ProtobufExtensionType> extensionMapper;

    public ProtobufMetricMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Imp.Metric, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Imp.Metric map(Metric metric) {
        final OpenRtb.BidRequest.Imp.Metric.Builder resultBuilder = OpenRtb.BidRequest.Imp.Metric.newBuilder();

        setNotNull(metric.getType(), resultBuilder::setType);
        setNotNull(mapNotNull(metric.getValue(), Float::doubleValue), resultBuilder::setValue);
        setNotNull(metric.getVendor(), resultBuilder::setVendor);

        mapAndSetExtension(extensionMapper, metric.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
