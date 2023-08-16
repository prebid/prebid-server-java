package org.prebid.server.deals.targeting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RequestContext {

    private static final String EXT_BIDDER = "bidder.";
    private static final String EXT_CONTEXT_DATA = "context.data.";
    private static final String EXT_DATA = "data.";

    private final BidRequest bidRequest;
    private final Imp imp;
    private final TxnLog txnLog;

    private final AttributeReader<Imp> impReader;
    private final AttributeReader<Geo> geoReader;
    private final AttributeReader<Device> deviceReader;
    private final AttributeReader<User> userReader;
    private final AttributeReader<Site> siteReader;
    private final AttributeReader<App> appReader;
    private final AttributeReader<Dooh> doohReader;

    public RequestContext(BidRequest bidRequest,
                          Imp imp,
                          TxnLog txnLog,
                          JacksonMapper mapper) {

        this.bidRequest = Objects.requireNonNull(bidRequest);
        this.imp = Objects.requireNonNull(imp);
        this.txnLog = Objects.requireNonNull(txnLog);

        impReader = AttributeReader.forImp();
        geoReader = AttributeReader.forGeo(getExtNode(
                bidRequest.getDevice(),
                device -> Optional.ofNullable(device).map(Device::getGeo).map(Geo::getExt).orElse(null),
                mapper
        ));
        deviceReader = AttributeReader.forDevice(getExtNode(bidRequest.getDevice(), Device::getExt, mapper));
        userReader = AttributeReader.forUser();
        siteReader = AttributeReader.forSite();
        appReader = AttributeReader.forApp();
        doohReader = AttributeReader.forDooh();
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

        final Site site = bidRequest.getSite();
        return switch (type) {
            case domain -> lookupResult(
                    Optional.ofNullable(site).map(Site::getDomain).orElse(null),
                    Optional.ofNullable(site).map(Site::getPublisher).map(Publisher::getDomain).orElse(null)
            );
            case publisherDomain -> lookupResult(
                    Optional.ofNullable(site).map(Site::getPublisher).map(Publisher::getDomain).orElse(null)
            );
            case referrer -> lookupResult(
                    Optional.ofNullable(site).map(Site::getPage).orElse(null)
            );
            case appBundle -> lookupResult(
                    Optional.ofNullable(bidRequest.getApp()).map(App::getBundle).orElse(null)
            );
            case adslot -> lookupResult(
                    imp.getTagid(),
                    impReader.readFromExt(imp, "gpid", RequestContext::nodeToString),
                    impReader.readFromExt(imp, "data.pbadslot", RequestContext::nodeToString),
                    impReader.readFromExt(imp, "data.adserver.adslot", RequestContext::nodeToString)
            );
            case deviceGeoExt -> lookupResult(geoReader.readFromExt(
                    Optional.ofNullable(bidRequest.getDevice()).map(Device::getGeo).orElse(null),
                    path,
                    RequestContext::nodeToString
            ));
            case deviceExt -> lookupResult(deviceReader.readFromExt(
                    bidRequest.getDevice(),
                    path,
                    RequestContext::nodeToString
            ));
            case bidderParam -> lookupResult(impReader.readFromExt(
                    imp,
                    EXT_BIDDER + path,
                    RequestContext::nodeToString
            ));
            case userFirstPartyData -> userReader.read(
                    bidRequest.getUser(),
                    path,
                    RequestContext::nodeToString,
                    String.class
            );
            case siteFirstPartyData -> getSiteFirstPartyData(path, RequestContext::nodeToString);
            default -> LookupResult.empty();
        };
    }

    //todo: LookupResult is an Optional duplicate at some point
    public LookupResult<Integer> lookupInteger(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();

        final User user = bidRequest.getUser();
        return switch (type) {
            case pagePosition -> lookupResult(
                    Optional.ofNullable(imp).map(Imp::getBanner).map(Banner::getPos).orElse(null));
            case dow -> lookupResult(
                    Optional.ofNullable(user).map(User::getExt).map(ExtUser::getTime).map(ExtUserTime::getUserdow)
                            .orElse(null));
            case hour -> lookupResult(
                    Optional.ofNullable(user).map(User::getExt).map(ExtUser::getTime).map(ExtUserTime::getUserhour)
                            .orElse(null));
            case deviceGeoExt -> lookupResult(geoReader.readFromExt(
                    Optional.ofNullable(bidRequest.getDevice()).map(Device::getGeo).orElse(null),
                    path,
                    RequestContext::nodeToInteger));
            case bidderParam -> lookupResult(impReader.readFromExt(
                    imp,
                    EXT_BIDDER + path,
                    RequestContext::nodeToInteger));
            case userFirstPartyData -> userReader.read(user, path, RequestContext::nodeToInteger, Integer.class);
            case siteFirstPartyData -> getSiteFirstPartyData(path, RequestContext::nodeToInteger);
            default -> LookupResult.empty();
        };
    }

    public LookupResult<List<String>> lookupStrings(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();
        final User user = bidRequest.getUser();

        return switch (type) {
            case mediaType -> lookupResult(getMediaTypes());
            case bidderParam -> lookupResult(impReader.readFromExt(
                    imp,
                    EXT_BIDDER + path,
                    RequestContext::nodeToListOfStrings));
            case userSegment -> lookupResult(getSegments(category));
            case userFirstPartyData -> lookupResult(
                    listOfNonNulls(userReader.readFromObject(user, path, String.class)),
                    userReader.readFromExt(user, path, RequestContext::nodeToListOfStrings));
            case siteFirstPartyData -> getSiteFirstPartyData(path, RequestContext::nodeToListOfStrings);
            default -> LookupResult.empty();
        };
    }

    public LookupResult<List<Integer>> lookupIntegers(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        final String path = category.path();
        final User user = bidRequest.getUser();

        return switch (type) {
            case bidderParam -> lookupResult(impReader.readFromExt(
                    imp,
                    EXT_BIDDER + path,
                    RequestContext::nodeToListOfIntegers));
            case userFirstPartyData -> lookupResult(
                    listOfNonNulls(userReader.readFromObject(user, path, Integer.class)),
                    userReader.readFromExt(user, path, RequestContext::nodeToListOfIntegers));
            case siteFirstPartyData -> getSiteFirstPartyData(path, RequestContext::nodeToListOfIntegers);
            default -> LookupResult.empty();
        };
    }

    public LookupResult<List<Size>> lookupSizes(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        if (type != TargetingCategory.Type.size) {
            throw new TargetingSyntaxException("Unexpected category for fetching sizes for: " + type);
        }

        final List<Size> sizes = ListUtils.union(sizesFromBanner(imp), sizesFromVideo(imp));

        return !sizes.isEmpty() ? LookupResult.ofValue(sizes) : LookupResult.empty();
    }

    private static List<Size> sizesFromBanner(Imp imp) {
        return Optional.ofNullable(imp.getBanner())
                .map(Banner::getFormat)
                .orElse(Collections.emptyList())
                .stream()
                .map(format -> Size.of(format.getW(), format.getH()))
                .toList();
    }

    private static List<Size> sizesFromVideo(Imp imp) {
        final Video video = imp.getVideo();
        final Integer width = video != null ? video.getW() : null;
        final Integer height = video != null ? video.getH() : null;

        return width != null && height != null
                ? Collections.singletonList(Size.of(width, height))
                : Collections.emptyList();
    }

    public GeoLocation lookupGeoLocation(TargetingCategory category) {
        final TargetingCategory.Type type = category.type();
        if (type != TargetingCategory.Type.location) {
            throw new TargetingSyntaxException("Unexpected category for fetching geo location for: " + type);
        }

        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getDevice)
                .map(Device::getGeo)
                .filter(geo -> geo.getLat() != null && geo.getLon() != null)
                .map(geo -> GeoLocation.of(geo.getLat(), geo.getLon()))
                .orElse(null);
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
                .toList();
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
                impReader.readFromExt(imp, EXT_DATA + path, valueExtractor),
                siteReader.readFromExt(bidRequest.getSite(), path, valueExtractor),
                doohReader.readFromExt(bidRequest.getDooh(), path, valueExtractor),
                appReader.readFromExt(bidRequest.getApp(), path, valueExtractor));
    }

    private List<String> getSegments(TargetingCategory category) {
        final List<String> segments = Optional.ofNullable(bidRequest.getUser()).map(User::getData).orElse(List.of())
                .stream()
                .filter(Objects::nonNull)
                .filter(data -> Objects.equals(data.getId(), category.path()))
                .flatMap(data -> ListUtils.emptyIfNull(data.getSegment()).stream())
                .map(Segment::getId)
                .filter(Objects::nonNull)
                .toList();

        return segments.isEmpty() ? null : segments;
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
                .toList();
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
                    imp -> Optional.ofNullable(imp).map(Imp::getExt).orElse(null));
        }

        public static AttributeReader<Geo> forGeo(ObjectNode geoExt) {
            return new AttributeReader<>(Geo.class, ignored -> geoExt);
        }

        public static AttributeReader<Device> forDevice(ObjectNode deviceExt) {
            return new AttributeReader<>(Device.class, ignored -> deviceExt);
        }

        public static AttributeReader<User> forUser() {
            return new AttributeReader<>(
                    User.class,
                    user -> Optional.ofNullable(user).map(User::getExt).map(ExtUser::getData).orElse(null));
        }

        public static AttributeReader<Site> forSite() {
            return new AttributeReader<>(
                    Site.class,
                    site -> Optional.ofNullable(site).map(Site::getExt).map(ExtSite::getData).orElse(null));
        }

        public static AttributeReader<App> forApp() {
            return new AttributeReader<>(
                    App.class,
                    app -> Optional.ofNullable(app).map(App::getExt).map(ExtApp::getData).orElse(null));
        }

        public static AttributeReader<Dooh> forDooh() {
            return new AttributeReader<>(
                    Dooh.class,
                    dooh -> Optional.ofNullable(dooh).map(Dooh::getExt).map(ExtDooh::getData).orElse(null));
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
                    ? Optional.ofNullable(target).map(user -> readProperty(user, path, attributeType)).orElse(null)
                    : null;
        }

        public <A> A readFromExt(T target, String path, Function<JsonNode, A> valueExtractor) {
            return Optional.ofNullable(target)
                    .map(extPathExtractor)
                    .map(node -> node.at(toJsonPointer(path)))
                    .map(valueExtractor)
                    .orElse(null);
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
