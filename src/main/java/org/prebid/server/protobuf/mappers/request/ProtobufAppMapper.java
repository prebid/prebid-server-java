package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Publisher;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufAppMapper<ProtobufExtensionType>
        implements ProtobufMapper<App, OpenRtb.BidRequest.App> {

    private final ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper;
    private final ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidRequest.App, ExtApp, ProtobufExtensionType> extensionMapper;

    public ProtobufAppMapper(
            ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper,
            ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper,
            ProtobufExtensionMapper<OpenRtb.BidRequest.App, ExtApp, ProtobufExtensionType> extensionMapper) {

        this.publisherMapper = Objects.requireNonNull(publisherMapper);
        this.contentMapper = Objects.requireNonNull(contentMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.App map(App app) {
        final OpenRtb.BidRequest.App.Builder resultBuilder = OpenRtb.BidRequest.App.newBuilder();

        setNotNull(app.getId(), resultBuilder::setId);
        setNotNull(app.getName(), resultBuilder::setName);
        setNotNull(app.getBundle(), resultBuilder::setBundle);
        setNotNull(app.getDomain(), resultBuilder::setDomain);
        setNotNull(app.getStoreurl(), resultBuilder::setStoreurl);
        setNotNull(app.getCat(), resultBuilder::addAllCat);
        setNotNull(app.getSectioncat(), resultBuilder::addAllSectioncat);
        setNotNull(app.getPagecat(), resultBuilder::addAllPagecat);
        setNotNull(app.getVer(), resultBuilder::setVer);
        setNotNull(mapNotNull(app.getPrivacypolicy(), BooleanUtils::toBoolean), resultBuilder::setPrivacypolicy);
        setNotNull(mapNotNull(app.getPaid(), BooleanUtils::toBoolean), resultBuilder::setPaid);
        setNotNull(mapNotNull(app.getPublisher(), publisherMapper::map), resultBuilder::setPublisher);
        setNotNull(mapNotNull(app.getContent(), contentMapper::map), resultBuilder::setContent);
        setNotNull(app.getKeywords(), resultBuilder::setKeywords);

        mapAndSetExtension(extensionMapper, app.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
