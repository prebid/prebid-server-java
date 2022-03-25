# Prebid Server Release Versioning

Prebid Server uses regular versioning `MAJOR.MINOR.PATCH-SNAPSHOT`
where:
- `MAJOR` version - increment when we make incompatible API changes.
- `MINOR` version - increment when we add functionality in backward compatible manner.
- `PATCH` version - incremented when we make backward-compatible bug fixes.
- `SNAPSHOT` qualifier - considers "as-yet-unreleased" version.

## Showcases

1. When Prebid Server involves breaking changes `MAJOR` version must be incremented.
For example, current project version is `1.17.3`. So, next project version in this case will be `2.0.0`.

2. When Prebid Server involves new features with backward compatibility `MINOR` version must be incremented.
For example, current project version is `1.17.3`. So, next project version in this case will be `1.18.0`.

3. When Prebid Server involves bug fixes `PATCH` version must be incremented.
For example, current project version in POM file is `1.18.0-SNAPSHOT` and previous release version was `1.17.3`. So, next release version in this case will be `1.17.4` and development version must be back to `1.18.0-SNAPSHOT`.

## For developers

To set a project version and create a tag in remote repository [maven release plugin](http://maven.apache.org/maven-release/maven-release-plugin) can be used.
For example:
```
mvn release:clean release:prepare -DreleaseVersion=1.17.0 -DdevelopmentVersion=1.18.0-SNAPSHOT
```
To validate modifications above command can be appended with `-DdryRun=true` flag. This will prevent any changes.
