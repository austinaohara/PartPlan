package service.autoballoon;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import service.config.AutoBalloonConfig;
import service.config.AutoBalloonConfigStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public class OpenAiAutoBalloonService implements AutoBalloonDetectionService {
    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";

    private final AutoBalloonConfigStore configStore;
    private final HttpClient httpClient;
    private final Gson gson;

    public OpenAiAutoBalloonService(AutoBalloonConfigStore configStore) {
        this(configStore, HttpClient.newHttpClient(), new Gson());
    }

    OpenAiAutoBalloonService(
            AutoBalloonConfigStore configStore,
            HttpClient httpClient,
            Gson gson
    ) {
        this.configStore = configStore;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public List<AutoBalloonCandidate> detectBalloons(AutoBalloonRequest request) {
        validateRequest(request);

        AutoBalloonConfig config = configStore.load()
                .filter(AutoBalloonConfig::isComplete)
                .orElseThrow(() -> new AutoBalloonException("Auto-balloon settings are missing or incomplete."));

        String imageDataUrl = encodeImageAsDataUrl(request.imagePath());
        JsonObject requestBody = buildRequestBody(config, request, imageDataUrl);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(RESPONSES_URL))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(httpRequest);
        ensureSuccess(response);

        JsonObject body = gson.fromJson(response.body(), JsonObject.class);
        String outputText = extractOutputText(body);
        DetectionResponse detectionResponse;
        try {
            detectionResponse = gson.fromJson(outputText, DetectionResponse.class);
        } catch (RuntimeException exception) {
            throw new AutoBalloonException("OpenAI returned malformed auto-balloon JSON.", exception);
        }

        if (detectionResponse == null || detectionResponse.candidates == null) {
            throw new AutoBalloonException("OpenAI returned no readable auto-balloon JSON.");
        }

        return detectionResponse.candidates;
    }

    private void validateRequest(AutoBalloonRequest request) {
        if (request == null) {
            throw new AutoBalloonException("Auto-balloon request is missing.");
        }
        if (request.imagePath() == null || request.imagePath().toString().isBlank()) {
            throw new AutoBalloonException("The selected page image path is missing.");
        }
        if (Files.notExists(request.imagePath())) {
            throw new AutoBalloonException("The selected page image could not be found on disk.");
        }
    }

    private JsonObject buildRequestBody(AutoBalloonConfig config, AutoBalloonRequest request, String imageDataUrl) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.resolvedModel());
        body.addProperty("temperature", 0.1);
        body.addProperty("max_output_tokens", 3000);
        body.addProperty("instructions", """
                You are analyzing a manufacturing inspection drawing page.
                This is an assistive extraction step and may not be 100 percent accurate. Prefer omitting a candidate or leaving numeric fields null instead of guessing.
                Return only inspectable callouts that should receive balloons.
                Include dimensions, GD&T callouts, and numbered notes.
                Exclude title block data, revision tables, material specs, sheet metadata, border labels, and unrelated text.
                For each candidate, set characteristic to the most specific type you can determine, such as Linear Dimension, Diameter, Radius, Note, Position, Parallelism, Perpendicularity, Flatness, Straightness, Circular Runout, Total Runout, Profile, Angle, Chamfer, or Thread.
                For notes, set characteristic to Note and populate noteText.
                For numeric callouts, preserve the visible text in detectedText and extract nominal and tolerances when visible.
                If a dimension does not show an explicit tolerance near the callout, check the page's general tolerance box or title-block tolerance table and apply the matching tolerance only if you can determine it confidently from the drawing. Otherwise leave the tolerance fields null.
                Use anchorX and anchorY as normalized 0 to 1 coordinates for the center of the callout on the full page image.
                """);
        body.add("input", buildInput(request, imageDataUrl));
        body.add("text", buildTextFormat());
        return body;
    }

    private JsonArray buildInput(AutoBalloonRequest request, String imageDataUrl) {
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject prompt = new JsonObject();
        prompt.addProperty("type", "input_text");
        prompt.addProperty("text", """
                Analyze page "%s".
                The image width is %d pixels and the image height is %d pixels.
                Return the balloon candidates as structured JSON.
                """.formatted(request.pageName(), request.imageWidth(), request.imageHeight()));
        content.add(prompt);

        JsonObject image = new JsonObject();
        image.addProperty("type", "input_image");
        image.addProperty("image_url", imageDataUrl);
        image.addProperty("detail", "high");
        content.add(image);

        userMessage.add("content", content);

        JsonArray input = new JsonArray();
        input.add(userMessage);
        return input;
    }

    private JsonObject buildTextFormat() {
        JsonObject text = new JsonObject();
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "auto_balloon_candidates");
        format.addProperty("strict", true);
        format.add("schema", buildSchema());
        text.add("format", format);
        return text;
    }

    private JsonObject buildSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);

        JsonObject properties = new JsonObject();
        JsonObject candidates = new JsonObject();
        candidates.addProperty("type", "array");
        candidates.add("items", buildCandidateSchema());
        properties.add("candidates", candidates);
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("candidates");
        schema.add("required", required);
        return schema;
    }

    private JsonObject buildCandidateSchema() {
        JsonObject candidate = new JsonObject();
        candidate.addProperty("type", "object");
        candidate.addProperty("additionalProperties", false);

        JsonObject properties = new JsonObject();
        properties.add("characteristic", stringSchema());
        properties.add("detectedText", stringSchema());
        properties.add("nominal", nullableNumberSchema());
        properties.add("lowerTolerance", nullableNumberSchema());
        properties.add("upperTolerance", nullableNumberSchema());
        properties.add("anchorX", boundedNumberSchema());
        properties.add("anchorY", boundedNumberSchema());
        properties.add("noteText", stringSchema());
        candidate.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("characteristic");
        required.add("detectedText");
        required.add("nominal");
        required.add("lowerTolerance");
        required.add("upperTolerance");
        required.add("anchorX");
        required.add("anchorY");
        required.add("noteText");
        candidate.add("required", required);
        return candidate;
    }

    private JsonObject stringSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        return schema;
    }

    private JsonObject nullableNumberSchema() {
        JsonObject schema = new JsonObject();
        JsonArray types = new JsonArray();
        types.add("number");
        types.add("null");
        schema.add("type", types);
        return schema;
    }

    private JsonObject boundedNumberSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "number");
        schema.addProperty("minimum", 0.0);
        schema.addProperty("maximum", 1.0);
        return schema;
    }

    private String encodeImageAsDataUrl(Path imagePath) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(imagePath);
        } catch (IOException exception) {
            throw new AutoBalloonException("Unable to read the selected page image for auto-ballooning.", exception);
        }

        String mimeType = mimeTypeFor(imagePath);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return "data:%s;base64,%s".formatted(mimeType, encoded);
    }

    private String mimeTypeFor(Path imagePath) {
        String fileName = imagePath.getFileName() == null ? "" : imagePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        throw new AutoBalloonException("The selected page image format is not supported by OpenAI vision.");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new AutoBalloonException("Unable to reach OpenAI.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AutoBalloonException("OpenAI request was interrupted.", exception);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        String message = "OpenAI request failed with HTTP " + response.statusCode() + ".";
        try {
            JsonObject body = gson.fromJson(response.body(), JsonObject.class);
            if (body != null && body.has("error")) {
                JsonObject error = body.getAsJsonObject("error");
                if (error != null && error.has("message")) {
                    message = "OpenAI request failed: " + error.get("message").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        throw new AutoBalloonException(message);
    }

    private String extractOutputText(JsonObject body) {
        if (body == null) {
            throw new AutoBalloonException("OpenAI returned no readable auto-balloon JSON.");
        }
        if (body.has("output_text") && body.get("output_text").isJsonPrimitive()) {
            return body.get("output_text").getAsString();
        }
        if (!body.has("output") || !body.get("output").isJsonArray()) {
            throw new AutoBalloonException("OpenAI returned no readable auto-balloon JSON.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonElement outputElement : body.getAsJsonArray("output")) {
            if (!outputElement.isJsonObject()) {
                continue;
            }
            JsonObject outputObject = outputElement.getAsJsonObject();
            if (!outputObject.has("content") || !outputObject.get("content").isJsonArray()) {
                continue;
            }
            for (JsonElement contentElement : outputObject.getAsJsonArray("content")) {
                if (!contentElement.isJsonObject()) {
                    continue;
                }
                JsonObject contentObject = contentElement.getAsJsonObject();
                if (contentObject.has("text") && contentObject.get("text").isJsonPrimitive()) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(contentObject.get("text").getAsString());
                }
            }
        }

        if (text.isEmpty()) {
            throw new AutoBalloonException("OpenAI returned no readable auto-balloon JSON.");
        }
        return text.toString();
    }

    private static final class DetectionResponse {
        private List<AutoBalloonCandidate> candidates;
    }
}
