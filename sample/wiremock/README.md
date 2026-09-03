# Sample WireMock for Prebid Server Java

This directory contains a minimal WireMock setup you can use for local integration testing with Prebid Server (PBS-Java).

Structure:
- `mappings/generic-exchange.json` — maps requests to the `/generic-exchange` endpoint.
- `__files/generic-bid.json` — sample OpenRTB BidResponse returned by the mapping.
- `docker-compose.wiremock.yml` — ready-to-use Docker Compose definition for running WireMock in a container.

Target mock endpoint: `POST /generic-exchange`.

---

## Run with Docker Compose
Requirements: Docker Desktop (or Docker Engine) + Docker Compose.

1. Go to the sample directory:
   ```bash
   cd sample/wiremock
   ```
2. Start WireMock in the background:
   ```bash
   docker compose -f docker-compose.wiremock.yml up -d
   ```
   - The container runs image `wiremock/wiremock:3.13.2`.
   - WireMock listens inside the container on port `8080` and is mapped on the host as `http://localhost:8090`.
   - A volume mounts the current directory as `/home/wiremock` (read-only), so any changes in `mappings`/`__files` are visible without rebuilding the image.

3. Verify the endpoint works (example call):
   ```bash
   curl -s -X POST http://localhost:8090/generic-exchange -H 'Content-Type: application/json' -d '{}'
   ```
   You should receive the content from `__files/generic-bid.json`.

4. Tail logs:
   ```bash
   docker logs -f wiremock-prebid-server
   ```

5. Stop and remove the container:
   ```bash
   docker compose -f docker-compose.wiremock.yml down
   ```

Notes:
- If port `8090` is taken, change the port mapping in `docker-compose.wiremock.yml` (the line `ports: - "8090:8080"`) to something else, e.g., `9090:8080`, and remember to use the new port in your calls.

---

## Run using the IntelliJ WireMock Plugin
Requirements: IntelliJ IDEA with the “WireMock” plugin installed.

1. Install the plugin:
   - File → Settings → Plugins → Marketplace → search for “WireMock” → Install → Restart IDE.

2. Create a WireMock Run Configuration:
   - Run → Edit Configurations… → `+` → select “WireMock”.
   - Set the fields:
     - Files root (or Root dir): point to the `sample/wiremock` directory in the repo.
     - Port: set to `8090` (important: see the port note below).
     - Optionally enable `Verbose` logs.

3. Run the configuration and test:
   - Click Run on the new configuration.
   - Check the endpoint:
     ```bash
     curl -s -X POST http://localhost:8090/generic-exchange -H 'Content-Type: application/json' -d '{}'
     ```

### Important: set the port in the IntelliJ Run Configuration
- Make sure the WireMock configuration uses port `8090` to stay consistent with this sample and with any local PBS config that points to `http://localhost:8090/...`.
- If needed, you can choose a different port (e.g., 9090), but then update the addresses in your testing tools and/or PBS configuration accordingly.

---

## Integration with Prebid Server (locally)
- In your PBS adapter/bidder configuration, set the endpoint to the WireMock address, e.g., `http://localhost:8090/generic-exchange`.
- This sample does not enforce a specific request body — any `POST` to `/generic-exchange` returns the fixed response from `__files/generic-bid.json`.

---

## Customizing mappings
- To change the response payload, edit `__files/generic-bid.json`.
- To refine match conditions (e.g., headers, body patterns), update `mappings/generic-exchange.json` according to WireMock 3.x documentation.

---

## Troubleshooting
- Port in use: change the port in Docker Compose or in the IntelliJ Run Configuration.
- No response/404: ensure `Files root` points to `sample/wiremock` and that files under `mappings` and `__files` are visible.
- Changes not reflected in Docker: remember the volume is mounted as `:ro` (read-only) in the container — make edits on the host; the container reads them live.
