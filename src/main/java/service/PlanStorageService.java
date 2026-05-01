package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.*;
import okhttp3.*;
import repository.AuthRepository;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class PlanStorageService {
    private static final String APP_DIRECTORY_NAME = ".partplan";
    private static final String CACHE_DIRECTORY_NAME = "firebase-cache";
    private static final String PLAN_FILE_NAME = "plan.json";
    private static final String DRAWING_DIRECTORY_NAME = "drawing";
    private static final String PAGES_DIRECTORY_NAME = "pages";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final MediaType BINARY_MEDIA = MediaType.get("application/octet-stream");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthRepository authRepository;
    private final FirebaseAuthService authService;
    private final String databaseUrl;
    private final String storageBucket;

    public PlanStorageService() {
        try {
            FirebaseAppService.FirebaseConfig config = FirebaseAppService.loadConfig();
            this.databaseUrl = config.databaseUrl();
            this.storageBucket = config.storageBucket();
            this.authRepository = new AuthRepository();
            this.authService = new FirebaseAuthService();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Firebase configuration.", exception);
        }
    }

    public void savePlan(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        try {
            String idToken = requireValidIdToken();
            String companyId = requireCompanyId();
            String ownerUid = requireUid();
            plan.setUpdatedAt(LocalDateTime.now());

            InspectionPlan storagePlan = preparePlanForStorage(plan, ownerUid);
            String planObjectName = planObjectName(ownerUid, plan.getId());
            uploadStorageObject(planObjectName, buildJson(storagePlan), JSON_MEDIA, idToken);
            savePlanIndex(storagePlan, companyId, ownerUid, planObjectName, idToken);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save inspection plan.", exception);
        }
    }

    public List<InspectionPlan> loadPlans() {
        List<InspectionPlan> plans = new ArrayList<>();
        String idToken = getValidIdTokenOrNull();
        String companyId = getCompanyIdOrNull();
        String ownerUid = getUidOrNull();
        if (idToken == null || companyId == null || ownerUid == null) {
            return plans;
        }

        try {
            JsonNode indexRoot = readDatabaseNodeWithRefresh("planIndexes/" + companyId);
            if (indexRoot == null || indexRoot.isMissingNode() || indexRoot.isNull()) {
                return plans;
            }

            for (JsonNode indexNode : indexRoot) {
                String planId = indexNode.path("id").asText("");
                if (!planId.isBlank() && isOwnedByCurrentUser(indexNode, ownerUid)) {
                    plans.add(loadPlan(planId));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load saved plans.", exception);
        }

        plans.sort(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed());
        return plans;
    }

    public InspectionPlan loadPlan(String planId) {
        try {
            String idToken = requireValidIdToken();
            String ownerUid = requireUid();
            String json = downloadStorageObject(planObjectName(ownerUid, planId), idToken);
            InspectionPlan plan = readPlan(json);
            hydrateRemoteDrawings(plan, ownerUid, idToken);
            return plan;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open inspection plan.", exception);
        }
    }

    public void deletePlan(String planId) {
        try {
            String idToken = requireValidIdToken();
            String companyId = requireCompanyId();
            String ownerUid = requireUid();
            deleteStorageObjectsWithPrefix("users/" + ownerUid + "/plans/" + planId + "/", idToken);
            deleteDatabaseNode("planIndexes/" + companyId + "/" + planId, idToken);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete inspection plan.", exception);
        }
    }

    private InspectionPlan readPlan(String json) {
        String id = readStringValue(json, "id");
        String name = readStringValue(json, "name");
        String partNumber = readStringValue(json, "partNumber");
        String revision = readStringValue(json, "revision");
        String description = readStringValue(json, "description");
        String createdAtText = readStringValue(json, "createdAt");
        String updatedAtText = readStringValue(json, "updatedAt");
        List<PlanPage> pages = readPages(json);
        List<Bubble> bubbles = readBubbles(json);

        PlanDrawing drawing = null;
        if (json.contains("\"drawing\"")) {
            String drawingFileName = readStringValue(json, "fileName");
            String drawingPath = readStringValue(json, "storedPath");
            String drawingType = readStringValue(json, "fileType");
            if (!drawingFileName.isBlank() || !drawingPath.isBlank()) {
                drawing = new PlanDrawing(drawingFileName, drawingPath, drawingType);
            }
        }

        return new InspectionPlan(
                id,
                name,
                partNumber,
                revision,
                description,
                drawing,
                pages,
                bubbles,
                LocalDateTime.parse(createdAtText),
                LocalDateTime.parse(updatedAtText)
        );
    }

    private boolean isOwnedByCurrentUser(JsonNode indexNode, String ownerUid) {
        String indexedOwnerUid = indexNode.path("ownerUid").asText("");
        if (!indexedOwnerUid.isBlank()) {
            return ownerUid.equals(indexedOwnerUid);
        }

        String storagePath = indexNode.path("storagePath").asText("");
        return storagePath.contains("/users/" + ownerUid + "/plans/");
    }

    private InspectionPlan preparePlanForStorage(InspectionPlan plan, String ownerUid) throws IOException {
        List<PlanPage> storagePages = new ArrayList<>();
        for (PlanPage page : plan.getPages()) {
            PlanDrawing drawing = uploadDrawingIfNeeded(
                    page.getDrawing(),
                    pageDrawingObjectName(ownerUid, plan.getId(), page),
                    requireValidIdToken()
            );
            storagePages.add(new PlanPage(page.getId(), page.getName(), page.getPageNumber(), drawing));
        }

        PlanDrawing storageDrawing = plan.getDrawing();
        if (storagePages.isEmpty() && storageDrawing != null) {
            storageDrawing = uploadDrawingIfNeeded(
                    storageDrawing,
                    drawingObjectName(ownerUid, plan.getId(), storageDrawing.getFileName()),
                    requireValidIdToken()
            );
        } else if (!storagePages.isEmpty()) {
            storageDrawing = storagePages.getFirst().getDrawing();
        }

        return new InspectionPlan(
                plan.getId(),
                plan.getName(),
                plan.getPartNumber(),
                plan.getRevision(),
                plan.getDescription(),
                storageDrawing,
                storagePages,
                plan.getBubbles(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    private PlanDrawing uploadDrawingIfNeeded(PlanDrawing drawing, String objectName, String idToken) throws IOException {
        if (drawing == null || drawing.getStoredPath() == null || drawing.getStoredPath().isBlank()) {
            return drawing;
        }

        if (drawing.getStoredPath().startsWith("gs://")) {
            return drawing;
        }

        Path sourcePath = Path.of(drawing.getStoredPath());
        if (!Files.isRegularFile(sourcePath)) {
            return drawing;
        }

        byte[] bytes = Files.readAllBytes(sourcePath);
        uploadStorageObject(objectName, bytes, BINARY_MEDIA, idToken);
        return new PlanDrawing(drawing.getFileName(), toGsUri(objectName), drawing.getFileType());
    }

    private void hydrateRemoteDrawings(InspectionPlan plan, String ownerUid, String idToken) throws IOException {
        for (PlanPage page : plan.getPages()) {
            PlanDrawing drawing = page.getDrawing();
            if (drawing != null && drawing.getStoredPath() != null && drawing.getStoredPath().startsWith("gs://")) {
                page.setDrawing(downloadDrawingToCache(drawing, ownerUid, plan.getId(), page.getPageNumber(), idToken));
            }
        }

        if (!plan.getPages().isEmpty()) {
            plan.setPages(plan.getPages());
        } else if (plan.getDrawing() != null && plan.getDrawing().getStoredPath() != null && plan.getDrawing().getStoredPath().startsWith("gs://")) {
            PlanDrawing cachedDrawing = downloadDrawingToCache(plan.getDrawing(), ownerUid, plan.getId(), 1, idToken);
            plan.setDrawing(cachedDrawing);
        }
    }

    private PlanDrawing downloadDrawingToCache(
            PlanDrawing drawing,
            String ownerUid,
            String planId,
            int pageNumber,
            String idToken
    ) throws IOException {
        String objectName = objectNameFromGsUri(drawing.getStoredPath());
        byte[] bytes = downloadStorageObjectBytes(objectName, idToken);
        Path cacheDirectory = getCacheDirectory(ownerUid, planId).resolve("page-" + pageNumber);
        Files.createDirectories(cacheDirectory);
        Path cachedPath = cacheDirectory.resolve(drawing.getFileName());
        Files.write(cachedPath, bytes);
        return new PlanDrawing(drawing.getFileName(), cachedPath.toString(), drawing.getFileType());
    }

    private void savePlanIndex(InspectionPlan plan, String companyId, String ownerUid, String planObjectName, String idToken) throws IOException {
        String json = """
                {
                  "id": "%s",
                  "ownerUid": "%s",
                  "name": "%s",
                  "partNumber": "%s",
                  "revision": "%s",
                  "description": "%s",
                  "updatedAt": "%s",
                  "storagePath": "%s"
                }
                """.formatted(
                escape(plan.getId()),
                escape(ownerUid),
                escape(plan.getName()),
                escape(plan.getPartNumber()),
                escape(plan.getRevision()),
                escape(plan.getDescription()),
                plan.getUpdatedAt(),
                escape(toGsUri(planObjectName))
        );

        putDatabaseNode("planIndexes/" + companyId + "/" + plan.getId(), json, idToken);
    }

    private Path getCacheDirectory(String ownerUid, String planId) {
        return Path.of(System.getProperty("user.home"), APP_DIRECTORY_NAME, CACHE_DIRECTORY_NAME, ownerUid, planId);
    }

    private String requireValidIdToken() {
        String token = getValidIdTokenOrNull();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Sign in before saving or loading Firebase plans.");
        }
        return token;
    }

    private String getValidIdTokenOrNull() {
        try {
            String token = authRepository.getToken();
            String refreshToken = authRepository.getRefreshToken();
            if (token == null || token.isBlank()) {
                return null;
            }

            if (AuthRepository.isTokenExpired()) {
                if (refreshToken == null || refreshToken.isBlank()) {
                    return null;
                }
                var refreshResult = authService.refreshToken(refreshToken);
                if (!refreshResult.isSuccess()) {
                    return null;
                }
                authRepository.saveAuthResult(
                        refreshResult.getIdToken(),
                        refreshResult.getRefreshToken(),
                        refreshResult.getUid(),
                        authRepository.getEmail()
                );
                return refreshResult.getIdToken();
            }

            return token;
        } catch (Exception exception) {
            return null;
        }
    }

    private String refreshIdTokenOrNull() {
        try {
            String refreshToken = authRepository.getRefreshToken();
            if (refreshToken == null || refreshToken.isBlank()) {
                return null;
            }

            var refreshResult = authService.refreshToken(refreshToken);
            if (!refreshResult.isSuccess()) {
                return null;
            }

            authRepository.saveAuthResult(
                    refreshResult.getIdToken(),
                    refreshResult.getRefreshToken(),
                    refreshResult.getUid(),
                    authRepository.getEmail()
            );
            return refreshResult.getIdToken();
        } catch (Exception exception) {
            return null;
        }
    }

    private String requireCompanyId() {
        String companyId = getCompanyIdOrNull();
        if (companyId == null || companyId.isBlank()) {
            throw new IllegalStateException("Sign in to a company before saving or loading Firebase plans.");
        }
        return companyId;
    }

    private String getCompanyIdOrNull() {
        return authRepository.getCompanyId();
    }

    private String requireUid() {
        String uid = getUidOrNull();
        if (uid == null || uid.isBlank()) {
            throw new IllegalStateException("Sign in before saving or loading Firebase plans.");
        }
        return uid;
    }

    private String getUidOrNull() {
        return authRepository.getUid();
    }

    private void uploadStorageObject(String objectName, String content, MediaType mediaType, String idToken) throws IOException {
        uploadStorageObject(objectName, content.getBytes(StandardCharsets.UTF_8), mediaType, idToken);
    }

    private void uploadStorageObject(String objectName, byte[] content, MediaType mediaType, String idToken) throws IOException {
        String url = storageBaseUrl() + "?uploadType=media&name=" + encode(objectName);
        executeWithRefresh(idToken, token -> new Request.Builder()
                .url(url)
                .header("Authorization", "Firebase " + token)
                .post(RequestBody.create(content, mediaType))
                .build());
    }

    private String downloadStorageObject(String objectName, String idToken) throws IOException {
        return new String(downloadStorageObjectBytes(objectName, idToken), StandardCharsets.UTF_8);
    }

    private byte[] downloadStorageObjectBytes(String objectName, String idToken) throws IOException {
        String url = storageBaseUrl() + "/" + encode(objectName) + "?alt=media";
        try (Response response = executeForResponseWithRefresh(idToken, token -> new Request.Builder()
                .url(url)
                .header("Authorization", "Firebase " + token)
                .get()
                .build())) {
            if (response.body() == null) {
                return new byte[0];
            }
            return response.body().bytes();
        }
    }

    private void deleteStorageObjectsWithPrefix(String prefix, String idToken) throws IOException {
        String url = storageBaseUrl() + "?prefix=" + encode(prefix);
        try (Response response = executeForResponseWithRefresh(idToken, token -> new Request.Builder()
                .url(url)
                .header("Authorization", "Firebase " + token)
                .get()
                .build())) {
            if (response.body() == null) {
                return;
            }
            JsonNode root = mapper.readTree(response.body().string());
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return;
            }
            for (JsonNode item : items) {
                String name = item.path("name").asText("");
                if (!name.isBlank()) {
                    deleteStorageObject(name, idToken);
                }
            }
        }
    }

    private void deleteStorageObject(String objectName, String idToken) throws IOException {
        String url = storageBaseUrl() + "/" + encode(objectName);
        try (Response response = executeForResponseWithRefresh(idToken, token -> new Request.Builder()
                .url(url)
                .header("Authorization", "Firebase " + token)
                .delete()
                .build())) {
        }
    }

    private JsonNode readDatabaseNodeWithRefresh(String path) throws IOException {
        String idToken = requireValidIdToken();
        return readDatabaseNode(path, idToken);
    }

    private JsonNode readDatabaseNode(String path, String idToken) throws IOException {
        String url = databaseUrl + "/" + path + ".json?auth=" + encode(idToken);
        try (Response response = executeForResponseWithRefresh(idToken, token ->
                new Request.Builder()
                        .url(databaseUrl + "/" + path + ".json?auth=" + encode(token))
                        .get()
                        .build())) {
            if (response.body() == null) {
                return null;
            }
            return mapper.readTree(response.body().string());
        }
    }

    private void putDatabaseNode(String path, String json, String idToken) throws IOException {
        executeWithRefresh(idToken, token -> new Request.Builder()
                .url(databaseUrl + "/" + path + ".json?auth=" + encode(token))
                .put(RequestBody.create(json, JSON_MEDIA))
                .build());
    }

    private void deleteDatabaseNode(String path, String idToken) throws IOException {
        executeWithRefresh(idToken, token -> new Request.Builder()
                .url(databaseUrl + "/" + path + ".json?auth=" + encode(token))
                .delete()
                .build());
    }

    private void executeWithRefresh(String idToken, Function<String, Request> requestFactory) throws IOException {
        try (Response response = executeForResponseWithRefresh(idToken, requestFactory)) {
        }
    }

    private Response executeForResponseWithRefresh(String idToken, Function<String, Request> requestFactory) throws IOException {
        Response response = client.newCall(requestFactory.apply(idToken)).execute();
        if (response.code() == 401) {
            String refreshedToken = refreshIdTokenOrNull();
            if (refreshedToken != null && !refreshedToken.equals(idToken)) {
                response.close();
                response = client.newCall(requestFactory.apply(refreshedToken)).execute();
            }
        }

        if (!response.isSuccessful()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            int code = response.code();
            String message = response.message();
            String url = response.request().url().toString();
            response.close();
            throw new IOException("Firebase request failed: " + code + " " + message + " at " + url + " " + responseBody);
        }

        return response;
    }

    private void execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                throw new IOException("Firebase request failed: " + response.code() + " " + response.message()
                        + " at " + response.request().url() + " " + responseBody);
            }
        }
    }

    private String storageBaseUrl() {
        return "https://firebasestorage.googleapis.com/v0/b/" + storageBucket + "/o";
    }

    private String planObjectName(String ownerUid, String planId) {
        return "users/" + ownerUid + "/plans/" + planId + "/" + PLAN_FILE_NAME;
    }

    private String drawingObjectName(String ownerUid, String planId, String fileName) {
        return "users/" + ownerUid + "/plans/" + planId + "/" + DRAWING_DIRECTORY_NAME + "/" + sanitizeStorageName(fileName);
    }

    private String pageDrawingObjectName(String ownerUid, String planId, PlanPage page) {
        String fileName = page.getDrawing() == null ? "drawing" : page.getDrawing().getFileName();
        return "users/" + ownerUid + "/plans/" + planId + "/" + PAGES_DIRECTORY_NAME
                + "/page-" + page.getPageNumber() + "/" + sanitizeStorageName(fileName);
    }

    private String toGsUri(String objectName) {
        return "gs://" + storageBucket + "/" + objectName;
    }

    private String objectNameFromGsUri(String gsUri) {
        String prefix = "gs://" + storageBucket + "/";
        if (!gsUri.startsWith(prefix)) {
            throw new IllegalArgumentException("Unexpected Firebase Storage path: " + gsUri);
        }
        return gsUri.substring(prefix.length());
    }

    private String sanitizeStorageName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file";
        }
        return fileName.replaceAll("[\\\\/]", "_");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String buildJson(InspectionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"id\": \"").append(escape(plan.getId())).append("\",\n");
        builder.append("  \"name\": \"").append(escape(plan.getName())).append("\",\n");
        builder.append("  \"partNumber\": \"").append(escape(plan.getPartNumber())).append("\",\n");
        builder.append("  \"revision\": \"").append(escape(plan.getRevision())).append("\",\n");
        builder.append("  \"description\": \"").append(escape(plan.getDescription())).append("\",\n");
        builder.append("  \"createdAt\": \"").append(plan.getCreatedAt()).append("\",\n");
        builder.append("  \"updatedAt\": \"").append(plan.getUpdatedAt()).append("\"");

        PlanDrawing drawing = plan.getDrawing();
        if (drawing != null) {
            builder.append(",\n");
            builder.append("  \"drawing\": {\n");
            builder.append("    \"fileName\": \"").append(escape(drawing.getFileName())).append("\",\n");
            builder.append("    \"storedPath\": \"").append(escape(drawing.getStoredPath())).append("\",\n");
            builder.append("    \"fileType\": \"").append(escape(drawing.getFileType())).append("\"\n");
            builder.append("  }\n");
        }

        if (!plan.getPages().isEmpty()) {
            builder.append(",\n");
            builder.append("  \"pages\": [\n");
            for (int index = 0; index < plan.getPages().size(); index++) {
                appendPageJson(builder, plan.getPages().get(index));
                if (index < plan.getPages().size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("  ]\n");
        }

        if (!plan.getBubbles().isEmpty()) {
            builder.append(",\n");
            builder.append("  \"bubbles\": [\n");
            for (int index = 0; index < plan.getBubbles().size(); index++) {
                appendBubbleJson(builder, plan.getBubbles().get(index));
                if (index < plan.getBubbles().size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("  ]\n");
        } else {
            builder.append("\n");
        }

        builder.append("}\n");
        return builder.toString();
    }

    private void appendPageJson(StringBuilder builder, PlanPage page) {
        PlanDrawing drawing = page.getDrawing();
        builder.append("    {\n");
        builder.append("      \"id\": \"").append(escape(page.getId())).append("\",\n");
        builder.append("      \"name\": \"").append(escape(page.getName())).append("\",\n");
        builder.append("      \"pageNumber\": \"").append(page.getPageNumber()).append("\"");
        if (drawing != null) {
            builder.append(",\n");
            builder.append("      \"drawing\": {\n");
            builder.append("        \"fileName\": \"").append(escape(drawing.getFileName())).append("\",\n");
            builder.append("        \"storedPath\": \"").append(escape(drawing.getStoredPath())).append("\",\n");
            builder.append("        \"fileType\": \"").append(escape(drawing.getFileType())).append("\"\n");
            builder.append("      }\n");
        } else {
            builder.append("\n");
        }
        builder.append("    }");
    }

    private List<PlanPage> readPages(String json) {
        List<PlanPage> pages = new ArrayList<>();
        String arrayText = readArrayText(json, "pages");
        if (arrayText.isBlank()) {
            return pages;
        }

        for (String pageJson : readObjectTexts(arrayText)) {
            pages.add(readPage(pageJson));
        }

        return pages;
    }

    private PlanPage readPage(String json) {
        PlanDrawing drawing = null;
        if (json.contains("\"drawing\"")) {
            String drawingFileName = readStringValue(json, "fileName");
            String drawingPath = readStringValue(json, "storedPath");
            String drawingType = readStringValue(json, "fileType");
            if (!drawingFileName.isBlank() || !drawingPath.isBlank()) {
                drawing = new PlanDrawing(drawingFileName, drawingPath, drawingType);
            }
        }

        return new PlanPage(
                readStringValue(json, "id"),
                readStringValue(json, "name"),
                parseInteger(readStringValue(json, "pageNumber"), 0),
                drawing
        );
    }

    private void appendBubbleJson(StringBuilder builder, Bubble bubble) {
        builder.append("    {\n");
        builder.append("      \"id\": \"").append(escape(bubble.getId())).append("\",\n");
        builder.append("      \"pageId\": \"").append(escape(bubble.getPageId())).append("\",\n");
        builder.append("      \"x\": \"").append(bubble.getX()).append("\",\n");
        builder.append("      \"y\": \"").append(bubble.getY()).append("\",\n");
        builder.append("      \"radius\": \"").append(bubble.getRadius()).append("\",\n");
        builder.append("      \"useDefaultDiameter\": \"").append(bubble.isUseDefaultDiameter()).append("\",\n");
        builder.append("      \"color\": \"").append(escape(bubble.getColor())).append("\",\n");
        builder.append("      \"useDefaultColor\": \"").append(bubble.isUseDefaultColor()).append("\",\n");
        builder.append("      \"label\": \"").append(escape(bubble.getLabel())).append("\",\n");
        builder.append("      \"characteristic\": \"").append(escape(bubble.getCharacteristic())).append("\",\n");
        builder.append("      \"inspectionType\": \"").append(bubble.getInspectionType()).append("\",\n");
        builder.append("      \"nominalValue\": \"").append(nullableDouble(bubble.getNominalValue())).append("\",\n");
        builder.append("      \"lowerTolerance\": \"").append(nullableDouble(bubble.getLowerTolerance())).append("\",\n");
        builder.append("      \"upperTolerance\": \"").append(nullableDouble(bubble.getUpperTolerance())).append("\",\n");
        builder.append("      \"expectedPassFail\": \"").append(nullableBoolean(bubble.getExpectedPassFail())).append("\",\n");
        builder.append("      \"measuredValue\": \"").append(nullableDouble(bubble.getMeasuredValue())).append("\",\n");
        builder.append("      \"actualPassFail\": \"").append(nullableBoolean(bubble.getActualPassFail())).append("\",\n");
        builder.append("      \"status\": \"").append(bubble.getStatus()).append("\",\n");
        builder.append("      \"note\": \"").append(escape(bubble.getNote())).append("\",\n");
        builder.append("      \"sequenceNumber\": \"").append(bubble.getSequenceNumber()).append("\",\n");
        builder.append("      \"createdAt\": \"").append(bubble.getCreatedAt()).append("\",\n");
        builder.append("      \"updatedAt\": \"").append(bubble.getUpdatedAt()).append("\"\n");
        builder.append("    }");
    }

    private List<Bubble> readBubbles(String json) {
        List<Bubble> bubbles = new ArrayList<>();
        String arrayText = readArrayText(json, "bubbles");
        if (arrayText.isBlank()) {
            return bubbles;
        }

        for (String bubbleJson : readObjectTexts(arrayText)) {
            bubbles.add(readBubble(bubbleJson));
        }

        return bubbles;
    }

    private Bubble readBubble(String json) {
        return new Bubble(
                readStringValue(json, "id"),
                readStringValue(json, "pageId"),
                parseDouble(readStringValue(json, "x"), 0.0),
                parseDouble(readStringValue(json, "y"), 0.0),
                parseDouble(readStringValue(json, "radius"), 18.0),
                parseBoolean(readStringValue(json, "useDefaultDiameter"), true),
                readStringOrDefault(json, "color", "#E53935"),
                parseBoolean(readStringValue(json, "useDefaultColor"), true),
                readStringValue(json, "label"),
                readStringValue(json, "characteristic"),
                parseInspectionType(readStringValue(json, "inspectionType")),
                parseNullableDouble(readStringValue(json, "nominalValue")),
                parseNullableDouble(readStringValue(json, "lowerTolerance")),
                parseNullableDouble(readStringValue(json, "upperTolerance")),
                parseNullableBoolean(readStringValue(json, "expectedPassFail")),
                parseNullableDouble(readStringValue(json, "measuredValue")),
                parseNullableBoolean(readStringValue(json, "actualPassFail")),
                parseBubbleStatus(readStringValue(json, "status")),
                readStringValue(json, "note"),
                parseInteger(readStringValue(json, "sequenceNumber"), 0),
                parseLocalDateTime(readStringValue(json, "createdAt")),
                parseLocalDateTime(readStringValue(json, "updatedAt"))
        );
    }

    private String readArrayText(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }

        int openingBracketIndex = json.indexOf('[', keyIndex);
        if (openingBracketIndex < 0) {
            return "";
        }

        int closingBracketIndex = findMatchingBracket(json, openingBracketIndex, '[', ']');
        if (closingBracketIndex < 0) {
            return "";
        }

        return json.substring(openingBracketIndex + 1, closingBracketIndex);
    }

    private List<String> readObjectTexts(String arrayText) {
        List<String> objects = new ArrayList<>();
        int index = 0;
        while (index < arrayText.length()) {
            int openingBraceIndex = arrayText.indexOf('{', index);
            if (openingBraceIndex < 0) {
                break;
            }

            int closingBraceIndex = findMatchingBracket(arrayText, openingBraceIndex, '{', '}');
            if (closingBraceIndex < 0) {
                break;
            }

            objects.add(arrayText.substring(openingBraceIndex, closingBraceIndex + 1));
            index = closingBraceIndex + 1;
        }

        return objects;
    }

    private int findMatchingBracket(String text, int openingIndex, char openingCharacter, char closingCharacter) {
        int depth = 0;
        boolean inString = false;

        for (int index = openingIndex; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"' && (index == 0 || text.charAt(index - 1) != '\\')) {
                inString = !inString;
            }

            if (inString) {
                continue;
            }

            if (character == openingCharacter) {
                depth++;
            } else if (character == closingCharacter) {
                depth--;
            }

            if (depth == 0) {
                return index;
            }
        }

        return -1;
    }

    private String readStringValue(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            return "";
        }

        int colonIndex = json.indexOf(':', keyIndex);
        int openingQuoteIndex = json.indexOf('"', colonIndex + 1);
        int closingQuoteIndex = openingQuoteIndex + 1;

        while (closingQuoteIndex < json.length()) {
            closingQuoteIndex = json.indexOf('"', closingQuoteIndex);
            if (closingQuoteIndex < 0) {
                return "";
            }
            if (json.charAt(closingQuoteIndex - 1) != '\\') {
                break;
            }
            closingQuoteIndex++;
        }

        String value = json.substring(openingQuoteIndex + 1, closingQuoteIndex);
        return unescape(value);
    }

    private String readStringOrDefault(String json, String key, String defaultValue) {
        String value = readStringValue(json, key);
        return value.isBlank() ? defaultValue : value;
    }

    private InspectionType parseInspectionType(String value) {
        if (value == null || value.isBlank()) {
            return InspectionType.NUMERIC;
        }

        try {
            return InspectionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return InspectionType.NUMERIC;
        }
    }

    private BubbleStatus parseBubbleStatus(String value) {
        if (value == null || value.isBlank()) {
            return BubbleStatus.OPEN;
        }

        try {
            return BubbleStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return BubbleStatus.OPEN;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }

        return LocalDateTime.parse(value);
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(value);
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Double.parseDouble(value);
    }

    private Boolean parseNullableBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Boolean.parseBoolean(value);
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private int parseInteger(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private String nullableDouble(Double value) {
        return value == null ? "" : value.toString();
    }

    private String nullableBoolean(Boolean value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String unescape(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
