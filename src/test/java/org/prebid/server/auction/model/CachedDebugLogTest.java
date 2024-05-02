package org.prebid.server.auction.model;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.Arrays;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class CachedDebugLogTest extends VertxTest {

    private CachedDebugLog cachedDebugLog;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private JacksonMapper jacksonMapperMock;

    @Before
    public void setUp() {
        cachedDebugLog = new CachedDebugLog(true, 2000, Pattern.compile("[<>]"), jacksonMapper);
    }

    @Test
    public void setHeadersShouldAddHeadersToLogBody() {
        // given
        final CaseInsensitiveMultiMap multiMap = CaseInsensitiveMultiMap.builder()
                .add("header1", "value1")
                .add("header2", "value2")
                .build();

        // when
        cachedDebugLog.setHeadersLog(multiMap);
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response></Response>
                <Headers>header1=value1
                header2=value2
                </Headers>
                </Log>""");
    }

    @Test
    public void setHeadersShouldReturnEmptyStringWhenHeadersMapIsNull() {
        // given and when
        cachedDebugLog.setHeadersLog(null);
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response></Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setHeadersShouldReturnEmptyStringWhenHeadersMapIsEmpty() {
        // given and when
        cachedDebugLog.setHeadersLog(CaseInsensitiveMultiMap.empty());
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response></Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setExtBidResponseShouldReturnCacheBodyWithResponse() {
        // given
        final ExtBidResponse extBidResponse = ExtBidResponse.builder().tmaxrequest(5L).build();

        // when
        cachedDebugLog.setExtBidResponse(extBidResponse);
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response>{"tmaxrequest":5}</Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setExtBidResponseShouldThrowPrebidException() {
        // given
        cachedDebugLog = new CachedDebugLog(true, 2000, Pattern.compile("[<>]"), jacksonMapperMock);
        given(jacksonMapperMock.encodeToString(any())).willThrow(new EncodeException("encode exception"));

        // when and then
        assertThatThrownBy(() -> cachedDebugLog.setExtBidResponse(
                ExtBidResponse.builder().build()))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Unable to marshal response ext for debugging with a reason: encode exception");
    }

    @Test
    public void setExtBidResponseShouldSetEmptyStringWhenArgumentIsNull() {
        // given and when
        cachedDebugLog.setExtBidResponse(null);
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response></Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setErrorsShouldAddErrorToExistingResponse() {
        // given
        final ExtBidResponse extBidResponse = ExtBidResponse.builder().tmaxrequest(5L).build();

        // when
        cachedDebugLog.setExtBidResponse(extBidResponse);
        cachedDebugLog.setErrors(Arrays.asList("error1", "error2"));
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response>{"tmaxrequest":5}
                Errors:
                error1
                error2</Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setErrorsShouldLogOnlyErrorInResponseIfExtBidResponseWasNotSet() {
        // given and when
        cachedDebugLog.setErrors(Arrays.asList("error1", "error2"));
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response>
                Errors:
                error1
                error2</Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setErrorsShouldLogSpecialMessageWhenBothErrorsAndExtBidResponseWereNotDefined() {
        // given and when
        cachedDebugLog.setErrors(emptyList());
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response>No response or errors created</Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setRequestShouldSetRequestAsIsIfNotNull() {
        // given and when
        cachedDebugLog.setRequest("request body");
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request>request body</Request>
                <Response></Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void setRequestShouldSetEmptyRequestIfNullPassedAsArgument() {
        // given and when
        cachedDebugLog.setRequest(null);
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request></Request>
                <Response></Response>
                <Headers></Headers>
                </Log>""");
    }

    @Test
    public void buildCacheBodyShouldLogRequestHeaderResponseAndErrors() {
        // given
        cachedDebugLog.setHeadersLog(CaseInsensitiveMultiMap.builder()
                .add("headerkey", "headervalue")
                .build());
        cachedDebugLog.setRequest("requestBody");
        cachedDebugLog.setExtBidResponse(ExtBidResponse.builder().tmaxrequest(5L).build());
        cachedDebugLog.setErrors(Arrays.asList("error1", "error2"));

        // when
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request>requestBody</Request>
                <Response>{"tmaxrequest":5}
                Errors:
                error1
                error2</Response>
                <Headers>headerkey=headervalue
                </Headers>
                </Log>""");
    }

    @Test
    public void buildCacheBodyShouldIgnoreDeprecatedXmlSymbolsInAllParts() {
        // given
        cachedDebugLog.setHeadersLog(CaseInsensitiveMultiMap.builder()
                .add("<headerkey>", "<headervalue>")
                .build());
        cachedDebugLog.setRequest("<requestBody>");
        cachedDebugLog.setExtBidResponse(ExtBidResponse.builder()
                .responsetimemillis(singletonMap("<key>", 5))
                .tmaxrequest(5L)
                .build());
        cachedDebugLog.setErrors(Arrays.asList("<error1>", "<error2>"));

        // when
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request>requestBody</Request>
                <Response>{"responsetimemillis":{"key":5},"tmaxrequest":5}
                Errors:
                error1
                error2</Response>
                <Headers>headerkey=headervalue
                </Headers>
                </Log>""");
    }

    @Test
    public void buildCacheBodyShouldNotEscapeSymbolsIfPatternIsNull() {
        // given
        cachedDebugLog = new CachedDebugLog(true, 2000, null, jacksonMapper);

        cachedDebugLog.setHeadersLog(CaseInsensitiveMultiMap.builder()
                .add("<headerkey>", "<headervalue>")
                .build());
        cachedDebugLog.setRequest("<requestBody>");
        cachedDebugLog.setExtBidResponse(ExtBidResponse.builder()
                .responsetimemillis(singletonMap("<key>", 5))
                .tmaxrequest(5L)
                .build());
        cachedDebugLog.setErrors(Arrays.asList("<error1>", "<error2>"));

        // when
        final String cacheBody = cachedDebugLog.buildCacheBody();

        // then
        assertThat(cacheBody).isEqualTo("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Log>
                <Request><requestBody></Request>
                <Response>{"responsetimemillis":{"<key>":5},"tmaxrequest":5}
                Errors:
                <error1>
                <error2></Response>
                <Headers><headerkey>=<headervalue>
                </Headers>
                </Log>""");
    }
}
