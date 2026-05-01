package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.auth.LoginResult;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public class CompanyService {
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String databaseUrl;

    public CompanyService() {
        FirebaseAppService.FirebaseConfig config = FirebaseAppService.loadConfig();
        this.databaseUrl = config.databaseUrl();
    }

    public CompanySession authenticateCompany(String companyName, LoginResult authResult, String email) throws IOException {
        String slug = slugifyCompanyName(companyName);
        JsonNode slugNode = readNode("companySlugs/" + slug, authResult.getIdToken());
        if (isMissing(slugNode)) {
            throw new IllegalStateException("Company was not found.");
        }

        String companyId = slugNode.path("companyId").asText("");
        if (companyId.isBlank()) {
            throw new IllegalStateException("Company record is missing its id.");
        }

        JsonNode membership = readNode("companyUsers/" + companyId + "/" + authResult.getUid(), authResult.getIdToken());
        if (isMissing(membership)) {
            throw new IllegalStateException("This account is not a member of that company.");
        }

        String resolvedName = readCompanyName(companyId, authResult.getIdToken(), companyName);
        String role = membership.path("role").asText("member");
        return new CompanySession(companyId, resolvedName, role);
    }

    public CompanySession createCompany(String companyName, LoginResult authResult, String email) throws IOException {
        String normalizedCompanyName = normalizeCompanyName(companyName);
        String slug = slugifyCompanyName(normalizedCompanyName);
        JsonNode existingSlug = readNode("companySlugs/" + slug, authResult.getIdToken());
        if (!isMissing(existingSlug)) {
            throw new IllegalStateException("A company with that name already exists.");
        }

        String companyId = slug;
        String now = LocalDateTime.now().toString();

        ObjectNode slugNode = mapper.createObjectNode();
        slugNode.put("companyId", companyId);
        putNode("companySlugs/" + slug, slugNode, authResult.getIdToken());

        ObjectNode companyNode = mapper.createObjectNode();
        companyNode.put("id", companyId);
        companyNode.put("name", normalizedCompanyName);
        companyNode.put("slug", slug);
        companyNode.put("createdBy", authResult.getUid());
        companyNode.put("createdAt", now);
        putNode("companies/" + companyId, companyNode, authResult.getIdToken());

        writeMembership(companyId, normalizedCompanyName, authResult.getUid(), email, "admin", authResult.getIdToken());
        return new CompanySession(companyId, normalizedCompanyName, "admin");
    }

    public CompanySession acceptInvite(String inviteCode, LoginResult authResult, String email) throws IOException {
        String normalizedCode = normalizeInviteCode(inviteCode);
        JsonNode invite = readNode("invites/" + normalizedCode, authResult.getIdToken());
        if (isMissing(invite)) {
            throw new IllegalStateException("Invite code was not found.");
        }

        String status = invite.path("status").asText("open");
        if (!"open".equals(status)) {
            throw new IllegalStateException("Invite code has already been used or closed.");
        }

        String invitedEmail = invite.path("email").asText("");
        if (!invitedEmail.isBlank() && !invitedEmail.equalsIgnoreCase(email)) {
            throw new IllegalStateException("Invite code does not match this email address.");
        }

        String companyId = invite.path("companyId").asText("");
        String companyName = invite.path("companyName").asText("");
        if (companyId.isBlank()) {
            throw new IllegalStateException("Invite is missing a company.");
        }

        if (companyName.isBlank()) {
            companyName = readCompanyName(companyId, authResult.getIdToken(), companyId);
        }

        writeMembership(companyId, companyName, authResult.getUid(), email, "member", authResult.getIdToken());

        ObjectNode inviteUpdate = mapper.createObjectNode();
        inviteUpdate.put("status", "accepted");
        inviteUpdate.put("acceptedBy", authResult.getUid());
        inviteUpdate.put("acceptedAt", LocalDateTime.now().toString());
        patchNode("invites/" + normalizedCode, inviteUpdate, authResult.getIdToken());

        return new CompanySession(companyId, companyName, "member");
    }

    public String createInvite(String companyName, String inviteEmail, LoginResult authResult, String signerEmail) throws IOException {
        CompanySession company = authenticateCompany(companyName, authResult, signerEmail);
        if (!"admin".equals(company.role())) {
            throw new IllegalStateException("Only company admins can create invites.");
        }

        String normalizedEmail = normalizeEmail(inviteEmail);
        String code = generateInviteCode();
        String now = LocalDateTime.now().toString();

        ObjectNode inviteNode = mapper.createObjectNode();
        inviteNode.put("code", code);
        inviteNode.put("companyId", company.companyId());
        inviteNode.put("companyName", company.companyName());
        inviteNode.put("email", normalizedEmail);
        inviteNode.put("createdBy", authResult.getUid());
        inviteNode.put("createdAt", now);
        inviteNode.put("status", "open");
        putNode("invites/" + code, inviteNode, authResult.getIdToken());

        ObjectNode companyInviteNode = mapper.createObjectNode();
        companyInviteNode.put("email", normalizedEmail);
        companyInviteNode.put("createdAt", now);
        companyInviteNode.put("status", "open");
        putNode("companyInvites/" + company.companyId() + "/" + code, companyInviteNode, authResult.getIdToken());

        return code;
    }

    private void writeMembership(String companyId, String companyName, String uid, String email, String role, String idToken) throws IOException {
        String now = LocalDateTime.now().toString();

        ObjectNode companyUserNode = mapper.createObjectNode();
        companyUserNode.put("uid", uid);
        companyUserNode.put("email", normalizeEmail(email));
        companyUserNode.put("role", role);
        companyUserNode.put("joinedAt", now);
        putNode("companyUsers/" + companyId + "/" + uid, companyUserNode, idToken);

        ObjectNode userCompanyNode = mapper.createObjectNode();
        userCompanyNode.put("companyId", companyId);
        userCompanyNode.put("companyName", companyName);
        userCompanyNode.put("role", role);
        putNode("userCompanies/" + uid + "/" + companyId, userCompanyNode, idToken);
    }

    private String readCompanyName(String companyId, String idToken, String fallbackName) throws IOException {
        JsonNode company = readNode("companies/" + companyId, idToken);
        if (isMissing(company)) {
            return fallbackName;
        }
        return company.path("name").asText(fallbackName);
    }

    private JsonNode readNode(String path, String idToken) throws IOException {
        Request request = new Request.Builder()
                .url(databaseUrl + "/" + path + ".json?auth=" + encode(idToken))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Firebase request failed: " + response.code() + " " + response.message() + " " + responseBody);
            }
            return mapper.readTree(responseBody);
        }
    }

    private void putNode(String path, JsonNode json, String idToken) throws IOException {
        Request request = new Request.Builder()
                .url(databaseUrl + "/" + path + ".json?auth=" + encode(idToken))
                .put(RequestBody.create(json.toString(), JSON_MEDIA))
                .build();
        execute(request);
    }

    private void patchNode(String path, JsonNode json, String idToken) throws IOException {
        Request request = new Request.Builder()
                .url(databaseUrl + "/" + path + ".json?auth=" + encode(idToken))
                .patch(RequestBody.create(json.toString(), JSON_MEDIA))
                .build();
        execute(request);
    }

    private void execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                throw new IOException("Firebase request failed: " + response.code() + " " + response.message() + " " + responseBody);
            }
        }
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private String normalizeCompanyName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required.");
        }
        return companyName.trim();
    }

    private String slugifyCompanyName(String companyName) {
        String normalizedName = normalizeCompanyName(companyName).toLowerCase(Locale.ROOT);
        String slug = normalizedName
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Company name must contain letters or numbers.");
        }
        return slug;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new IllegalArgumentException("Invite code is required.");
        }
        return inviteCode.trim().toUpperCase(Locale.ROOT);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record CompanySession(String companyId, String companyName, String role) {
    }
}
