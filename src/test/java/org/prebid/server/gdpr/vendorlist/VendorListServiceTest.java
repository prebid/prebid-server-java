package org.prebid.server.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class VendorListServiceTest extends VertxTest {

    private static final String CACHE_DIR = "/cache/dir";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;
    @Mock
    private HttpClient httpClient;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private MetaInfo metaInfo;

    private VendorListService vendorListService;

    @Before
    public void setUp() {
        given(fileSystem.existsBlocking(anyString())).willReturn(false); // always create cache dir

        given(bidderCatalog.names()).willReturn(singleton(null));
        given(bidderCatalog.metaInfoByName(any())).willReturn(metaInfo);
        given(metaInfo.info()).willReturn(new BidderInfo(true, null, null, null, new BidderInfo.GdprInfo(52, true)));

        vendorListService = VendorListService.create(fileSystem, CACHE_DIR, httpClient, "http://vendorlist/{VERSION}",
                0, null, bidderCatalog);
    }

    // Creation related tests

    @Test
    public void creationShouldFailsIfCannotCreateCacheDir() {
        // given
        given(fileSystem.mkdirsBlocking(anyString())).willThrow(new RuntimeException("dir creation error"));

        // then
        assertThatThrownBy(() -> VendorListService.create(fileSystem, CACHE_DIR, httpClient, "http://vendorlist/%s",
                0, null, bidderCatalog))
                .hasMessage("dir creation error");
    }

    @Test
    public void creationShouldFailsIfCannotReadFiles() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willThrow(new RuntimeException("read error"));

        // then
        assertThatThrownBy(() -> VendorListService.create(fileSystem, CACHE_DIR, httpClient, "http://vendorlist/%s",
                0, null, bidderCatalog))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void creationShouldFailsIfCannotReadAtLeastOneVendorListFile() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("1.json"));
        given(fileSystem.readFileBlocking(anyString())).willThrow(new RuntimeException("read error"));

        // then
        assertThatThrownBy(() -> VendorListService.create(fileSystem, CACHE_DIR, httpClient, "http://vendorlist/%s",
                0, null, bidderCatalog))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void creationShouldFailsIfAtLeastOneVendorListFileCannotBeParsed() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("1.json"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("invalid"));

        // then
        assertThatThrownBy(() -> VendorListService.create(fileSystem, CACHE_DIR, httpClient, "http://vendorlist/%s",
                0, null, bidderCatalog))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    // Http related tests

    @Test
    public void shouldPerformHttpRequestWithExpectedQueryIfVendorListNotFound() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(eq("http://vendorlist/1"), anyLong());
    }

    @Test
    public void shouldNotAskToSaveFileIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidVendorListVersion() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(null, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasInvalidLastUpdated() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, null, null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasNoVendors() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), null);
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasEmptyVendors() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), emptyList());
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    @Test
    public void shouldNotAskToSaveFileIfFetchedVendorListHasAtLeastOneInvalidVendor() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), singletonList(Vendor.of(null, null)));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        // when
        vendorListService.forVersion(1);

        // then
        verify(httpClient).get(anyString(), anyLong());
        verify(fileSystem, never()).writeFile(any(), any(), any());
    }

    // File system related tests

    @Test
    public void shouldSaveFileWithExpectedPathAndContentIfVendorListNotFound() throws JsonProcessingException {
        // given
        final String vendorListAsString = mapper.writeValueAsString(givenVendorList());
        givenHttpClientReturnsResponse(200, vendorListAsString);

        // when
        vendorListService.forVersion(1);

        // then
        verify(fileSystem).writeFile(eq("/cache/dir/1.json"), eq(Buffer.buffer(vendorListAsString)), any());
    }

    // In-memory cache related tests

    @Test
    public void shouldFailIfVendorListNotFound() {
        // given
        givenHttpClientProducesException(new RuntimeException());

        // when
        final Future<?> future = vendorListService.forVersion(1);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .hasMessage("Vendor list for version 1 not fetched yet, try again later.");
    }

    @Test
    public void shouldReturnVendorListFromCache() throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(givenVendorList()));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        vendorListService.forVersion(1); // populate cache
        final Future<Map<Integer, Set<Integer>>> future = vendorListService.forVersion(1);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(52, singleton(1));
    }

    @Test
    public void shouldKeepPurposesOnlyForKnownVendors() throws JsonProcessingException {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(),
                asList(Vendor.of(52, singleton(1)), Vendor.of(42, singleton(1))));
        givenHttpClientReturnsResponse(200, mapper.writeValueAsString(vendorList));

        given(fileSystem.writeFile(anyString(), any(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(Future.succeededFuture()));

        // when
        vendorListService.forVersion(1); // populate cache
        final Future<Map<Integer, Set<Integer>>> future = vendorListService.forVersion(1);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).hasSize(1)
                .containsEntry(52, singleton(1));
    }

    private static VendorList givenVendorList() {
        final Vendor vendor = Vendor.of(52, singleton(1));
        return VendorList.of(1, new Date(), singletonList(vendor));
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(statusCode, null, response)));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.get(anyString(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(2)).handle(obj);
            return inv.getMock();
        };
    }
}
