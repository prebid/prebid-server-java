package org.prebid.server.execution.file.supplier;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.file.FileUtil;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.function.Supplier;

public class RemoteFileSupplier implements Supplier<Future<String>> {

    private static final Logger logger = LoggerFactory.getLogger(RemoteFileSupplier.class);

    private final String savePath;
    private final String backupPath;
    private final String tmpPath;
    private final HttpClient httpClient;
    private final FileSystem fileSystem;

    private final RequestOptions getRequestOptions;
    private final RequestOptions headRequestOptions;

    public RemoteFileSupplier(String downloadUrl,
                              String savePath,
                              String tmpPath,
                              HttpClient httpClient,
                              long timeout,
                              boolean checkRemoteFileSize,
                              FileSystem fileSystem) {

        this.savePath = Objects.requireNonNull(savePath);
        this.backupPath = savePath + ".old";
        this.tmpPath = Objects.requireNonNull(tmpPath);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.fileSystem = Objects.requireNonNull(fileSystem);

        HttpUtil.validateUrl(downloadUrl);
        FileUtil.createAndCheckWritePermissionsFor(fileSystem, savePath);
        FileUtil.createAndCheckWritePermissionsFor(fileSystem, backupPath);
        FileUtil.createAndCheckWritePermissionsFor(fileSystem, tmpPath);

        getRequestOptions = new RequestOptions()
                .setMethod(HttpMethod.GET)
                .setTimeout(timeout)
                .setAbsoluteURI(downloadUrl)
                .setFollowRedirects(true);
        headRequestOptions = checkRemoteFileSize
                ? new RequestOptions()
                .setMethod(HttpMethod.HEAD)
                .setTimeout(timeout)
                .setAbsoluteURI(downloadUrl)
                .setFollowRedirects(true)
                : null;
    }

    @Override
    public Future<String> get() {
        return isDownloadRequired().compose(isDownloadRequired -> isDownloadRequired
                ? downloadFile()
                .compose(ignored -> createBackup())
                .compose(ignored -> tmpToSave())
                .map(savePath)
                : Future.succeededFuture());
    }

    private Future<Boolean> isDownloadRequired() {
        return headRequestOptions != null
                ? fileSystem.exists(savePath)
                .compose(exists -> exists ? isSizeChanged() : Future.succeededFuture(true))
                : Future.succeededFuture(true);
    }

    private Future<Boolean> isSizeChanged() {
        final Future<Long> localFileSize = fileSystem.props(savePath).map(FileProps::size);
        final Future<Long> remoteFileSize = sendHttpRequest(headRequestOptions)
                .map(response -> response.getHeader(HttpHeaders.CONTENT_LENGTH))
                .map(Long::parseLong);

        return Future.join(localFileSize, remoteFileSize)
                .map(compositeResult -> Objects.equals(compositeResult.resultAt(0), compositeResult.resultAt(1)));
    }

    private Future<Void> downloadFile() {
        return fileSystem.open(tmpPath, new OpenOptions())
                .compose(tmpFile -> sendHttpRequest(getRequestOptions)
                        .compose(response -> response.pipeTo(tmpFile))
                        .onComplete(result -> tmpFile.close()));
    }

    private Future<HttpClientResponse> sendHttpRequest(RequestOptions requestOptions) {
        return httpClient.request(requestOptions)
                .compose(HttpClientRequest::send)
                .map(this::validateResponse);
    }

    private HttpClientResponse validateResponse(HttpClientResponse response) {
        final int statusCode = response.statusCode();
        if (statusCode != HttpResponseStatus.OK.code()) {
            throw new PreBidException("Got unexpected response from server with status code %s and message %s"
                    .formatted(statusCode, response.statusMessage()));
        }

        return response;
    }

    private Future<Void> tmpToSave() {
        return copyFile(tmpPath, savePath)
                .compose(ignored -> deleteFile(tmpPath));
    }

    private Future<Void> createBackup() {
        return copyFile(savePath, backupPath);
    }

    public Future<Void> deleteBackup() {
        return fileSystem.exists(backupPath)
                .compose(exists -> exists ? deleteFile(backupPath) : Future.succeededFuture());
    }

    public Future<Void> restoreFromBackup() {
        return copyFile(backupPath, savePath)
                .compose(ignored -> deleteFile(backupPath));
    }

    private Future<Void> copyFile(String from, String to) {
        return fileSystem.move(from, to, new CopyOptions().setReplaceExisting(true));
    }

    private Future<Void> deleteFile(String filePath) {
        return fileSystem.delete(filePath)
                .onFailure(error -> logger.error("Can't delete file: " + filePath))
                .otherwiseEmpty();
    }
}
