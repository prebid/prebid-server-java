package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.PbRichMediaFilterProperties;

import java.util.Objects;
import java.util.Optional;

public class ModuleConfigResolver {

    private final ObjectMapper mapper;
    private final PbRichMediaFilterProperties globalProperties;

    public ModuleConfigResolver(ObjectMapper mapper,
                                PbRichMediaFilterProperties globalProperties) {
        this.mapper = Objects.requireNonNull(mapper);
        this.globalProperties = Objects.requireNonNull(globalProperties);
    }

    public PbRichMediaFilterProperties resolve(ObjectNode accountConfigNode) {
        return readAccountConfig(accountConfigNode).orElse(globalProperties);
    }

    private Optional<PbRichMediaFilterProperties> readAccountConfig(ObjectNode accountConfigNode) {
        try {
            return Optional.ofNullable(accountConfigNode)
                    .filter(node -> !node.isEmpty())
                    .map(node -> mapper.convertValue(node, PbRichMediaFilterProperties.class));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
