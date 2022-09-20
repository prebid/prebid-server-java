package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufSiteMapper<ProtobufExtensionType>
        implements ProtobufMapper<Site, OpenRtb.BidRequest.Site> {

    private final ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper;
    private final ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper;
    private final ProtobufExtensionMapper<OpenRtb.BidRequest.Site, ExtSite, ProtobufExtensionType> extensionMapper;

    public ProtobufSiteMapper(
            ProtobufMapper<Publisher, OpenRtb.BidRequest.Publisher> publisherMapper,
            ProtobufMapper<Content, OpenRtb.BidRequest.Content> contentMapper,
            ProtobufExtensionMapper<OpenRtb.BidRequest.Site, ExtSite, ProtobufExtensionType> extensionMapper) {

        this.publisherMapper = Objects.requireNonNull(publisherMapper);
        this.contentMapper = Objects.requireNonNull(contentMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Site map(Site site) {
        final OpenRtb.BidRequest.Site.Builder resultBuilder = OpenRtb.BidRequest.Site.newBuilder();

        setNotNull(site.getId(), resultBuilder::setId);
        setNotNull(site.getName(), resultBuilder::setName);
        setNotNull(site.getDomain(), resultBuilder::setDomain);
        setNotNull(site.getCat(), resultBuilder::addAllCat);
        setNotNull(site.getSectioncat(), resultBuilder::addAllSectioncat);
        setNotNull(site.getPagecat(), resultBuilder::addAllPagecat);
        setNotNull(site.getPage(), resultBuilder::setPage);
        setNotNull(site.getRef(), resultBuilder::setRef);
        setNotNull(site.getSearch(), resultBuilder::setSearch);
        setNotNull(mapNotNull(site.getMobile(), BooleanUtils::toBoolean), resultBuilder::setMobile);
        setNotNull(mapNotNull(site.getPrivacypolicy(), BooleanUtils::toBoolean), resultBuilder::setPrivacypolicy);
        setNotNull(mapNotNull(site.getPublisher(), publisherMapper::map), resultBuilder::setPublisher);
        setNotNull(mapNotNull(site.getContent(), contentMapper::map), resultBuilder::setContent);
        setNotNull(site.getKeywords(), resultBuilder::setKeywords);

        mapAndSetExtension(extensionMapper, site.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
