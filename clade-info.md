# Prebid Server Java — Adapter Porting Notes

## Środowisko

- **Go repo:** `C:\Users\Lenovo\projects\prebid-server`
- **Java repo:** `C:\Users\Lenovo\IdeaProjects\prebid-server-java`
- **IDE:** IntelliJ IDEA

---

## Task 1: Insticator — port z Go PR #4726

**Issue Java:** https://github.com/prebid/prebid-server-java/issues/4481  
**Go PR:** https://github.com/prebid/prebid-server/pull/4726

### Zmiana: DSP seat z `TypedBid` → `ExtBidPrebidMeta.seat`

Go adapter ustawiał `Seat` na `TypedBid` co powodowało odrzucanie bidów przez `alternateBidderCodes`. Fix: przenieść seat do `bid.ext.prebid.meta.seat`.

### Zmodyfikowane pliki

**`InsticatorBidder.java`**
- Nowa metoda `modifyBidExt(bid, bidType, seat)` — wzorzec z Criteo
- Ustawia `bid.ext.prebid.meta.mediaType` i `bid.ext.prebid.meta.seat`
- `extractBids` iteruje przez `SeatBid` i przekazuje `seatBid.getSeat()`

**Testy i fixture**
- `InsticatorBidderTest.java` — zaktualizowane asserty na `bid.ext.prebid.meta`
- `test-insticator-bid-response.json` — dodano `"seat": "dsp_seat"` do seatbid
- `test-auction-insticator-response.json` — dodano `"mediaType"` i `"seat"` do meta

---

## Task 2: Alliance Gravity — nowy adapter

**Issue Java:** https://github.com/prebid/prebid-server-java/issues/4403  
**Go PR:** https://github.com/prebid/prebid-server/pull/4522

### Stworzone pliki

| Plik | Ścieżka |
|------|---------|
| Bidder | `src/main/java/org/prebid/server/bidder/alliancegravity/AllianceGravityBidder.java` |
| Ext model | `src/main/java/org/prebid/server/proto/openrtb/ext/request/alliancegravity/ExtImpAllianceGravity.java` |
| Spring config | `src/main/java/org/prebid/server/spring/config/bidder/AllianceGravityConfiguration.java` |
| Bidder YAML | `src/main/resources/bidder-config/alliancegravity.yaml` |
| Params JSON | `src/main/resources/static/bidder-params/alliance_gravity.json` |
| Unit test | `src/test/java/org/prebid/server/bidder/alliancegravity/AllianceGravityBidderTest.java` |
| IT test | `src/test/java/org/prebid/server/it/AllianceGravityTest.java` |
| IT fixtures | `src/test/resources/org/prebid/server/it/openrtb2/alliancegravity/` (4 pliki) |
| test-application.properties | dodano 2 linie dla `alliancegravity` |

---

## Kluczowe wzorce i gotchas

### Nazwa bidddera z podkreśleniem (np. `alliance_gravity`)

| Element | Wartość |
|---------|---------|
| `BIDDER_NAME` w Configuration.java | `"alliance_gravity"` (z podkreśleniem) |
| Klucz YAML w `bidder-config/*.yaml` | `alliancegravity` (bez podkreślenia) |
| `@ConfigurationProperties` prefix | `"adapters.alliancegravity"` (bez podkreślenia) |
| Plik params JSON | `alliance_gravity.json` (Z podkreśleniem) ← WAŻNE |
| `imp.ext` w auction request (IT test) | `"alliance_gravity"` (z podkreśleniem) |

**Wzorzec:** identyczny jak `EmxDigital` (`emxdigital.yaml` + `BIDDER_NAME = "emx_digital"`)

### Logika adaptera

```
makeHttpRequests:
  - parsuje ExtImpAllianceGravity.srId z imp.ext.bidder.srid
  - zastępuje imp.ext na ExtPrebid.of(ExtImpPrebid(storedrequest=srId), null)
  - wysyła jeden request dla wszystkich impów

makeBids:
  - bid type z mtype (1=banner, 2=video, 3=audio, 4=native)
  - brakujący/nieznany mtype → BidderError (bid pomijany, reszta dalej)
```

### Obsługa błędów w makeBids

```java
// DOBRZE — błędy per-bid zbierane, reszta działa
private static BidderBid resolveBidderBid(Bid bid, String currency, List<BidderError> errors) {
    try {
        return BidderBid.of(bid, getBidType(bid, errors), currency);
    } catch (PreBidException e) {
        errors.add(BidderError.badServerResponse(e.getMessage()));
        return null;
    }
}

// ŹLE — wyjątek w stream.map() dropuje WSZYSTKIE bidy
.map(bid -> BidderBid.of(bid, getBidType(bid), cur))  // jeśli getBidType rzuci
```

### WireMock equalToJson w IT testach

```java
// Użyj ignoreExtraElements=true żeby PBS-dodane pola nie blokowały matchu
.withRequestBody(equalToJson(jsonFrom("...bid-request.json"), true, true))
//                                                             ^     ^
//                                               ignoreArrayOrder   ignoreExtraElements
```

- WireMock 3 obsługuje `${json-unit.any-string}` i `${json-unit.any-number}` jako wildcards
- **NIE dodawaj** pól do fixture które adapter usuwa (np. `tid` w `imp.ext` gdy adapter zastępuje całe `ext`)

### IT auction request — format dla biddera z podkreśleniem

```json
"ext": {
  "prebid": {
    "bidder": {
      "alliance_gravity": {
        "srid": "test-stored-request-id"
      }
    }
  }
}
```

(NIE `"ext": {"alliance_gravity": {...}}` — PBS może nie znormalizować przy `fail-on-unknown-bidders: true`)

---

## Uruchamianie testów

```bash
# Testy jednostkowe
mvn test -Dtest=AllianceGravityBidderTest -Dcheckstyle.skip=true --no-transfer-progress

# IT test (może wymagać clean gdy są stare pliki w target/)
mvn clean test -Dtest=AllianceGravityTest -Dcheckstyle.skip=true --no-transfer-progress

# Oba naraz
mvn test -Dtest=AllianceGravityBidderTest,AllianceGravityTest -Dcheckstyle.skip=true --no-transfer-progress
```

---

## Wyniki

| Test | Status |
|------|--------|
| `InsticatorBidderTest` (24 testy) | ✅ PASS |
| `InsticatorTest` (IT) | ✅ PASS |
| `AllianceGravityBidderTest` (14 testów) | ✅ PASS |
| `AllianceGravityTest` (IT) | ✅ PASS |
