package service.firestore;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import service.auth.AuthService;
import service.config.FirebaseProjectConfig;
import service.config.FirebaseProjectConfigStore;
import service.session.SessionManager;
import service.session.UserSession;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreRestClient {
    private static final String FIRESTORE_BASE = "https://firestore.googleapis.com/v1";

    private final SessionManager sessionManager;
    private final AuthService authService;
    private final FirebaseProjectConfigStore configStore;
    private final HttpClient httpClient;
    private final Gson gson;

    public FirestoreRestClient(
            SessionManager sessionManager,
            AuthService authService,
            FirebaseProjectConfigStore configStore
    ) {
        this(sessionManager, authService, configStore, HttpClient.newHttpClient(), new Gson());
    }

    FirestoreRestClient(
            SessionManager sessionManager,
            AuthService authService,
            FirebaseProjectConfigStore configStore,
            HttpClient httpClient,
            Gson gson
    ) {
        this.sessionManager = sessionManager;
        this.authService = authService;
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public Optional<FirestoreDocument> getDocument(String documentPath) {
        HttpRequest request = authorizedRequest(documentUrl(documentPath))
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (isIgnorableDocumentNotFound(response)) {
            return Optional.empty();
        }
        ensureSuccess(response);
        return Optional.of(parseDocument(gson.fromJson(response.body(), JsonObject.class)));
    }

    public List<FirestoreDocument> listDocuments(String collectionPath) {
        HttpRequest request = authorizedRequest(collectionUrl(collectionPath))
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (isIgnorableCollectionNotFound(response)) {
            return List.of();
        }
        ensureSuccess(response);

        JsonObject body = gson.fromJson(response.body(), JsonObject.class);
        if (body == null || !body.has("documents")) {
            return List.of();
        }

        List<FirestoreDocument> documents = new ArrayList<>();
        for (JsonElement element : body.getAsJsonArray("documents")) {
            documents.add(parseDocument(element.getAsJsonObject()));
        }
        return documents;
    }

    public List<FirestoreDocument> runCollectionGroupQuery(String parentPath, String collectionId, Map<String, Object> equalFilters) {
        JsonObject structuredQuery = new JsonObject();
        JsonArray from = new JsonArray();
        JsonObject collection = new JsonObject();
        collection.addProperty("collectionId", collectionId);
        collection.addProperty("allDescendants", true);
        from.add(collection);
        structuredQuery.add("from", from);

        if (equalFilters != null && !equalFilters.isEmpty()) {
            structuredQuery.add("where", buildWhere(equalFilters));
        }

        JsonObject body = new JsonObject();
        body.add("structuredQuery", structuredQuery);

        HttpRequest request = authorizedRequest(runQueryUrl(parentPath))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<String> response = send(request);
        ensureSuccess(response);

        JsonArray results = gson.fromJson(response.body(), JsonArray.class);
        if (results == null) {
            return List.of();
        }

        List<FirestoreDocument> documents = new ArrayList<>();
        for (JsonElement element : results) {
            JsonObject result = element.getAsJsonObject();
            if (result.has("document")) {
                documents.add(parseDocument(result.getAsJsonObject("document")));
            }
        }
        return documents;
    }

    public void upsertDocument(String documentPath, Map<String, Object> fields) {
        Optional<FirestoreDocument> existing = getDocument(documentPath);
        if (existing.isPresent()) {
            patchDocument(documentPath, fields);
            return;
        }

        createDocument(documentPath, fields);
    }

    private void patchDocument(String documentPath, Map<String, Object> fields) {
        JsonObject body = new JsonObject();
        body.addProperty("name", fullDocumentName(documentPath));
        body.add("fields", encodeFields(fields));

        HttpRequest request = authorizedRequest(documentUrl(documentPath))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<String> response = send(request);
        ensureSuccess(response);
    }

    private void createDocument(String documentPath, Map<String, Object> fields) {
        int splitIndex = documentPath.lastIndexOf('/');
        if (splitIndex <= 0) {
            throw new FirestoreException("Invalid Firestore document path: " + documentPath);
        }

        String collectionPath = documentPath.substring(0, splitIndex);
        String documentId = documentPath.substring(splitIndex + 1);

        JsonObject body = new JsonObject();
        body.add("fields", encodeFields(fields));

        String url = collectionUrl(collectionPath) + "?documentId="
                + URLEncoder.encode(documentId, StandardCharsets.UTF_8);
        HttpRequest request = authorizedRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        HttpResponse<String> response = send(request);
        ensureSuccess(response);
    }

    public void deleteDocument(String documentPath) {
        HttpRequest request = authorizedRequest(documentUrl(documentPath))
                .DELETE()
                .build();
        HttpResponse<String> response = send(request);
        if (isIgnorableDocumentNotFound(response)) {
            return;
        }
        ensureSuccess(response);
    }

    public void deleteCollection(String collectionPath) {
        for (FirestoreDocument document : listDocuments(collectionPath)) {
            deleteDocument(document.path());
        }
    }

    public String currentUserId() {
        return sessionManager.requireCurrentSession().getUid();
    }

    private HttpRequest.Builder authorizedRequest(String url) {
        UserSession session = requireValidSession();
        return HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + session.getIdToken())
                .header("Content-Type", "application/json");
    }

    private UserSession requireValidSession() {
        UserSession session = sessionManager.requireCurrentSession();
        if (session.getIdToken() == null || session.getIdToken().isBlank()) {
            throw new FirestoreException("No Firebase session token is available.");
        }
        if (session.isExpired()) {
            session = authService.refreshSession(session);
        }
        return session;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new FirestoreException("Unable to reach Firestore.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FirestoreException("Firestore request was interrupted.", exception);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        String defaultMessage = "Firestore request failed with HTTP " + response.statusCode() + ".";
        String message = defaultMessage;
        try {
            JsonObject body = gson.fromJson(response.body(), JsonObject.class);
            if (body != null && body.has("error")) {
                JsonObject error = body.getAsJsonObject("error");
                if (error.has("message")) {
                    message = "Firestore request failed: " + error.get("message").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the generic message above.
        }
        if (defaultMessage.equals(message)
                && response.body() != null
                && !response.body().isBlank()) {
            message = message + " " + response.body();
        }

        if (response.statusCode() == 404 && message.contains("does not exist for project")) {
            message = message + " Verify the Firebase Project ID and Database ID on the Firebase setup screen.";
        }
        throw new FirestoreException(message);
    }

    private boolean isIgnorableDocumentNotFound(HttpResponse<String> response) {
        return response.statusCode() == 404 && !isMissingDatabaseResponse(response);
    }

    private boolean isIgnorableCollectionNotFound(HttpResponse<String> response) {
        return response.statusCode() == 404 && !isMissingDatabaseResponse(response);
    }

    private boolean isMissingDatabaseResponse(HttpResponse<String> response) {
        if (response.statusCode() != 404 || response.body() == null || response.body().isBlank()) {
            return false;
        }

        try {
            JsonObject body = gson.fromJson(response.body(), JsonObject.class);
            if (body != null && body.has("error")) {
                JsonObject error = body.getAsJsonObject("error");
                if (error.has("message")) {
                    String message = error.get("message").getAsString();
                    return message.contains("does not exist for project");
                }
            }
        } catch (RuntimeException ignored) {
            // Treat unreadable 404 responses as normal missing resources.
        }
        return false;
    }

    private FirestoreDocument parseDocument(JsonObject document) {
        String fullName = document.get("name").getAsString();
        String path = relativePath(fullName);
        String id = path.substring(path.lastIndexOf('/') + 1);
        Map<String, Object> fields = document.has("fields")
                ? decodeFields(document.getAsJsonObject("fields"))
                : Map.of();
        return new FirestoreDocument(path, id, fields);
    }

    private JsonObject encodeFields(Map<String, Object> fields) {
        JsonObject result = new JsonObject();
        if (fields == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            result.add(entry.getKey(), encodeValue(entry.getValue()));
        }
        return result;
    }

    private JsonObject encodeValue(Object value) {
        JsonObject encoded = new JsonObject();
        if (value == null) {
            encoded.add("nullValue", JsonNull.INSTANCE);
            return encoded;
        }
        if (value instanceof String text) {
            encoded.addProperty("stringValue", text);
            return encoded;
        }
        if (value instanceof Boolean bool) {
            encoded.addProperty("booleanValue", bool);
            return encoded;
        }
        if (value instanceof Integer integer) {
            encoded.addProperty("integerValue", integer.toString());
            return encoded;
        }
        if (value instanceof Long longValue) {
            encoded.addProperty("integerValue", longValue.toString());
            return encoded;
        }
        if (value instanceof Float floatValue) {
            encoded.addProperty("doubleValue", floatValue);
            return encoded;
        }
        if (value instanceof Double doubleValue) {
            encoded.addProperty("doubleValue", doubleValue);
            return encoded;
        }
        if (value instanceof Enum<?> enumValue) {
            encoded.addProperty("stringValue", enumValue.name());
            return encoded;
        }
        if (value instanceof LocalDateTime dateTime) {
            Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
            encoded.addProperty("timestampValue", instant.toString());
            return encoded;
        }
        if (value instanceof byte[] bytes) {
            encoded.addProperty("bytesValue", Base64.getEncoder().encodeToString(bytes));
            return encoded;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject mapValue = new JsonObject();
            JsonObject fieldValues = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                fieldValues.add(String.valueOf(entry.getKey()), encodeValue(entry.getValue()));
            }
            mapValue.add("fields", fieldValues);
            encoded.add("mapValue", mapValue);
            return encoded;
        }
        if (value instanceof Iterable<?> iterable) {
            JsonArray values = new JsonArray();
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                values.add(encodeValue(item));
            }
            JsonObject arrayValue = new JsonObject();
            arrayValue.add("values", values);
            encoded.add("arrayValue", arrayValue);
            return encoded;
        }
        throw new FirestoreException("Unsupported Firestore value type: " + value.getClass().getName());
    }

    private Map<String, Object> decodeFields(JsonObject fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
            result.put(entry.getKey(), decodeValue(entry.getValue().getAsJsonObject()));
        }
        return result;
    }

    private Object decodeValue(JsonObject value) {
        if (value.has("nullValue")) {
            return null;
        }
        if (value.has("stringValue")) {
            return value.get("stringValue").getAsString();
        }
        if (value.has("booleanValue")) {
            return value.get("booleanValue").getAsBoolean();
        }
        if (value.has("integerValue")) {
            return value.get("integerValue").getAsLong();
        }
        if (value.has("doubleValue")) {
            return value.get("doubleValue").getAsDouble();
        }
        if (value.has("timestampValue")) {
            Instant instant = Instant.parse(value.get("timestampValue").getAsString());
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value.has("bytesValue")) {
            return Base64.getDecoder().decode(value.get("bytesValue").getAsString());
        }
        if (value.has("mapValue")) {
            JsonObject mapValue = value.getAsJsonObject("mapValue");
            if (!mapValue.has("fields")) {
                return new LinkedHashMap<String, Object>();
            }
            return decodeFields(mapValue.getAsJsonObject("fields"));
        }
        if (value.has("arrayValue")) {
            JsonObject arrayValue = value.getAsJsonObject("arrayValue");
            List<Object> items = new ArrayList<>();
            if (arrayValue.has("values")) {
                for (JsonElement item : arrayValue.getAsJsonArray("values")) {
                    items.add(decodeValue(item.getAsJsonObject()));
                }
            }
            return items;
        }
        throw new FirestoreException("Unsupported Firestore value payload.");
    }

    private JsonObject buildWhere(Map<String, Object> equalFilters) {
        if (equalFilters.size() == 1) {
            Map.Entry<String, Object> entry = equalFilters.entrySet().iterator().next();
            return buildFieldFilter(entry.getKey(), entry.getValue());
        }

        JsonArray filters = new JsonArray();
        for (Map.Entry<String, Object> entry : equalFilters.entrySet()) {
            filters.add(buildFieldFilter(entry.getKey(), entry.getValue()));
        }

        JsonObject composite = new JsonObject();
        composite.addProperty("op", "AND");
        composite.add("filters", filters);

        JsonObject where = new JsonObject();
        where.add("compositeFilter", composite);
        return where;
    }

    private JsonObject buildFieldFilter(String fieldName, Object value) {
        JsonObject fieldReference = new JsonObject();
        fieldReference.addProperty("fieldPath", fieldName);

        JsonObject fieldFilter = new JsonObject();
        fieldFilter.add("field", fieldReference);
        fieldFilter.addProperty("op", "EQUAL");
        fieldFilter.add("value", encodeValue(value));

        JsonObject where = new JsonObject();
        where.add("fieldFilter", fieldFilter);
        return where;
    }

    private String collectionUrl(String collectionPath) {
        return documentsRootUrl() + "/" + collectionPath;
    }

    private String documentUrl(String documentPath) {
        return documentsRootUrl() + "/" + documentPath;
    }

    private String runQueryUrl(String parentPath) {
        return documentsRootUrl() + (parentPath == null || parentPath.isBlank() ? "" : "/" + parentPath) + ":runQuery";
    }

    private String documentsRootUrl() {
        FirebaseProjectConfig config = requireConfig();
        return FIRESTORE_BASE + "/projects/" + config.projectId()
                + "/databases/" + encodePathSegment(config.resolvedDatabaseId())
                + "/documents";
    }

    private String fullDocumentName(String documentPath) {
        FirebaseProjectConfig config = requireConfig();
        return "projects/" + config.projectId()
                + "/databases/" + config.resolvedDatabaseId()
                + "/documents/" + documentPath;
    }

    private String relativePath(String fullName) {
        FirebaseProjectConfig config = requireConfig();
        String prefix = "projects/" + config.projectId()
                + "/databases/" + config.resolvedDatabaseId()
                + "/documents/";
        if (!fullName.startsWith(prefix)) {
            throw new FirestoreException("Unexpected Firestore document path: " + fullName);
        }
        return fullName.substring(prefix.length());
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private FirebaseProjectConfig requireConfig() {
        return configStore.load()
                .orElseThrow(() -> new FirestoreException("Firebase project settings are missing."));
    }

    public record FirestoreDocument(String path, String id, Map<String, Object> fields) {
    }
}
