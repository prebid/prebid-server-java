package org.prebid.server.deals.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.deals.targeting.model.GeoLocation;
import org.prebid.server.deals.targeting.model.LookupResult;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;
import org.prebid.server.exception.TargetingSyntaxException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.FlexibleExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTime;
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
import java.util.stream.Stream;

public class RequestContext {

    private static final String EXT_PREBID_BIDDER = "prebid.bidder.";
    private static final String EXT_CONTEXT_DATA = "context.data.";

    private final BidRequest bidRequest;
    private final Imp imp;
    private final TxnLog txnLog;

    private final AttributeReader<Imp> impReader;
    private final AttributeReader<Geo> geoReader;
    private final AttributeReader<Device> deviceReader;
    private final AttributeReader<User> userReader;
    private final AttributeReader<Site> siteReader;
    private final AttributeReader<App> appReader;

    public RequestContext(BidRequest bidRequest, Imp imp, TxnLog txnLog, JacksonMapper mapper) {
        this.bidRequest = Objects.requireNonNull(bidRequest);
        this.imp = Objects.requireNonNull(imp);
        this.txnLog = Objects.requireNonNull(txnLog);

        impReader = AttributeReader.forImp();
        geoReader = AttributeReader.forGeo(getExtNode(
                bidRequest.getDevice(),
                device -> getIfNotNull(getIfNotNull(device, Device::getGeo), Geo::getExt),
                mapper));
        deviceReader = AttributeReader.forDevice(getExtNode(bidRequest.getDevice(), Device::getExt, mapper));
        userReader = AttributeReader.forUser();
        siteReader = AttributeReader.forSite();
        appReader = AttributeReader.forApp();
    }

    private static <T> ObjectNode getExtNode(T target,
                                             Function<T, FlexibleExtension> extExtractor,
                                             JacksonMapper mapper) {

        final FlexibleExtension ext = target != null ? extExtractor.apply(target) : null;
        return ext != null ? (ObjectNode) mapper.mapper().valueToTree(ext) : null;
    }

    public LookupResult<String> lookupString(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();

        switch (type) {
            case domain:
                return lookupResult(
                        getIfNotNull(bidRequest.getSite(), Site::getDomain),
                        getIfNotNull(getIfNotNull(bidRequest.getSite(), Site::getPublisher), Publisher::getDomain));
            case publisherDomain:
                return lookupResult(
                        getIfNotNull(getIfNotNull(bidRequest.getSite(), Site::getPublisher), Publisher::getDomain));
            case referrer:
                return lookupResult(getIfNotNull(bidRequest.getSite(), Site::getPage));
            case appBundle:
                return lookupResult(getIfNotNull(bidRequest.getApp(), App::getBundle));
            case adslot:
                return lookupResult(
                        impReader.readFromExt(imp, "context.data.pbadslot", RequestContext::nodeToString),
                        impReader.readFromExt(imp, "context.data.adserver.adslot", RequestContext::nodeToString),
                        impReader.readFromExt(imp, "data.pbadslot", RequestContext::nodeToString),
                        impReader.readFromExt(imp, "data.adserver.adslot", RequestContext::nodeToString));
            case deviceGeoExt:
                final Geo geo = getIfNotNull(bidRequest.getDevice(), Device::getGeo);
                return lookupResult(geoReader.readFromExt(geo, path, RequestContext::nodeToString));
            case deviceExt:
                return lookupResult(
                        deviceReader.readFromExt(bidRequest.getDevice(), path, RequestContext::nodeToString));
            case bidderParam:
                return lookupResult(
                        impReader.readFromExt(imp, EXT_PREBID_BIDDER + path, RequestContext::nodeToString));
            case userFirstPartyData:
                return userReader.read(bidRequest.getUser(), path, RequestContext::nodeToString, String.class);
            case siteFirstPartyData:
                return getSiteFirstPartyData(path, RequestContext::nodeToString);
            default:
                return LookupResult.empty();
        }
    }

    public LookupResult<Integer> lookupInteger(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();

        switch (type) {
            case pagePosition:
                return lookupResult(getIfNotNull(getIfNotNull(imp, Imp::getBanner), Banner::getPos));
            case dow:
                return lookupResult(
                        getIfNotNull(
                                getIfNotNull(getIfNotNull(bidRequest.getUser(), User::getExt), ExtUser::getTime),
                                ExtUserTime::getUserdow));
            case hour:
                return lookupResult(
                        getIfNotNull(
                                getIfNotNull(getIfNotNull(bidRequest.getUser(), User::getExt), ExtUser::getTime),
                                ExtUserTime::getUserhour));
            case deviceGeoExt:
                final Geo geo = getIfNotNull(bidRequest.getDevice(), Device::getGeo);
                return lookupResult(geoReader.readFromExt(geo, path, RequestContext::nodeToInteger));
            case bidderParam:
                return lookupResult(
                        impReader.readFromExt(imp, EXT_PREBID_BIDDER + path, RequestContext::nodeToInteger));
            case userFirstPartyData:
                return userReader.read(bidRequest.getUser(), path, RequestContext::nodeToInteger, Integer.class);
            case siteFirstPartyData:
                return getSiteFirstPartyData(path, RequestContext::nodeToInteger);
            default:
                return LookupResult.empty();
        }
    }

    public LookupResult<List<String>> lookupStrings(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();

        switch (type) {
            case mediaType:
                return lookupResult(getMediaTypes());
            case bidderParam:
                return lookupResult(
                        impReader.readFromExt(imp, EXT_PREBID_BIDDER + path, RequestContext::nodeToListOfStrings));
            case userSegment:
                return lookupResult(getSegments(category));
            case userFirstPartyData:
                return lookupResult(
                        userReader.readFromExt(bidRequest.getUser(), path, RequestContext::nodeToListOfStrings));
            case siteFirstPartyData:
                return getSiteFirstPartyData(path, RequestContext::nodeToListOfStrings);
            default:
                return LookupResult.empty();
        }
    }

    public LookupResult<List<Integer>> lookupIntegers(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();

        switch (type) {
            case bidderParam:
                return lookupResult(
                        impReader.readFromExt(imp, EXT_PREBID_BIDDER + path, RequestContext::nodeToListOfIntegers));
            case userFirstPartyData:
                return lookupResult(
                        userReader.readFromExt(bidRequest.getUser(), path, RequestContext::nodeToListOfIntegers));
            case siteFirstPartyData:
                return getSiteFirstPartyData(path, RequestContext::nodeToListOfIntegers);
            default:
                return LookupResult.empty();
        }
    }

    public LookupResult<List<Size>> lookupSizes(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        if (type != TargetingCategory.Type.size) {
            throw new TargetingSyntaxException(
                    String.format("Unexpected category for fetching sizes for: %s", type));
        }

        final List<Format> formats = getIfNotNull(getIfNotNull(imp, Imp::getBanner), Banner::getFormat);
        final List<Size> sizes = ListUtils.emptyIfNull(formats).stream()
                .map(format -> Size.of(format.getW(), format.getH()))
                .collect(Collectors.toList());

        return !sizes.isEmpty() ? LookupResult.ofValue(sizes) : LookupResult.empty();
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

    @SafeVarargs
    private static <T> LookupResult<T> lookupResult(T... candidates) {
        return LookupResult.of(listOfNonNulls(candidates));
    }

    @SafeVarargs
    private static <T> List<T> listOfNonNulls(T... candidates) {
        return Stream.of(candidates)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static <S, T> T getIfNotNull(S source, Function<S, T> getter) {
        return source != null ? getter.apply(source) : null;
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

    private <T> LookupResult<T> getSiteFirstPartyData(String path, Function<JsonNode, T> valueExtractor) {
        return lookupResult(
                impReader.readFromExt(imp, EXT_CONTEXT_DATA + path, valueExtractor),
                siteReader.readFromExt(bidRequest.getSite(), path, valueExtractor),
                appReader.readFromExt(bidRequest.getApp(), path, valueExtractor));
    }

    private List<String> getSegments(TargetingCategory category) {
        final List<Data> userData = getIfNotNull(bidRequest.getUser(), User::getData);

        final List<String> segments = ListUtils.emptyIfNull(userData)
                .stream()
                .filter(Objects::nonNull)
                .filter(data -> Objects.equals(data.getId(), category.path()))
                .flatMap(data -> ListUtils.emptyIfNull(data.getSegment()).stream())
                .map(Segment::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return !segments.isEmpty() ? segments : null;
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
        if (!node.isArray()) {
            return null;
        }

        return StreamUtil.asStream(node.spliterator())
                .map(valueExtractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static class AttributeReader<T> {

        private static final Set<Class<?>> SUPPORTED_PROPERTY_TYPES = Set.of(String.class, Integer.class, int.class);

        private final Map<String, PropertyDescriptor> properties;
        private final Function<T, JsonNode> extPathExtractor;

        private AttributeReader(Class<T> type, Function<T, JsonNode> extPathExtractor) {
            this.properties = supportedBeanProperties(type);
            this.extPathExtractor = extPathExtractor;
        }

        public static AttributeReader<Imp> forImp() {
            return new AttributeReader<>(
                    Imp.class,
                    imp -> getIfNotNull(imp, Imp::getExt));
        }

        public static AttributeReader<Geo> forGeo(ObjectNode geoExt) {
            return new AttributeReader<>(
                    Geo.class,
                    ignored -> geoExt);
        }

        public static AttributeReader<Device> forDevice(ObjectNode deviceExt) {
            return new AttributeReader<>(
                    Device.class,
                    ignored -> deviceExt);
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

        public <A> LookupResult<A> read(T target,
                                        String path,
                                        Function<JsonNode, A> valueExtractor,
                                        Class<A> attributeType) {

            return lookupResult(
                    // look in the object itself
                    readFromObject(target, path, attributeType),
                    // then examine ext if value not found on top level or if it is nested attribute
                    readFromExt(target, path, valueExtractor));
        }

        public <A> A readFromObject(T target, String path, Class<A> attributeType) {
            return isTopLevelAttribute(path)
                    ? getIfNotNull(target, user -> readProperty(user, path, attributeType))
                    : null;
        }

        public <A> A readFromExt(T target, String path, Function<JsonNode, A> valueExtractor) {
            final JsonNode extPath = getIfNotNull(target, extPathExtractor);
            final JsonNode value = getIfNotNull(extPath, node -> node.at(toJsonPointer(path)));
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
