package org.prebid.server.cache;

import io.vertx.core.Future;
import org.prebid.server.cache.proto.request.module.ModuleCacheType;
import org.prebid.server.cache.proto.response.module.ModuleCacheResponse;

public interface ModuleCacheService {

    Future<Void> storeModuleEntry(String key,
                                  String value,
                                  ModuleCacheType type,
                                  Integer ttlseconds,
                                  String application,
                                  String moduleCode);

    Future<ModuleCacheResponse> retrieveModuleEntry(String key, String moduleCode, String application);

    static ModuleCacheService.NoOpModuleCacheService noOp() {
        return new ModuleCacheService.NoOpModuleCacheService();
    }

    class NoOpModuleCacheService implements ModuleCacheService {

        @Override
        public Future<Void> storeModuleEntry(String key,
                                             String value,
                                             ModuleCacheType type,
                                             Integer ttlseconds,
                                             String application,
                                             String moduleCode) {

            return Future.succeededFuture();
        }

        @Override
        public Future<ModuleCacheResponse> retrieveModuleEntry(String key, String moduleCode, String application) {
            return Future.succeededFuture(ModuleCacheResponse.empty());
        }
    }
}
