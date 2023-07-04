# Unit tests

## Run unit tests

### Run all tests

```
mvn test
```

### Run a single test file

```
mvn test -Dtest="SharethroughBidderTest"
```

### Run a single unit test

```
mvn test -Dtest="SharethroughBidderTest#makeHttpRequestsShouldCorrectlyAddHeaders"
```

### Run a unit test and skip checkstyle

Can be useful for printf-debugging because Checkstyle will fail on `System.out.println`

```
mvn test -Dtest=SharethroughBidderTest#makeHttpRequestsShouldCorrectlyAddHeaders -Dcheckstyle.skip
```
