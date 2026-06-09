package org.prebid.server.privacy.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Feature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialFeature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialPurpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.TWO;

@ExtendWith(MockitoExtension.class)
public class VendorListFileStoreTest extends VertxTest {

    private static final String CACHE_DIR = "/cache/dir";
    private static final String FALLBACK_VENDOR_LIST_PATH = "fallback.json";
    private static final String GENERATION_VERSION = "v0";

    @Mock
    private FileSystem fileSystem;

    @TempDir
    Path tempDir;

    private VendorListFileStore target;

    @BeforeEach
    public void setUp() {
        target = new VendorListFileStore(0, fileSystem, jacksonMapper);
    }

    @Test
    public void createCacheFromDiskShouldCreateCacheDirWhenItDoesNotExist() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of());

        // when
        target.createCacheFromDisk(CACHE_DIR);

        // then
        verify(fileSystem).mkdirsBlocking(eq(CACHE_DIR));
    }

    @Test
    public void createCacheFromDiskShouldCreateCacheDirWhenPathExistsButIsNotADirectory() {
        // given
        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(true);
        given(fileSystem.propsBlocking(eq(CACHE_DIR))).willReturn(fileProps);
        given(fileProps.isDirectory()).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of());

        // when
        target.createCacheFromDisk(CACHE_DIR);

        // then
        verify(fileSystem).mkdirsBlocking(eq(CACHE_DIR));
    }

    @Test
    public void createCacheFromDiskShouldFailIfCannotCreateCacheDir() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.mkdirsBlocking(eq(CACHE_DIR)))
                .willThrow(new FileSystemException("dir creation error"));

        // when and then
        assertThatThrownBy(() -> target.createCacheFromDisk(CACHE_DIR))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot create directory: " + CACHE_DIR);
    }

    @Test
    public void createCacheFromDiskShouldFailIfNoWritePermissionsForCacheDir() {
        // given
        final String cacheDir = tempDir.toString();
        tempDir.toFile().setWritable(false, false);
        final FileProps fileProps = mock(FileProps.class);
        given(fileSystem.existsBlocking(eq(cacheDir))).willReturn(true);
        given(fileSystem.propsBlocking(eq(cacheDir))).willReturn(fileProps);
        given(fileProps.isDirectory()).willReturn(true);

        // when and then
        assertThatThrownBy(() -> target.createCacheFromDisk(cacheDir))
                .isInstanceOf(PreBidException.class)
                .hasMessage("No write permissions for directory: " + cacheDir);
    }

    @Test
    public void createCacheFromDiskShouldReturnCacheLoadedFromJsonFiles() throws JsonProcessingException {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of("/cache/dir/1.json"));
        given(fileSystem.readFileBlocking(eq("/cache/dir/1.json")))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(givenVendorList())));

        // when
        final Map<Integer, Map<Integer, Vendor>> cache = target.createCacheFromDisk(CACHE_DIR);

        // then
        assertThat(cache).isEqualTo(singletonMap(1, givenVendorMap()));
    }

    @Test
    public void createCacheFromDiskShouldReturnEmptyCacheWhenCacheDirHasNoJsonFiles() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of());

        // when
        final Map<Integer, Map<Integer, Vendor>> cache = target.createCacheFromDisk(CACHE_DIR);

        // then
        assertThat(cache).isEmpty();
    }

    @Test
    public void createCacheFromDiskShouldFailIfCannotReadCacheDir() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willThrow(new RuntimeException("read error"));

        // when and then
        assertThatThrownBy(() -> target.createCacheFromDisk(CACHE_DIR))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void createCacheFromDiskShouldFailIfCannotReadAtLeastOneVendorListFile() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of("1.json"));
        given(fileSystem.readFileBlocking(eq("1.json"))).willThrow(new RuntimeException("read error"));

        // when and then
        assertThatThrownBy(() -> target.createCacheFromDisk(CACHE_DIR))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void createCacheFromDiskShouldFailIfAtLeastOneVendorListFileCannotBeParsed() {
        // given
        given(fileSystem.existsBlocking(eq(CACHE_DIR))).willReturn(false);
        given(fileSystem.readDirBlocking(eq(CACHE_DIR))).willReturn(List.of("1.json"));
        given(fileSystem.readFileBlocking(eq("1.json"))).willReturn(Buffer.buffer("invalid"));

        // when and then
        assertThatThrownBy(() -> target.createCacheFromDisk(CACHE_DIR))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    @Test
    public void saveToFileShouldCompleteWithVendorListResultWhenWriteSucceeds() throws JsonProcessingException {
        // given
        final VendorList vendorList = givenVendorList();
        final String vendorListAsString = mapper.writeValueAsString(vendorList);
        final VendorListResult vendorListResult = VendorListResult.of(1, vendorListAsString, vendorList);
        givenWriteFileSucceeds();

        // when
        final Future<VendorListResult> future = target.saveToFile(vendorListResult, CACHE_DIR, GENERATION_VERSION);

        // then
        org.prebid.server.assertion.FutureAssertion.assertThat(future).succeededWith(vendorListResult);
    }

    @Test
    public void saveToFileShouldWriteToExpectedPathWithExpectedContent() throws JsonProcessingException {
        // given
        final VendorList vendorList = givenVendorList();
        final String vendorListAsString = mapper.writeValueAsString(vendorList);
        final VendorListResult vendorListResult = VendorListResult.of(1, vendorListAsString, vendorList);
        givenWriteFileSucceeds();

        // when
        target.saveToFile(vendorListResult, CACHE_DIR, GENERATION_VERSION);

        // then
        final ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(fileSystem).writeFile(pathCaptor.capture(), bufferCaptor.capture(), any());
        assertThat(pathCaptor.getValue()).isEqualTo(new File(CACHE_DIR, "1.json").getPath());
        assertThat(bufferCaptor.getValue().toString()).isEqualTo(vendorListAsString);
    }

    @Test
    public void saveToFileShouldFailWhenWriteFails() throws JsonProcessingException {
        // given
        final VendorList vendorList = givenVendorList();
        final String vendorListAsString = mapper.writeValueAsString(vendorList);
        final VendorListResult vendorListResult = VendorListResult.of(1, vendorListAsString, vendorList);
        final RuntimeException exception = new RuntimeException("write error");
        givenWriteFileFails(exception);

        // when
        final Future<VendorListResult> future = target.saveToFile(vendorListResult, CACHE_DIR, GENERATION_VERSION);

        // then
        org.prebid.server.assertion.FutureAssertion.assertThat(future)
                .isFailed()
                .isInstanceOf(RuntimeException.class)
                .hasMessage("write error");
    }

    @Test
    public void readFallbackVendorListShouldReturnNullWhenPathIsBlank() {
        // when and then
        assertThat(target.readFallbackVendorList(null)).isNull();
        assertThat(target.readFallbackVendorList("")).isNull();
        assertThat(target.readFallbackVendorList("   ")).isNull();
    }

    @Test
    public void readFallbackVendorListShouldReturnVendorsWhenPathIsValid() throws JsonProcessingException {
        // given
        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(givenVendorList())));

        // when
        final Map<Integer, Vendor> vendors = target.readFallbackVendorList(FALLBACK_VENDOR_LIST_PATH);

        // then
        assertThat(vendors).isEqualTo(givenVendorMap());
    }

    @Test
    public void readFallbackVendorListShouldFailIfCannotReadFallbackFile() {
        // given
        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willThrow(new RuntimeException("read error"));

        // when and then
        assertThatThrownBy(() -> target.readFallbackVendorList(FALLBACK_VENDOR_LIST_PATH))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("read error");
    }

    @Test
    public void readFallbackVendorListShouldFailIfFallbackCannotBeParsed() {
        // given
        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willReturn(Buffer.buffer("invalid"));

        // when and then
        assertThatThrownBy(() -> target.readFallbackVendorList(FALLBACK_VENDOR_LIST_PATH))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    @Test
    public void readFallbackVendorListShouldFailIfFallbackHasInvalidData() throws JsonProcessingException {
        // given
        final VendorList invalidVendorList = VendorList.of(1, new Date(), emptyMap());
        given(fileSystem.readFileBlocking(eq(FALLBACK_VENDOR_LIST_PATH)))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(invalidVendorList)));

        // when and then
        assertThatThrownBy(() -> target.readFallbackVendorList(FALLBACK_VENDOR_LIST_PATH))
                .isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Fallback vendor list parsed but has invalid data:");
    }

    private void givenWriteFileSucceeds() {
        given(fileSystem.writeFile(anyString(), any(Buffer.class), any())).willAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            return null;
        });
    }

    private void givenWriteFileFails(Throwable throwable) {
        given(fileSystem.writeFile(anyString(), any(Buffer.class), any())).willAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(2);
            handler.handle(Future.failedFuture(throwable));
            return null;
        });
    }

    private static VendorList givenVendorList() {
        return VendorList.of(1, new Date(), givenVendorMap());
    }

    private static Map<Integer, Vendor> givenVendorMap() {
        final Vendor vendor = Vendor.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
        return singletonMap(52, vendor);
    }
}
