package service.firestore;

import app.AppStoragePaths;
import model.Bubble;
import model.BubbleStatus;
import model.InspectionPlan;
import model.InspectionType;
import model.PlanDrawing;
import model.PlanPage;
import model.PlanStatus;
import service.repository.PlanRepository;
import service.util.ModelCopies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class FirestorePlanRepository implements PlanRepository {
    private static final int PAGE_CHUNK_SIZE = 256 * 1024;

    private final FirestoreRestClient firestore;

    public FirestorePlanRepository(FirestoreRestClient firestore) {
        this.firestore = firestore;
    }

    @Override
    public void savePlan(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (!plan.isPending()) {
            throw new IllegalStateException("Complete plans are read-only. Create a revision to make changes.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (plan.getCreatedAt() == null) {
            plan.setCreatedAt(now);
        }
        plan.setUpdatedAt(now);

        syncPlanVersion(plan);
        updateFamilySummary(plan.getFamilyId());
    }

    @Override
    public List<InspectionPlan> loadPlans() {
        return loadAllVersionSummaries().stream()
                .sorted(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed())
                .map(ModelCopies::copyPlan)
                .toList();
    }

    @Override
    public List<InspectionPlan> loadCompletePlans() {
        return loadAllVersionSummaries().stream()
                .filter(InspectionPlan::isComplete)
                .sorted(Comparator.comparing(InspectionPlan::getUpdatedAt).reversed())
                .map(ModelCopies::copyPlan)
                .toList();
    }

    @Override
    public InspectionPlan loadPlan(String planId) {
        FirestoreRestClient.FirestoreDocument versionDocument = requireVersionDocument(planId);
        Map<String, Object> fields = versionDocument.fields();
        String familyId = stringValue(fields, "familyId", planId);

        List<PlanPage> pages = loadPages(familyId, planId);
        List<Bubble> bubbles = loadBubbles(familyId, planId);

        InspectionPlan plan = new InspectionPlan(
                planId,
                familyId,
                stringValue(fields, "name", ""),
                stringValue(fields, "partNumber", ""),
                stringValue(fields, "revision", ""),
                stringValue(fields, "description", ""),
                pages.isEmpty() ? null : ModelCopies.copyDrawing(pages.getFirst().getDrawing()),
                pages,
                bubbles,
                intValue(fields, "version", 0),
                enumValue(PlanStatus.class, fields.get("status"), PlanStatus.PENDING),
                dateTimeValue(fields.get("completedAt")),
                dateTimeValue(fields.get("createdAt")),
                dateTimeValue(fields.get("updatedAt"))
        );
        return ModelCopies.copyPlan(plan);
    }

    @Override
    public InspectionPlan completePlan(String planId) {
        InspectionPlan plan = loadPlan(planId);
        if (!plan.isPending()) {
            throw new IllegalStateException("Only pending plans can be completed.");
        }

        int nextVersion = nextCompletedVersion(plan.getFamilyId());
        plan.markComplete(Math.max(nextVersion, plan.getVersion()), LocalDateTime.now());
        upsertDocumentIfChanged(versionDocumentPath(plan.getFamilyId(), plan.getId()), versionFields(plan));
        updateFamilySummary(plan.getFamilyId());
        return ModelCopies.copyPlan(plan);
    }

    @Override
    public InspectionPlan createRevision(String planId) {
        InspectionPlan plan = loadPlan(planId);
        if (!plan.isComplete()) {
            throw new IllegalStateException("Only complete plans can create a revision.");
        }

        InspectionPlan existingDraft = loadFamilyVersions(plan.getFamilyId()).stream()
                .filter(InspectionPlan::isPending)
                .max(Comparator.comparing(InspectionPlan::getUpdatedAt))
                .orElse(null);
        if (existingDraft != null) {
            return ModelCopies.copyPlan(existingDraft);
        }

        int nextVersion = nextCompletedVersion(plan.getFamilyId());
        InspectionPlan revision = new InspectionPlan(
                UUID.randomUUID().toString(),
                plan.getFamilyId(),
                plan.getName(),
                plan.getPartNumber(),
                plan.getRevision(),
                plan.getDescription(),
                ModelCopies.copyDrawing(plan.getDrawing()),
                plan.getPages().stream().map(ModelCopies::copyPage).toList(),
                plan.getBubbles().stream().map(ModelCopies::copyBubble).toList(),
                nextVersion,
                PlanStatus.PENDING,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        syncPlanVersion(revision);
        updateFamilySummary(revision.getFamilyId());
        return ModelCopies.copyPlan(revision);
    }

    @Override
    public void deletePlan(String planId) {
        FirestoreRestClient.FirestoreDocument versionDocument = requireVersionDocument(planId);
        String familyId = stringValue(versionDocument.fields(), "familyId", planId);

        for (PlanPage page : loadPages(familyId, planId)) {
            deletePage(pageCollectionPath(familyId, planId), page.getId());
        }
        firestore.deleteCollection(bubbleCollectionPath(familyId, planId));
        firestore.deleteDocument(versionDocument.path());

        List<InspectionPlan> remaining = loadFamilyVersions(familyId);
        if (remaining.isEmpty()) {
            firestore.deleteDocument(familyDocumentPath(familyId));
            return;
        }

        updateFamilySummary(familyId);
    }

    private void syncPlanVersion(InspectionPlan plan) {
        String familyId = plan.getFamilyId();
        String planId = plan.getId();

        upsertFamilyDocument(plan);
        upsertDocumentIfChanged(versionDocumentPath(familyId, planId), versionFields(plan));
        syncPages(plan);
        syncBubbles(plan);
    }

    private void upsertFamilyDocument(InspectionPlan plan) {
        List<InspectionPlan> familyVersions = new ArrayList<>(loadFamilyVersions(plan.getFamilyId()));
        familyVersions.removeIf(existing -> existing.getId().equals(plan.getId()));
        familyVersions.add(ModelCopies.copyPlan(plan));
        upsertDocumentIfChanged(
                familyDocumentPath(plan.getFamilyId()),
                familyFields(plan.getFamilyId(), familyVersions)
        );
    }

    private void updateFamilySummary(String familyId) {
        List<InspectionPlan> familyVersions = loadFamilyVersions(familyId);
        if (familyVersions.isEmpty()) {
            firestore.deleteDocument(familyDocumentPath(familyId));
            return;
        }
        upsertDocumentIfChanged(familyDocumentPath(familyId), familyFields(familyId, familyVersions));
    }

    private Map<String, Object> familyFields(String familyId, List<InspectionPlan> versions) {
        InspectionPlan latestUpdated = versions.stream()
                .max(Comparator.comparing(InspectionPlan::getUpdatedAt))
                .orElseThrow();
        InspectionPlan latestComplete = versions.stream()
                .filter(InspectionPlan::isComplete)
                .max(Comparator.comparingInt(InspectionPlan::getVersion))
                .orElse(null);
        InspectionPlan latestDraft = versions.stream()
                .filter(InspectionPlan::isPending)
                .max(Comparator.comparing(InspectionPlan::getUpdatedAt))
                .orElse(null);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("familyId", familyId);
        fields.put("name", latestUpdated.getName());
        fields.put("partNumber", latestUpdated.getPartNumber());
        fields.put("latestCompleteVersion", latestComplete == null ? 0 : latestComplete.getVersion());
        fields.put("latestCompletePlanId", latestComplete == null ? "" : latestComplete.getId());
        fields.put("activeDraftPlanId", latestDraft == null ? "" : latestDraft.getId());
        fields.put("createdAt", earliestCreatedAt(versions));
        fields.put("updatedAt", latestUpdated.getUpdatedAt());
        return fields;
    }

    private LocalDateTime earliestCreatedAt(List<InspectionPlan> versions) {
        return versions.stream()
                .map(InspectionPlan::getCreatedAt)
                .filter(value -> value != null)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }

    private Map<String, Object> versionFields(InspectionPlan plan) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("planId", plan.getId());
        fields.put("familyId", plan.getFamilyId());
        fields.put("name", plan.getName());
        fields.put("partNumber", plan.getPartNumber());
        fields.put("revision", plan.getRevision());
        fields.put("description", plan.getDescription());
        fields.put("version", plan.getVersion());
        fields.put("status", plan.getStatus().name());
        fields.put("createdAt", plan.getCreatedAt());
        fields.put("updatedAt", plan.getUpdatedAt());
        fields.put("completedAt", plan.getCompletedAt());
        return fields;
    }

    private void syncPages(InspectionPlan plan) {
        String collectionPath = pageCollectionPath(plan.getFamilyId(), plan.getId());
        Map<String, FirestoreRestClient.FirestoreDocument> existingPages = new HashMap<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(collectionPath)) {
            existingPages.put(document.id(), document);
        }

        for (PlanPage page : plan.getPages()) {
            FirestoreRestClient.FirestoreDocument existingPage = existingPages.remove(page.getId());
            syncPage(plan, page, existingPage);
        }

        for (String removedPageId : existingPages.keySet()) {
            deletePage(collectionPath, removedPageId);
        }
    }

    private void syncPage(
            InspectionPlan plan,
            PlanPage page,
            FirestoreRestClient.FirestoreDocument existingPageDocument
    ) {
        byte[] bytes = null;
        String contentHash = "";
        long byteSize = 0L;
        if (page.getDrawing() != null && page.getDrawing().getStoredPath() != null && !page.getDrawing().getStoredPath().isBlank()) {
            bytes = readPageBytes(page);
            byteSize = bytes.length;
            contentHash = sha256(bytes);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("pageId", page.getId());
        fields.put("name", page.getName());
        fields.put("pageNumber", page.getPageNumber());
        fields.put("fileName", page.getDrawing() == null ? "" : page.getDrawing().getFileName());
        fields.put("fileType", page.getDrawing() == null ? "" : page.getDrawing().getFileType());
        fields.put("storagePath", chunkCollectionPath(plan.getFamilyId(), plan.getId(), page.getId()));
        fields.put("byteSize", byteSize);
        fields.put("contentHash", contentHash);
        String documentPath = pageDocumentPath(plan.getFamilyId(), plan.getId(), page.getId());
        if (existingPageDocument == null || !fieldsEqual(fields, existingPageDocument.fields())) {
            firestore.upsertDocument(documentPath, fields);
        }

        if (page.getDrawing() == null || page.getDrawing().getStoredPath() == null || page.getDrawing().getStoredPath().isBlank()) {
            firestore.deleteCollection(chunkCollectionPath(plan.getFamilyId(), plan.getId(), page.getId()));
            return;
        }

        if (existingPageDocument != null && fieldsEqual(fields, existingPageDocument.fields())) {
            return;
        }

        syncPageChunks(plan.getFamilyId(), plan.getId(), page.getId(), bytes);
    }

    private void deletePage(String pageCollectionPath, String pageId) {
        String chunksPath = pageCollectionPath + "/" + pageId + "/chunks";
        firestore.deleteCollection(chunksPath);
        firestore.deleteDocument(pageCollectionPath + "/" + pageId);
    }

    private byte[] readPageBytes(PlanPage page) {
        try {
            return Files.readAllBytes(Path.of(page.getDrawing().getStoredPath()));
        } catch (IOException exception) {
            throw new FirestoreException("Unable to read page image for upload: " + page.getDrawing().getStoredPath(), exception);
        }
    }

    private void syncPageChunks(String familyId, String planId, String pageId, byte[] bytes) {
        String collectionPath = chunkCollectionPath(familyId, planId, pageId);
        Map<String, FirestoreRestClient.FirestoreDocument> existingChunks = new HashMap<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(collectionPath)) {
            existingChunks.put(document.id(), document);
        }

        int chunkCount = (int) Math.ceil((double) bytes.length / PAGE_CHUNK_SIZE);
        for (int index = 0; index < chunkCount; index++) {
            int start = index * PAGE_CHUNK_SIZE;
            int end = Math.min(bytes.length, start + PAGE_CHUNK_SIZE);
            byte[] chunk = java.util.Arrays.copyOfRange(bytes, start, end);
            String chunkId = String.format("%05d", index);
            existingChunks.remove(chunkId);

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("index", index);
            fields.put("data", chunk);
            firestore.upsertDocument(collectionPath + "/" + chunkId, fields);
        }

        for (String obsoleteChunkId : existingChunks.keySet()) {
            firestore.deleteDocument(collectionPath + "/" + obsoleteChunkId);
        }
    }

    private void syncBubbles(InspectionPlan plan) {
        String collectionPath = bubbleCollectionPath(plan.getFamilyId(), plan.getId());
        Map<String, FirestoreRestClient.FirestoreDocument> existing = new HashMap<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(collectionPath)) {
            existing.put(document.id(), document);
        }

        for (Bubble bubble : plan.getBubbles()) {
            FirestoreRestClient.FirestoreDocument existingBubble = existing.remove(bubble.getId());
            Map<String, Object> fields = bubbleFields(bubble);
            if (existingBubble == null || !fieldsEqual(fields, existingBubble.fields())) {
                firestore.upsertDocument(collectionPath + "/" + bubble.getId(), fields);
            }
        }

        for (String removedBubbleId : existing.keySet()) {
            firestore.deleteDocument(collectionPath + "/" + removedBubbleId);
        }
    }

    private Map<String, Object> bubbleFields(Bubble bubble) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("bubbleId", bubble.getId());
        fields.put("pageId", bubble.getPageId());
        fields.put("x", bubble.getX());
        fields.put("y", bubble.getY());
        fields.put("radius", bubble.getRadius());
        fields.put("useDefaultDiameter", bubble.isUseDefaultDiameter());
        fields.put("color", bubble.getColor());
        fields.put("useDefaultColor", bubble.isUseDefaultColor());
        fields.put("label", bubble.getLabel());
        fields.put("characteristic", bubble.getCharacteristic());
        fields.put("inspectionType", bubble.getInspectionType().name());
        fields.put("nominalValue", bubble.getNominalValue());
        fields.put("lowerTolerance", bubble.getLowerTolerance());
        fields.put("upperTolerance", bubble.getUpperTolerance());
        fields.put("expectedPassFail", bubble.getExpectedPassFail());
        fields.put("note", bubble.getNote());
        fields.put("sequenceNumber", bubble.getSequenceNumber());
        fields.put("status", bubble.getStatus().name());
        fields.put("createdAt", bubble.getCreatedAt());
        fields.put("updatedAt", bubble.getUpdatedAt());
        return fields;
    }

    private InspectionPlan toPlanSummary(FirestoreRestClient.FirestoreDocument document) {
        Map<String, Object> fields = document.fields();
        String planId = stringValue(fields, "planId", document.id());
        String familyId = stringValue(fields, "familyId", planId);
        return new InspectionPlan(
                planId,
                familyId,
                stringValue(fields, "name", ""),
                stringValue(fields, "partNumber", ""),
                stringValue(fields, "revision", ""),
                stringValue(fields, "description", ""),
                null,
                List.of(),
                List.of(),
                intValue(fields, "version", 0),
                enumValue(PlanStatus.class, fields.get("status"), PlanStatus.PENDING),
                dateTimeValue(fields.get("completedAt")),
                dateTimeValue(fields.get("createdAt")),
                dateTimeValue(fields.get("updatedAt"))
        );
    }

    private List<InspectionPlan> loadFamilyVersions(String familyId) {
        return firestore.listDocuments(versionCollectionPath(familyId)).stream()
                .map(this::toPlanSummary)
                .toList();
    }

    private List<InspectionPlan> loadAllVersionSummaries() {
        List<InspectionPlan> versions = new ArrayList<>();
        for (FirestoreRestClient.FirestoreDocument familyDocument : firestore.listDocuments(planFamilyCollectionPath())) {
            versions.addAll(loadFamilyVersions(familyDocument.id()));
        }
        return versions;
    }

    private int nextCompletedVersion(String familyId) {
        return loadFamilyVersions(familyId).stream()
                .filter(InspectionPlan::isComplete)
                .mapToInt(InspectionPlan::getVersion)
                .max()
                .orElse(0) + 1;
    }

    private FirestoreRestClient.FirestoreDocument requireVersionDocument(String planId) {
        for (FirestoreRestClient.FirestoreDocument familyDocument : firestore.listDocuments(planFamilyCollectionPath())) {
            String familyId = familyDocument.id();
            for (FirestoreRestClient.FirestoreDocument versionDocument : firestore.listDocuments(versionCollectionPath(familyId))) {
                String candidatePlanId = stringValue(versionDocument.fields(), "planId", versionDocument.id());
                if (planId.equals(candidatePlanId) || planId.equals(versionDocument.id())) {
                    return versionDocument;
                }
            }
        }
        throw new IllegalStateException("Saved plan file was not found.");
    }

    private List<PlanPage> loadPages(String familyId, String planId) {
        List<PlanPage> pages = new ArrayList<>();
        for (FirestoreRestClient.FirestoreDocument pageDocument : firestore.listDocuments(pageCollectionPath(familyId, planId))) {
            Map<String, Object> fields = pageDocument.fields();
            String pageId = stringValue(fields, "pageId", pageDocument.id());
            String fileName = stringValue(fields, "fileName", "");
            String fileType = stringValue(fields, "fileType", "");
            Path localCachePath = materializePageAsset(familyId, planId, pageId, fileName);
            PlanDrawing drawing = fileName.isBlank()
                    ? null
                    : new PlanDrawing(fileName, localCachePath.toString(), fileType);
            pages.add(new PlanPage(
                    pageId,
                    stringValue(fields, "name", "Page"),
                    intValue(fields, "pageNumber", 0),
                    drawing
            ));
        }
        pages.sort(Comparator.comparingInt(PlanPage::getPageNumber));
        return pages;
    }

    private Path materializePageAsset(String familyId, String planId, String pageId, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Path.of("");
        }

        String safeFileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path cacheFile = AppStoragePaths.assetCacheDirectory()
                .resolve(familyId)
                .resolve(planId)
                .resolve(pageId)
                .resolve(safeFileName);

        try {
            Files.createDirectories(cacheFile.getParent());
            List<FirestoreRestClient.FirestoreDocument> chunks = firestore.listDocuments(chunkCollectionPath(familyId, planId, pageId));
            chunks.sort(Comparator.comparingInt(chunk -> intValue(chunk.fields(), "index", 0)));

            try (java.io.OutputStream outputStream = Files.newOutputStream(cacheFile)) {
                for (FirestoreRestClient.FirestoreDocument chunk : chunks) {
                    byte[] bytes = bytesValue(chunk.fields().get("data"));
                    outputStream.write(bytes);
                }
            }
        } catch (IOException exception) {
            throw new FirestoreException("Unable to cache plan page image locally.", exception);
        }

        return cacheFile;
    }

    private List<Bubble> loadBubbles(String familyId, String planId) {
        List<Bubble> bubbles = new ArrayList<>();
        for (FirestoreRestClient.FirestoreDocument document : firestore.listDocuments(bubbleCollectionPath(familyId, planId))) {
            Map<String, Object> fields = document.fields();
            bubbles.add(new Bubble(
                    stringValue(fields, "bubbleId", document.id()),
                    stringValue(fields, "pageId", ""),
                    doubleValue(fields, "x", 0.0),
                    doubleValue(fields, "y", 0.0),
                    doubleValue(fields, "radius", 18.0),
                    booleanValue(fields, "useDefaultDiameter", true),
                    stringValue(fields, "color", "#E53935"),
                    booleanValue(fields, "useDefaultColor", true),
                    stringValue(fields, "label", ""),
                    stringValue(fields, "characteristic", ""),
                    enumValue(InspectionType.class, fields.get("inspectionType"), InspectionType.NUMERIC),
                    nullableDouble(fields.get("nominalValue")),
                    nullableDouble(fields.get("lowerTolerance")),
                    nullableDouble(fields.get("upperTolerance")),
                    nullableBoolean(fields.get("expectedPassFail")),
                    null,
                    null,
                    enumValue(BubbleStatus.class, fields.get("status"), BubbleStatus.OPEN),
                    stringValue(fields, "note", ""),
                    intValue(fields, "sequenceNumber", 0),
                    dateTimeValue(fields.get("createdAt")),
                    dateTimeValue(fields.get("updatedAt"))
            ));
        }
        bubbles.sort(Comparator.comparing(Bubble::getPageId).thenComparingInt(Bubble::getSequenceNumber));
        return bubbles;
    }

    private String userDocumentPath() {
        return "users/" + firestoreUserId();
    }

    private String familyDocumentPath(String familyId) {
        return userDocumentPath() + "/planFamilies/" + familyId;
    }

    private String planFamilyCollectionPath() {
        return userDocumentPath() + "/planFamilies";
    }

    private String versionCollectionPath(String familyId) {
        return familyDocumentPath(familyId) + "/versions";
    }

    private String versionDocumentPath(String familyId, String planId) {
        return versionCollectionPath(familyId) + "/" + planId;
    }

    private String pageCollectionPath(String familyId, String planId) {
        return versionDocumentPath(familyId, planId) + "/pages";
    }

    private String pageDocumentPath(String familyId, String planId, String pageId) {
        return pageCollectionPath(familyId, planId) + "/" + pageId;
    }

    private String chunkCollectionPath(String familyId, String planId, String pageId) {
        return pageDocumentPath(familyId, planId, pageId) + "/chunks";
    }

    private String bubbleCollectionPath(String familyId, String planId) {
        return versionDocumentPath(familyId, planId) + "/bubbles";
    }

    private void upsertDocumentIfChanged(String documentPath, Map<String, Object> fields) {
        FirestoreRestClient.FirestoreDocument existing = firestore.getDocument(documentPath).orElse(null);
        if (existing != null && fieldsEqual(fields, existing.fields())) {
            return;
        }
        firestore.upsertDocument(documentPath, fields);
    }

    private boolean fieldsEqual(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> normalizedLeft = normalizeMap(left);
        Map<String, Object> normalizedRight = normalizeMap(right);
        if (normalizedLeft.size() != normalizedRight.size()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : normalizedLeft.entrySet()) {
            if (!normalizedRight.containsKey(entry.getKey())) {
                return false;
            }
            if (!valuesEqual(entry.getValue(), normalizedRight.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> normalizeMap(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (source == null) {
            return normalized;
        }

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getValue() != null) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return normalized;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            boolean floating = left instanceof Float || left instanceof Double
                    || right instanceof Float || right instanceof Double;
            return floating
                    ? Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0
                    : leftNumber.longValue() == rightNumber.longValue();
        }
        if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
            return Arrays.equals(leftBytes, rightBytes);
        }
        if (left instanceof Map<?, ?> leftMap && right instanceof Map<?, ?> rightMap) {
            return fieldsEqual(castMap(leftMap), castMap(rightMap));
        }
        if (left instanceof Iterable<?> leftIterable && right instanceof Iterable<?> rightIterable) {
            return iterableValuesEqual(leftIterable, rightIterable);
        }
        return Objects.equals(left, right);
    }

    private boolean iterableValuesEqual(Iterable<?> left, Iterable<?> right) {
        java.util.Iterator<?> leftIterator = left.iterator();
        java.util.Iterator<?> rightIterator = right.iterator();
        while (leftIterator.hasNext() && rightIterator.hasNext()) {
            if (!valuesEqual(leftIterator.next(), rightIterator.next())) {
                return false;
            }
        }
        return !leftIterator.hasNext() && !rightIterator.hasNext();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> cast = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                cast.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return cast;
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String stringValue(Map<String, Object> fields, String key, String fallback) {
        Object value = fields.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Map<String, Object> fields, String key, int fallback) {
        Object value = fields.get(key);
        return intValue(value, fallback);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private double doubleValue(Map<String, Object> fields, String key, double fallback) {
        Object value = fields.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return fallback;
    }

    private boolean booleanValue(Map<String, Object> fields, String key, boolean fallback) {
        Object value = fields.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private Double nullableDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    private Boolean nullableBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private LocalDateTime dateTimeValue(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    private byte[] bytesValue(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new FirestoreException("Expected page chunk bytes.");
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, Object value, T fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return Enum.valueOf(enumType, text);
        }
        return fallback;
    }

    private String firestoreUserId() {
        return firestore.currentUserId();
    }
}
