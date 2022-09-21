package org.prebid.server.protobuf.mappers.request;

import com.iab.openrtb.request.Regs;
import com.iabtechlab.openrtb.v2.OpenRtb;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.protobuf.ProtobufExtensionMapper;
import org.prebid.server.protobuf.ProtobufMapper;

import static org.prebid.server.protobuf.MapperUtils.mapAndSetExtension;
import static org.prebid.server.protobuf.MapperUtils.mapNotNull;
import static org.prebid.server.protobuf.MapperUtils.setNotNull;

public class ProtobufRegsMapper<ProtobufExtensionType>
        implements ProtobufMapper<Regs, OpenRtb.BidRequest.Regs> {

    private final ProtobufExtensionMapper<OpenRtb.BidRequest.Regs, ExtRegs, ProtobufExtensionType> extensionMapper;

    public ProtobufRegsMapper(
            ProtobufExtensionMapper<OpenRtb.BidRequest.Regs, ExtRegs, ProtobufExtensionType> extensionMapper) {

        this.extensionMapper = extensionMapper;
    }

    @Override
    public OpenRtb.BidRequest.Regs map(Regs regs) {
        final OpenRtb.BidRequest.Regs.Builder resultBuilder = OpenRtb.BidRequest.Regs.newBuilder();

        setNotNull(mapNotNull(regs.getCoppa(), BooleanUtils::toBoolean), resultBuilder::setCoppa);

        mapAndSetExtension(extensionMapper, regs.getExt(), resultBuilder::setExtension);

        return resultBuilder.build();
    }
}
