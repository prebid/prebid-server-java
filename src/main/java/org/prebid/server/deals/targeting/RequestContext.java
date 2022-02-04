package org.prebid.server.deals.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.targeting.model.GeoLocation;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.StreamUtil;

import java.beans.BeanInfo;
import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RequestContext {

    private final BidRequest bidRequest;
    private final Imp imp;
    private final TxnLog txnLog;
    private final ObjectNode deviceExt;
    private final ObjectNode geoExt;
    private final ObjectNode userExt;

    private final AttributeReader<User> userAttributeReader = AttributeReader.forUser();
    private final AttributeReader<Site> siteAttributeReader = AttributeReader.forSite();
    private final AttributeReader<App> appAttributeReader = AttributeReader.forApp();
    private final AttributeReader<Imp> impContextDataAttributeReader = AttributeReader.forImpContextData();
    private final AttributeReader<Imp> impBidderAttributeReader = AttributeReader.forImpBidder();

    public RequestContext(BidRequest bidRequest, Imp imp, TxnLog txnLog, JacksonMapper mapper) {
        this.bidRequest = Objects.requireNonNull(bidRequest);
        this.imp = Objects.requireNonNull(imp);
        this.deviceExt = getExtNode(bidRequest.getDevice(), Device::getExt, mapper);
        this.geoExt = getExtNode(bidRequest.getDevice(),
                device -> getIfNotNull(getIfNotNull(device, Device::getGeo), Geo::getExt), mapper);
        this.userExt = getExtNode(bidRequest.getUser(), User::getExt, mapper);
        this.txnLog = Objects.requireNonNull(txnLog);
    }

    private static <T> ObjectNode getExtNode(T target,
                                             Function<T, FlexibleExtension> extExtractor,
                                             JacksonMapper mapper) {

        final FlexibleExtension ext = target != null ? extExtractor.apply(target) : null;
        return ext != null ? (ObjectNode) mapper.mapper().valueToTree(ext) : null;
    }

    public String lookupString(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        switch (type) {
            case domain:
                return ObjectUtils.defaultIfNull(
                        getIfNotNull(bidRequest.getSite(), Site::getDomain),
                        getIfNotNull(getIfNotNull(bidRequest.getSite(), Site::getPublisher), Publisher::getDomain));
            case publisherDomain:
                return getIfNotNull(getIfNotNull(bidRequest.getSite(), Site::getPublisher), Publisher::getDomain);
            case referrer:
                return getIfNotNull(bidRequest.getSite(), Site::getPage);
            case appBundle:
                return getIfNotNull(bidRequest.getApp(), App::getBundle);
            case adslot:
                return getFirstNonNullStringFromImpExt(
                        "context.data.pbadslot",
                        "context.data.adserver.adslot",
                        "data.pbadslot",
                        "data.adserver.adslot");
            case deviceGeoExt:
                return getValueFrom(geoExt, category, RequestContext::nodeToString);
            case deviceExt:
                return getValueFrom(deviceExt, category, RequestContext::nodeToString);
            case bidderParam:
                return impBidderAttributeReader.readFromExt(imp, category, RequestContext::nodeToString);
            case userFirstPartyData:
                return userAttributeReader.read(bidRequest.getUser(), category,
                        RequestContext::nodeToString, String.class);
            case siteFirstPartyData:
                return getSiteFirstPartyData(category, RequestContext::nodeToString);
            default:
                throw new TargetingSyntaxException(
                        String.format("Unexpected category for fetching string value for: %s", type));
        }
    }

    public Integer lookupInteger(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        switch (type) {
            case pagePosition:
                return getIfNotNull(getIfNotNull(imp, Imp::getBanner), Banner::getPos);
            case dow:
                return getIntegerFromUserExt("time.userdow");
            case hour:
                return getIntegerFromUserExt("time.userhour");
            case bidderParam:
                return impBidderAttributeReader.readFromExt(imp, category, RequestContext::nodeToInteger);
            case userFirstPartyData:
                return userAttributeReader.read(bidRequest.getUser(), category,
                        RequestContext::nodeToInteger, Integer.class);
            case siteFirstPartyData:
                return getSiteFirstPartyData(category, RequestContext::nodeToInteger);
            default:
                throw new TargetingSyntaxException(
                        String.format("Unexpected category for fetching integer value for: %s", type));
        }
    }

    public List<String> lookupStrings(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        switch (type) {
            case mediaType:
                return getMediaTypes();
            case bidderParam:
                return impBidderAttributeReader.readFromExt(imp, category, RequestContext::nodeToListOfStrings);
            case userSegment:
                return getSegments(category);
            case userFirstPartyData:
                return userAttributeReader.readFromExt(bidRequest.getUser(), category,
                        RequestContext::nodeToListOfStrings);
            case siteFirstPartyData:
                return getSiteFirstPartyData(category, RequestContext::nodeToListOfStrings);
            default:
                throw new TargetingSyntaxException(
                        String.format("Unexpected category for fetching string values for: %s", type));
        }
    }

    public List<Integer> lookupIntegers(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        switch (type) {
            case bidderParam:
                return impBidderAttributeReader.readFromExt(imp, category, RequestContext::nodeToListOfIntegers);
            case userFirstPartyData:
                return userAttributeReader.readFromExt(bidRequest.getUser(), category,
                        RequestContext::nodeToListOfIntegers);
            case siteFirstPartyData:
                return getSiteFirstPartyData(category, RequestContext::nodeToListOfIntegers);
            default:
                throw new TargetingSyntaxException(
                        String.format("Unexpected category for fetching integer values for: %s", type));
        }
    }

    public List<Size> lookupSizes(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        if (type != TargetingCategory.Type.size) {
            throw new TargetingSyntaxException(
                    String.format("Unexpected category for fetching sizes for: %s", type));
        }

        return ListUtils.emptyIfNull(getIfNotNull(getIfNotNull(imp, Imp::getBanner), Banner::getFormat))
                .stream()
                .map(format -> Size.of(format.getW(), format.getH()))
                .collect(Collectors.toList());
    }

    public GeoLocation lookupGeoLocation(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        if (type != TargetingCategory.Type.location) {
            throw new TargetingSyntaxException(
                    String.format("Unexpected category for fetching geo location for: %s", type));
        }

        final Geo geo = getIfNotNull(getIfNotNull(bidRequest, BidRequest::getDevice), Device::getGeo);
        final Float lat = getIfNotNull(geo, Geo::getLat);
        final Float lon = getIfNotNull(geo, Geo::getLon);

        return lat != null && lon != null ? GeoLocation.of(lat, lon) : null;
    }

    public TxnLog txnLog() {
        return txnLog;
    }

    private String getFirstNonNullStringFromImpExt(String... path) {
        return Arrays.stream(path)
                .map(this::getStringFromImpExt)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String getStringFromImpExt(String path) {
        return getIfNotNull(getIfNotNull(imp, Imp::getExt),
                node -> nodeToString(node.at(toJsonPointer(path))));
    }

    private static <S, T> T getIfNotNull(S source, Function<S, T> getter) {
        return source != null ? getter.apply(source) : null;
    }

    private Integer getIntegerFromUserExt(String path) {
        return getIfNotNull(getIfNotNull(userExt, node -> node.at(toJsonPointer(path))),
                RequestContext::nodeToInteger);
    }

    private List<String> getMediaTypes() {
        final List<String> mediaTypes = new ArrayList<>();
        if (imp.getBanner() != null) {
            mediaTypes.add("banner");
        }
        if (imp.getVideo() != null) {
            mediaTypes.add("video");
        }
        if (imp.getXNative() != null) {
            mediaTypes.add("native");
        }
        return mediaTypes;
    }

    private static <T> T getValueFrom(ObjectNode objectNode,
                                      TargetingCategory category,
                                      Function<JsonNode, T> valueExtractor) {

        final JsonNode jsonNode = getIfNotNull(objectNode, node -> node.at(toJsonPointer(category.path())));
        return getIfNotNull(jsonNode, valueExtractor);
    }

    private <T> T getSiteFirstPartyData(TargetingCategory category, Function<JsonNode, T> valueExtractor) {
        return ObjectUtil.firstNonNull(
                () -> impContextDataAttributeReader.readFromExt(imp, category, valueExtractor),
                () -> siteAttributeReader.readFromExt(bidRequest.getSite(), category, valueExtractor),
                () -> appAttributeReader.readFromExt(bidRequest.getApp(), category, valueExtractor));
    }

    public List<String> getSegments(TargetingCategory category) {
        final User user = getIfNotNull(bidRequest, BidRequest::getUser);
        final List<Data> userData = getIfNotNull(user, User::getData);

        return ListUtils.emptyIfNull(userData)
                .stream()
                .filter(Objects::nonNull)
                .filter(data -> Objects.equals(data.getId(), category.path()))
                .flatMap(data -> ListUtils.emptyIfNull(data.getSegment()).stream())
                .map(Segment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static String toJsonPointer(String path) {
        return Arrays.stream(path.split("\\."))
                .collect(Collectors.joining("/", "/", StringUtils.EMPTY));
    }

    private static String nodeToString(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static Integer nodeToInteger(JsonNode node) {
        return node.isInt() ? node.asInt() : null;
    }

    private static List<String> nodeToListOfStrings(JsonNode node) {
        final Function<JsonNode, String> valueExtractor = RequestContext::nodeToString;
        return node.isTextual()
                ? Collections.singletonList(valueExtractor.apply(node))
                : nodeToList(node, valueExtractor);
    }

    private static List<Integer> nodeToListOfIntegers(JsonNode node) {
        final Function<JsonNode, Integer> valueExtractor = RequestContext::nodeToInteger;
        return node.isInt()
                ? Collections.singletonList(valueExtractor.apply(node))
                : nodeToList(node, valueExtractor);
    }

    private static <T> List<T> nodeToList(JsonNode node, Function<JsonNode, T> valueExtractor) {
        if (node.isArray()) {
            return StreamUtil.asStream(node.spliterator())
                    .map(valueExtractor)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private static class AttributeReader<T> {

        private static final Set<Class<?>> SUPPORTED_PROPERTY_TYPES = Set.of(String.class, Integer.class, int.class);

        private static final String EXT_PREBID = "prebid";
        private static final String EXT_BIDDER = "bidder";
        private static final String EXT_DATA = "data";
        private static final String EXT_CONTEXT = "context";

        private final Map<String, PropertyDescriptor> properties;
        private final Function<T, JsonNode> extPathExtractor;

        private AttributeReader(Class<T> type, Function<T, JsonNode> extPathExtractor) {
            this.properties = supportedBeanProperties(type);
            this.extPathExtractor = extPathExtractor;
        }

        public static AttributeReader<User> forUser() {
            return new AttributeReader<>(
                    User.class,
                    user -> getIfNotNull(getIfNotNull(user, User::getExt), ExtUser::getData));
        }

        public static AttributeReader<Site> forSite() {
            return new AttributeReader<>(
                    Site.class,
                    site -> getIfNotNull(getIfNotNull(site, Site::getExt), ExtSite::getData));
        }

        public static AttributeReader<App> forApp() {
            return new AttributeReader<>(
                    App.class,
                    app -> getIfNotNull(getIfNotNull(app, App::getExt), ExtApp::getData));
        }

        public static AttributeReader<Imp> forImpContextData() {
            return new AttributeReader<>(
                    Imp.class,
                    imp -> getIfNotNull(
                            getIfNotNull(
                                    getIfNotNull(imp, Imp::getExt), node -> node.get(EXT_CONTEXT)),
                            node -> node.get(EXT_DATA)));
        }

        public static AttributeReader<Imp> forImpBidder() {
            return new AttributeReader<>(
                    Imp.class,
                    imp -> getIfNotNull(
                            getIfNotNull(
                                    getIfNotNull(imp, Imp::getExt), node -> node.get(EXT_PREBID)),
                            node -> node.get(EXT_BIDDER)));
        }

        public <A> A read(T target,
                          TargetingCategory category,
                          Function<JsonNode, A> valueExtractor,
                          Class<A> attributeType) {

            return ObjectUtil.firstNonNull(
                    // look in the object itself
                    () -> readFromObject(target, category, attributeType),
                    // then examine ext if value not found on top level or if it is nested attribute
                    () -> readFromExt(target, category, valueExtractor));
        }

        public <A> A readFromObject(T target, TargetingCategory category, Class<A> attributeType) {
            return isTopLevelAttribute(category.path())
                    ? getIfNotNull(target, user -> readProperty(user, category.path(), attributeType))
                    : null;
        }

        public <A> A readFromExt(T target, TargetingCategory category, Function<JsonNode, A> valueExtractor) {
            final JsonNode path = getIfNotNull(target, extPathExtractor);
            final JsonNode value = getIfNotNull(path, node -> node.at(toJsonPointer(category.path())));
            return getIfNotNull(value, valueExtractor);
        }

        private boolean isTopLevelAttribute(String path) {
            return !path.contains(".");
        }

        private static Map<String, PropertyDescriptor> supportedBeanProperties(Class<?> beanClass) {
            try {
                final BeanInfo beanInfo = Introspector.getBeanInfo(beanClass, Object.class);
                return Arrays.stream(beanInfo.getPropertyDescriptors())
                        .filter(descriptor -> SUPPORTED_PROPERTY_TYPES.contains(descriptor.getPropertyType()))
                        .collect(Collectors.toMap(FeatureDescriptor::getName, Function.identity()));
            } catch (IntrospectionException e) {
                return ExceptionUtils.rethrow(e);
            }
        }

        @SuppressWarnings("unchecked")
        private <A> A readProperty(T target, String path, Class<A> attributeType) {
            final PropertyDescriptor descriptor = properties.get(path);

            if (descriptor != null && descriptor.getPropertyType().equals(attributeType)) {
                try {
                    return (A) descriptor.getReadMethod().invoke(target);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    // just ignore
                }
            }

            return null;
        }
    }
}
