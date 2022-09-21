package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Producer;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufProducerMapper<ProtobufExtensionType>
        implements ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> {

    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Producer, ProtobufExtensionType> extensionMapper;

    public ProtobufProducerMapper(
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Producer, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Producer map(Producer producer) {
        final OpenRtb.BidRequest.Producer.Builder resultBuilder = OpenRtb.BidRequest.Producer.newBuilder();

        setNotNull(producer.getId(), resultBuilder::setId);
        setNotNull(producer.getName(), resultBuilder::setName);
        setNotNull(producer.getCat(), resultBuilder::addAllCat);
        setNotNull(producer.getDomain(), resultBuilder::setDomain);

        mapAndSetExtension(extensionMapper, producer.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
