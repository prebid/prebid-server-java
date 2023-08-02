# Unit tests

## Run unit tests

### Run all tests

```
mvn test
```

### Run a single test file

```
mvn test -Dtest=GenericBidderTest
```

### Run a single unit test

```
mvn test -Dtest=GenericBidderTest#makeHttpRequestsShouldCreateExpectedUrl
```

### Run a unit test and skip checkstyle

Can be useful for printf-debugging because Checkstyle will fail on `System.out.println`

```
mvn test -Dtest=GenericBidderTest#makeHttpRequestsShouldCreateExpectedUrl -Dcheckstyle.skip
```
