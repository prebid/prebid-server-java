package org.prebid.server.settings.bidder;

import com.iab.openrtb.request.Imp;
import lombok.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value(staticConstructor = "of")
public class MediaTypeMappings {

    private static final List<MediaTypeMapping<?>> ALL_MAPPINGS = Arrays.asList(
            new MediaTypeMapping<>("banner", Imp::getBanner, Imp.ImpBuilder::banner),
            new MediaTypeMapping<>("video", Imp::getVideo, Imp.ImpBuilder::video),
            new MediaTypeMapping<>("native", Imp::getXNative, Imp.ImpBuilder::xNative),
            new MediaTypeMapping<>("audio", Imp::getAudio, Imp.ImpBuilder::audio));

    public static final MediaTypeMappings EMPTY = MediaTypeMappings.of(Collections.emptyList());
    public static final MediaTypeMappings ALL = MediaTypeMappings.of(ALL_MAPPINGS);

    List<MediaTypeMapping<?>> mediaTypeMappings;

    public static MediaTypeMappings byNames(List<String> mediaTypeNames) {
        final List<MediaTypeMapping<?>> mediaTypeMappings = mediaTypeNames.stream()
                .map(MediaTypeMappings::find)
                .collect(Collectors.toList());

        return MediaTypeMappings.of(mediaTypeMappings);
    }

    public static MediaTypeMappings negateNames(List<String> mediaTypeNames) {
        final List<MediaTypeMapping<?>> mediaTypeMappings = ALL_MAPPINGS.stream()
                .filter(mediaTypeMapping -> !mediaTypeNames.contains(mediaTypeMapping.getMediaType()))
                .collect(Collectors.toList());

        return MediaTypeMappings.of(mediaTypeMappings);
    }

    public boolean isAnyCorresponding(Imp imp) {
        return mediaTypeMappings.stream()
                .anyMatch(mediaTypeMapping -> mediaTypeMapping.corresponds(imp));
    }

    public List<MediaTypeMapping<?>> allCorresponding(Imp imp) {
        return mediaTypeMappings.stream()
                .filter(mediaTypeMapping -> mediaTypeMapping.corresponds(imp))
                .collect(Collectors.toList());
    }

    private static MediaTypeMapping<?> find(String mediaType) {
        return ALL_MAPPINGS.stream()
                .filter(mediaTypeCorrespondence -> mediaTypeCorrespondence.getMediaType().equals(mediaType))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Provided bidder media type is invalid %s", mediaType)));
    }

    public static class MediaTypeMapping<T> {

        private final String mediaType;
        private final Function<Imp, T> valueSupplier;
        private final BiFunction<Imp.ImpBuilder, T, Imp.ImpBuilder> modifier;

        private MediaTypeMapping(String mediaType,
                                 Function<Imp, T> valueSupplier,
                                 BiFunction<Imp.ImpBuilder, T, Imp.ImpBuilder> valueModifier) {
            this.mediaType = mediaType;
            this.valueSupplier = valueSupplier;
            this.modifier = valueModifier;
        }

        public boolean corresponds(Imp imp) {
            return valueSupplier.apply(imp) != null;
        }

        public Imp.ImpBuilder setValue(Imp.ImpBuilder impBuilder, T parameter) {
            return modifier.apply(impBuilder, parameter);
        }

        public String getMediaType() {
            return mediaType;
        }
    }
}
