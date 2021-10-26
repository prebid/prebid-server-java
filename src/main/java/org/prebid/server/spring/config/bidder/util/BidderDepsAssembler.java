package org.prebid.server.spring.config.bidder.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.BidderInstanceDeps;
import org.prebid.server.bidder.DisabledBidder;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.InputStreamResource;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BidderDepsAssembler<CFG extends BidderConfigurationProperties> {

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DISABLED = "%s is not configured properly on this "
            + "Prebid Server deploy. If you believe this should work, contact the company hosting the service "
            + "and tell them to check their configuration.";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private String bidderName;
    private CFG configProperties;
    private Function<UsersyncConfigurationProperties, Usersyncer> usersyncerCreator;
    private Function<CFG, Bidder<?>> bidderCreator;

    private BidderDepsAssembler() {
    }

    public static <CFG extends BidderConfigurationProperties> BidderDepsAssembler<CFG> forBidder(String bidderName) {
        final BidderDepsAssembler<CFG> assembler = new BidderDepsAssembler<>();
        assembler.bidderName = bidderName;
        return assembler;
    }

    public BidderDepsAssembler<CFG> usersyncerCreator(
            Function<UsersyncConfigurationProperties, Usersyncer> usersyncerCreator) {

        this.usersyncerCreator = usersyncerCreator;
        return this;
    }

    public BidderDepsAssembler<CFG> bidderCreator(Function<CFG, Bidder<?>> bidderCreator) {
        this.bidderCreator = bidderCreator;
        return this;
    }

    public BidderDepsAssembler<CFG> withConfig(CFG configProperties) {
        this.configProperties = configProperties;
        return this;
    }

    public BidderDeps assemble() {
        return BidderDeps.of(coreAndAliasesDeps());
    }

    private List<BidderInstanceDeps> coreAndAliasesDeps() {
        final List<BidderInstanceDeps> deps = new ArrayList<>();

        deps.add(coreDeps());
        deps.addAll(aliasesDeps());

        return deps;
    }

    private BidderInstanceDeps coreDeps() {
        return deps(bidderName, BidderInfoCreator.create(configProperties), configProperties);
    }

    private List<BidderInstanceDeps> aliasesDeps() {
        return configProperties.getAliases().entrySet().stream()
                .map(this::aliasDeps)
                .collect(Collectors.toList());
    }

    private BidderInstanceDeps aliasDeps(Map.Entry<String, Object> entry) {
        final String alias = entry.getKey();
        final CFG aliasConfigProperties = mergeAliasConfiguration(entry.getValue(), configProperties);

        validateCapabilities(alias, aliasConfigProperties, bidderName, configProperties);

        return deps(alias, BidderInfoCreator.create(aliasConfigProperties, bidderName), aliasConfigProperties);
    }

    private BidderInstanceDeps deps(String bidderName, BidderInfo bidderInfo, CFG configProperties) {
        return BidderInstanceDeps.builder()
                .name(bidderName)
                .deprecatedNames(configProperties.getDeprecatedNames())
                .bidderInfo(bidderInfo)
                .usersyncer(usersyncer(configProperties))
                .bidder(bidder(configProperties))
                .build();
    }

    private Usersyncer usersyncer(CFG configProperties) {
        return configProperties.getEnabled() ? usersyncerCreator.apply(configProperties.getUsersync()) : null;
    }

    private Bidder<?> bidder(CFG configProperties) {
        return configProperties.getEnabled()
                ? bidderCreator.apply(configProperties)
                : new DisabledBidder(String.format(ERROR_MESSAGE_TEMPLATE_FOR_DISABLED, bidderName));
    }

    private CFG mergeAliasConfiguration(Object aliasConfiguration, CFG coreConfiguration) {
        return mergeConfigurations(
                configurationAsPropertiesObject(aliasConfiguration, coreConfiguration.getSelfClass()),
                coreConfiguration);
    }

    private void validateCapabilities(String alias, CFG aliasConfiguration, String coreBidder, CFG coreConfiguration) {
        final MetaInfo coreMetaInfo = coreConfiguration.getMetaInfo();
        final MetaInfo aliasMetaInfo = aliasConfiguration.getMetaInfo();
        final List<String> coreAppMediaTypes = coreMetaInfo.getAppMediaTypes();
        final List<String> coreSiteMediaTypes = coreMetaInfo.getSiteMediaTypes();
        final List<String> aliasAppMediaTypes = aliasMetaInfo.getAppMediaTypes();
        final List<String> aliasSiteMediaTypes = aliasMetaInfo.getSiteMediaTypes();

        if (!coreAppMediaTypes.containsAll(aliasAppMediaTypes)
                || !coreSiteMediaTypes.containsAll(aliasSiteMediaTypes)) {

            throw new IllegalArgumentException(String.format(
                    "Alias %s supports more capabilities (app: %s, site: %s) "
                            + "than the core bidder %s (app: %s, site: %s)",
                    alias,
                    aliasAppMediaTypes,
                    aliasSiteMediaTypes,
                    coreBidder,
                    coreAppMediaTypes,
                    coreSiteMediaTypes));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CFG configurationAsPropertiesObject(Object configuration, Class<?> targetClass) {
        final String configAsYamlString = new Yaml().dump(configuration);

        final Properties configAsProperties = YamlPropertySourceFactory.readPropertiesFromYamlResource(
                new InputStreamResource(new ByteArrayInputStream(configAsYamlString.getBytes())));
        final Binder configurationBinder = new Binder(new MapConfigurationPropertySource(configAsProperties));

        return (CFG) configurationBinder.bind(StringUtils.EMPTY, (Class) targetClass).get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CFG mergeConfigurations(CFG aliasConfiguration, CFG coreConfiguration) {
        try {
            final JsonNode mergedNode = JsonMergePatch
                    .fromJson(MAPPER.valueToTree(aliasConfiguration))
                    .apply(MAPPER.valueToTree(coreConfiguration));

            return (CFG) MAPPER.treeToValue(mergedNode, (Class) coreConfiguration.getSelfClass());
        } catch (JsonPatchException | JsonProcessingException e) {
            throw new IllegalArgumentException("Exception occurred while merging alias configuration", e);
        }
    }
}
