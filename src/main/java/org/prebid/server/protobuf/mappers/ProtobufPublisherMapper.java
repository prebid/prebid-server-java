package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Publisher;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufPublisherMapper<ProtobufExtensionType>
        implements ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> {

    private final ProtobufExtensionMapper<
            OpenRtb.BidRequest.Publisher,
            ExtPublisher,
            ProtobufExtensionType
            > extensionMapper;

    public ProtobufPublisherMapper(
            ProtobufExtensionMapper<
                    OpenRtb.BidRequest.Publisher,
                    ExtPublisher,
                    ProtobufExtensionType
                    > extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Publisher map(Publisher publisher) {
        final OpenRtb.BidRequest.Publisher.Builder resultBuilder = OpenRtb.BidRequest.Publisher.newBuilder();

        setNotNull(publisher.getId(), resultBuilder::setId);
        setNotNull(publisher.getName(), resultBuilder::setName);
        setNotNull(publisher.getCat(), resultBuilder::addAllCat);
        setNotNull(publisher.getDomain(), resultBuilder::setDomain);

        mapAndSetExtension(extensionMapper, publisher.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
