package org.prebid.server.functional.testcontainers

import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.SystemProperties
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName

import static org.prebid.server.functional.util.SystemProperties.MOCKSERVER_VERSION

class Dependencies {

    private static final Boolean IS_LAUNCH_CONTAINERS = Boolean.valueOf(
            SystemProperties.getPropertyOrDefault("launchContainers", "false"))

    static final Network network = Network.newNetwork()

    static final MySQLContainer mysqlContainer = new MySQLContainer<>("mysql:8.0.30")
            .withConfigurationOverride("org/prebid/server/functional/")
            .withDatabaseName("prebid")
            .withUsername("prebid")
            .withPassword("prebid")
            .withInitScript("org/prebid/server/functional/db_mysql_schema.sql")
            .withNetwork(network)

    static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer<>("postgres:16.0")
            .withDatabaseName("prebid")
            .withUsername("prebid")
            .withPassword("prebid")
            .withInitScript("org/prebid/server/functional/db_psql_schema.sql")
            .withNetwork(network)

    static final NetworkServiceContainer networkServiceContainer = new NetworkServiceContainer(MOCKSERVER_VERSION)
            .withNetwork(network)

    static LocalStackContainer localStackContainer

    static void start() {
        if (IS_LAUNCH_CONTAINERS) {
            Startables.deepStart([networkServiceContainer, mysqlContainer,
                                  localStackContainer = new LocalStackContainer(DockerImageName.parse("localstack/localstack:s3-latest"))
                                          .withNetwork(Dependencies.network)
                                          .withServices(LocalStackContainer.Service.S3)])
                    .join()
        }
    }

    static void stop() {
        if (IS_LAUNCH_CONTAINERS) {
            [networkServiceContainer, mysqlContainer, localStackContainer].parallelStream()
                    .forEach({ it.stop() })
        }
    }

    private Dependencies() {} // should not be instantiated
}
