package org.prebid.server.protobuf.mappers;

import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Producer;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.protobuf.JsonProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import java.util.Objects;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapList;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufContentMapper<ProtobufExtensionType>
        implements ProtobufMapper<Content, OpenRtb.BidRequest.Content> {

    private final ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> producerMapper;
    private final ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper;
    private final JsonProtobufExtensionMapper<OpenRtb.BidRequest.Content, ProtobufExtensionType> extensionMapper;

    public ProtobufContentMapper(
            ProtobufMapper<Producer, OpenRtb.BidRequest.Producer> producerMapper,
            ProtobufMapper<Data, OpenRtb.BidRequest.Data> dataMapper,
            JsonProtobufExtensionMapper<OpenRtb.BidRequest.Content, ProtobufExtensionType> extensionMapper) {

        this.producerMapper = Objects.requireNonNull(producerMapper);
        this.dataMapper = Objects.requireNonNull(dataMapper);
        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Content map(Content content) {
        final OpenRtb.BidRequest.Content.Builder resultBuilder = OpenRtb.BidRequest.Content.newBuilder();

        setNotNull(content.getId(), resultBuilder::setId);
        setNotNull(content.getEpisode(), resultBuilder::setEpisode);
        setNotNull(content.getTitle(), resultBuilder::setTitle);
        setNotNull(content.getSeries(), resultBuilder::setSeries);
        setNotNull(content.getSeason(), resultBuilder::setSeason);
        setNotNull(content.getArtist(), resultBuilder::setArtist);
        setNotNull(content.getGenre(), resultBuilder::setGenre);
        setNotNull(content.getAlbum(), resultBuilder::setAlbum);
        setNotNull(content.getIsrc(), resultBuilder::setIsrc);
        setNotNull(mapNotNull(content.getProducer(), producerMapper::map), resultBuilder::setProducer);
        setNotNull(content.getUrl(), resultBuilder::setUrl);
        setNotNull(content.getCat(), resultBuilder::addAllCat);
        setNotNull(content.getProdq(), resultBuilder::setProdq);
        setNotNull(content.getContext(), resultBuilder::setContext);
        setNotNull(content.getContentrating(), resultBuilder::setContentrating);
        setNotNull(content.getUserrating(), resultBuilder::setUserrating);
        setNotNull(content.getQagmediarating(), resultBuilder::setQagmediarating);
        setNotNull(content.getKeywords(), resultBuilder::setKeywords);
        setNotNull(mapNotNull(content.getLivestream(), BooleanUtils::toBoolean), resultBuilder::setLivestream);
        setNotNull(
                mapNotNull(content.getSourcerelationship(), BooleanUtils::toBoolean),
                resultBuilder::setSourcerelationship);
        setNotNull(content.getLen(), resultBuilder::setLen);
        setNotNull(content.getLanguage(), resultBuilder::setLanguage);
        setNotNull(mapNotNull(content.getEmbeddable(), BooleanUtils::toBoolean), resultBuilder::setEmbeddable);
        setNotNull(mapList(content.getData(), dataMapper::map), resultBuilder::addAllData);

        mapAndSetExtension(extensionMapper, content.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
