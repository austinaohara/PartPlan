package service;

import model.Bubble;
import model.InspectionLot;
import model.InspectionLotSummary;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PartLot;
import model.PartRecord;
import model.PlanPage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InspectionLotDatabaseService {
    private static final String APP_DIRECTORY_NAME = ".partplan";
    private static final String DATABASE_DIRECTORY_NAME = "database";
    private static final String DATABASE_FILE_NAME = "partplan.db";

    public InspectionLotDatabaseService() {
        initializeDatabase();
    }

    public List<InspectionLotSummary> loadLotSummaries() {
        String sql = """
                SELECT id, name, plan_id, plan_name, lot_size, created_at, updated_at
                FROM inspection_lot
                ORDER BY updated_at DESC
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<InspectionLotSummary> summaries = new ArrayList<>();
            while (resultSet.next()) {
                summaries.add(new InspectionLotSummary(
                        resultSet.getString("id"),
                        resultSet.getString("name"),
                        resultSet.getString("plan_id"),
                        resultSet.getString("plan_name"),
                        resultSet.getInt("lot_size"),
                        LocalDateTime.parse(resultSet.getString("created_at")),
                        LocalDateTime.parse(resultSet.getString("updated_at"))
                ));
            }
            return summaries;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load inspection lots.", exception);
        }
    }

    public InspectionLot createLot(String proposedLotName, InspectionPlan plan, int lotSize) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

        InspectionLot lot = new InspectionLot(
                sanitizeLotName(proposedLotName, plan),
                plan.getId(),
                displayPlanName(plan),
                lotSize
        );
        lot.replaceBubbles(buildBubbleDefinitions(plan));
        persistNewLot(lot);
        return loadLot(lot.getId());
    }

    public InspectionLot loadLot(String lotId) {
        try (Connection connection = openConnection()) {
            InspectionLot lot = readLot(connection, lotId);
            if (lot == null) {
                throw new IllegalStateException("Inspection lot was not found.");
            }
            return lot;
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load inspection lot.", exception);
        }
    }

    public void saveLotName(String lotId, String lotName) {
        String sql = """
                UPDATE inspection_lot
                SET name = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotName);
            statement.setString(2, LocalDateTime.now().toString());
            statement.setString(3, lotId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save inspection lot name.", exception);
        }
    }

    public void saveLotStructure(InspectionLot lot) {
        String updateLotSql = """
                UPDATE inspection_lot
                SET name = ?, lot_size = ?, updated_at = ?
                WHERE id = ?
                """;
        String deletePartsSql = "DELETE FROM inspection_lot_part WHERE lot_id = ?";
        String deleteMeasurementsSql = "DELETE FROM inspection_lot_measurement WHERE lot_id = ?";
        String insertPartSql = """
                INSERT INTO inspection_lot_part (lot_id, part_id, part_number)
                VALUES (?, ?, ?)
                """;
        String insertMeasurementSql = """
                INSERT INTO inspection_lot_measurement (lot_id, part_id, bubble_id, value)
                VALUES (?, ?, ?, ?)
                """;

        LocalDateTime updatedAt = LocalDateTime.now();
        lot.setUpdatedAt(updatedAt);

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement updateLotStatement = connection.prepareStatement(updateLotSql);
                 PreparedStatement deletePartsStatement = connection.prepareStatement(deletePartsSql);
                 PreparedStatement deleteMeasurementsStatement = connection.prepareStatement(deleteMeasurementsSql);
                 PreparedStatement insertPartStatement = connection.prepareStatement(insertPartSql);
                 PreparedStatement insertMeasurementStatement = connection.prepareStatement(insertMeasurementSql)) {

                updateLotStatement.setString(1, lot.getName());
                updateLotStatement.setInt(2, lot.getLotSize());
                updateLotStatement.setString(3, updatedAt.toString());
                updateLotStatement.setString(4, lot.getId());
                updateLotStatement.executeUpdate();

                deleteMeasurementsStatement.setString(1, lot.getId());
                deleteMeasurementsStatement.executeUpdate();

                deletePartsStatement.setString(1, lot.getId());
                deletePartsStatement.executeUpdate();

                for (PartRecord part : lot.getParts()) {
                    insertPartStatement.setString(1, lot.getId());
                    insertPartStatement.setString(2, part.getId());
                    insertPartStatement.setInt(3, part.getPartNumber());
                    insertPartStatement.addBatch();

                    for (PartBubbleDefinition bubble : lot.getBubbles()) {
                        insertMeasurementStatement.setString(1, lot.getId());
                        insertMeasurementStatement.setString(2, part.getId());
                        insertMeasurementStatement.setString(3, bubble.getId());
                        insertMeasurementStatement.setString(4, part.getMeasurement(bubble.getId()));
                        insertMeasurementStatement.addBatch();
                    }
                }

                insertPartStatement.executeBatch();
                insertMeasurementStatement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save inspection lot structure.", exception);
        }
    }

    public void saveMeasurement(String lotId, String partId, String bubbleId, String value) {
        String sql = """
                INSERT INTO inspection_lot_measurement (lot_id, part_id, bubble_id, value)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(lot_id, part_id, bubble_id)
                DO UPDATE SET value = excluded.value
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lotId);
            statement.setString(2, partId);
            statement.setString(3, bubbleId);
            statement.setString(4, value == null ? "" : value.trim());
            statement.executeUpdate();
            touchLot(connection, lotId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to save measurement value.", exception);
        }
    }

    private void persistNewLot(InspectionLot lot) {
        String insertLotSql = """
                INSERT INTO inspection_lot (id, name, plan_id, plan_name, lot_size, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String insertBubbleSql = """
                INSERT INTO inspection_lot_bubble
                (lot_id, bubble_id, sequence_number, name, nominal_value, lower_tolerance, upper_tolerance, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertPartSql = """
                INSERT INTO inspection_lot_part (lot_id, part_id, part_number)
                VALUES (?, ?, ?)
                """;
        String insertMeasurementSql = """
                INSERT INTO inspection_lot_measurement (lot_id, part_id, bubble_id, value)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insertLotStatement = connection.prepareStatement(insertLotSql);
                 PreparedStatement insertBubbleStatement = connection.prepareStatement(insertBubbleSql);
                 PreparedStatement insertPartStatement = connection.prepareStatement(insertPartSql);
                 PreparedStatement insertMeasurementStatement = connection.prepareStatement(insertMeasurementSql)) {

                insertLotStatement.setString(1, lot.getId());
                insertLotStatement.setString(2, lot.getName());
                insertLotStatement.setString(3, lot.getPlanId());
                insertLotStatement.setString(4, lot.getPlanName());
                insertLotStatement.setInt(5, lot.getLotSize());
                insertLotStatement.setString(6, lot.getCreatedAt().toString());
                insertLotStatement.setString(7, lot.getUpdatedAt().toString());
                insertLotStatement.executeUpdate();

                for (PartBubbleDefinition bubble : lot.getBubbles()) {
                    insertBubbleStatement.setString(1, lot.getId());
                    insertBubbleStatement.setString(2, bubble.getId());
                    insertBubbleStatement.setInt(3, bubble.getSequenceNumber());
                    insertBubbleStatement.setString(4, bubble.getName());
                    insertBubbleStatement.setString(5, bubble.getNominalValue());
                    insertBubbleStatement.setString(6, bubble.getLowerTolerance());
                    insertBubbleStatement.setString(7, bubble.getUpperTolerance());
                    insertBubbleStatement.setString(8, bubble.getNote());
                    insertBubbleStatement.addBatch();
                }
                insertBubbleStatement.executeBatch();

                for (PartRecord part : lot.getParts()) {
                    insertPartStatement.setString(1, lot.getId());
                    insertPartStatement.setString(2, part.getId());
                    insertPartStatement.setInt(3, part.getPartNumber());
                    insertPartStatement.addBatch();

                    for (PartBubbleDefinition bubble : lot.getBubbles()) {
                        insertMeasurementStatement.setString(1, lot.getId());
                        insertMeasurementStatement.setString(2, part.getId());
                        insertMeasurementStatement.setString(3, bubble.getId());
                        insertMeasurementStatement.setString(4, part.getMeasurement(bubble.getId()));
                        insertMeasurementStatement.addBatch();
                    }
                }
                insertPartStatement.executeBatch();
                insertMeasurementStatement.executeBatch();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to create inspection lot.", exception);
        }
    }

    private InspectionLot readLot(Connection connection, String lotId) throws SQLException {
        String lotSql = """
                SELECT id, name, plan_id, plan_name, lot_size, created_at, updated_at
                FROM inspection_lot
                WHERE id = ?
                """;
        String bubblesSql = """
                SELECT bubble_id, sequence_number, name, nominal_value, lower_tolerance, upper_tolerance, note
                FROM inspection_lot_bubble
                WHERE lot_id = ?
                ORDER BY sequence_number ASC
                """;
        String partsSql = """
                SELECT part_id, part_number
                FROM inspection_lot_part
                WHERE lot_id = ?
                ORDER BY part_number ASC
                """;
        String measurementsSql = """
                SELECT part_id, bubble_id, value
                FROM inspection_lot_measurement
                WHERE lot_id = ?
                """;

        try (PreparedStatement lotStatement = connection.prepareStatement(lotSql)) {
            lotStatement.setString(1, lotId);
            try (ResultSet lotResult = lotStatement.executeQuery()) {
                if (!lotResult.next()) {
                    return null;
                }

                PartLot lotData = new PartLot(lotResult.getInt("lot_size"));

                List<PartBubbleDefinition> bubbleDefinitions = new ArrayList<>();
                try (PreparedStatement bubblesStatement = connection.prepareStatement(bubblesSql)) {
                    bubblesStatement.setString(1, lotId);
                    try (ResultSet bubbleResult = bubblesStatement.executeQuery()) {
                        while (bubbleResult.next()) {
                            bubbleDefinitions.add(new PartBubbleDefinition(
                                    bubbleResult.getString("bubble_id"),
                                    bubbleResult.getString("name"),
                                    bubbleResult.getInt("sequence_number"),
                                    bubbleResult.getString("nominal_value"),
                                    bubbleResult.getString("lower_tolerance"),
                                    bubbleResult.getString("upper_tolerance"),
                                    bubbleResult.getString("note")
                            ));
                        }
                    }
                }
                lotData.replaceBubbles(bubbleDefinitions);

                LinkedHashMap<String, PartRecord> partsById = new LinkedHashMap<>();
                try (PreparedStatement partsStatement = connection.prepareStatement(partsSql)) {
                    partsStatement.setString(1, lotId);
                    try (ResultSet partResult = partsStatement.executeQuery()) {
                        while (partResult.next()) {
                            PartRecord part = new PartRecord(partResult.getString("part_id"), partResult.getInt("part_number"));
                            for (PartBubbleDefinition bubble : bubbleDefinitions) {
                                part.ensureMeasurement(bubble.getId());
                            }
                            partsById.put(part.getId(), part);
                        }
                    }
                }

                lotData.getParts().clear();
                lotData.getParts().addAll(partsById.values());

                try (PreparedStatement measurementsStatement = connection.prepareStatement(measurementsSql)) {
                    measurementsStatement.setString(1, lotId);
                    try (ResultSet measurementResult = measurementsStatement.executeQuery()) {
                        while (measurementResult.next()) {
                            PartRecord part = partsById.get(measurementResult.getString("part_id"));
                            if (part != null) {
                                part.setMeasurement(
                                        measurementResult.getString("bubble_id"),
                                        measurementResult.getString("value")
                                );
                            }
                        }
                    }
                }

                return new InspectionLot(
                        lotResult.getString("id"),
                        lotResult.getString("name"),
                        lotResult.getString("plan_id"),
                        lotResult.getString("plan_name"),
                        lotData,
                        LocalDateTime.parse(lotResult.getString("created_at")),
                        LocalDateTime.parse(lotResult.getString("updated_at"))
                );
            }
        }
    }

    private void touchLot(Connection connection, String lotId) throws SQLException {
        String sql = """
                UPDATE inspection_lot
                SET updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LocalDateTime.now().toString());
            statement.setString(2, lotId);
            statement.executeUpdate();
        }
    }

    private void initializeDatabase() {
        try {
            Files.createDirectories(getDatabaseDirectory());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create database directory.", exception);
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inspection_lot (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        plan_id TEXT NOT NULL,
                        plan_name TEXT NOT NULL,
                        lot_size INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inspection_lot_bubble (
                        lot_id TEXT NOT NULL,
                        bubble_id TEXT NOT NULL,
                        sequence_number INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        nominal_value TEXT NOT NULL,
                        lower_tolerance TEXT NOT NULL,
                        upper_tolerance TEXT NOT NULL,
                        note TEXT NOT NULL,
                        PRIMARY KEY (lot_id, bubble_id),
                        FOREIGN KEY (lot_id) REFERENCES inspection_lot(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inspection_lot_part (
                        lot_id TEXT NOT NULL,
                        part_id TEXT NOT NULL,
                        part_number INTEGER NOT NULL,
                        PRIMARY KEY (lot_id, part_id),
                        FOREIGN KEY (lot_id) REFERENCES inspection_lot(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS inspection_lot_measurement (
                        lot_id TEXT NOT NULL,
                        part_id TEXT NOT NULL,
                        bubble_id TEXT NOT NULL,
                        value TEXT NOT NULL,
                        PRIMARY KEY (lot_id, part_id, bubble_id),
                        FOREIGN KEY (lot_id) REFERENCES inspection_lot(id) ON DELETE CASCADE
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inspection_lot_bubble_order ON inspection_lot_bubble(lot_id, sequence_number)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inspection_lot_part_order ON inspection_lot_part(lot_id, part_number)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to initialize inspection lot database.", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + getDatabasePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private Path getDatabaseDirectory() {
        return Path.of(System.getProperty("user.home"), APP_DIRECTORY_NAME, DATABASE_DIRECTORY_NAME);
    }

    private Path getDatabasePath() {
        return getDatabaseDirectory().resolve(DATABASE_FILE_NAME);
    }

    private List<PartBubbleDefinition> buildBubbleDefinitions(InspectionPlan plan) {
        Map<String, Integer> pageOrder = new HashMap<>();
        for (PlanPage page : plan.getPages()) {
            pageOrder.put(page.getId(), page.getPageNumber());
        }

        List<Bubble> sortedBubbles = plan.getBubbles().stream()
                .sorted(Comparator
                        .comparingInt((Bubble bubble) -> pageOrder.getOrDefault(bubble.getPageId(), Integer.MAX_VALUE))
                        .thenComparingInt(Bubble::getSequenceNumber)
                        .thenComparing(Bubble::getId))
                .toList();

        boolean includePagePrefix = pageOrder.size() > 1;
        return java.util.stream.IntStream.range(0, sortedBubbles.size())
                .mapToObj(index -> {
                    Bubble bubble = sortedBubbles.get(index);
                    String name = buildBubbleName(bubble, pageOrder.getOrDefault(bubble.getPageId(), 0), includePagePrefix);
                    return new PartBubbleDefinition(
                            bubble.getId(),
                            name,
                            index + 1,
                            formatNullableNumber(bubble.getNominalValue()),
                            formatNullableNumber(bubble.getLowerTolerance()),
                            formatNullableNumber(bubble.getUpperTolerance()),
                            bubble.getNote() == null ? "" : bubble.getNote().trim()
                    );
                })
                .toList();
    }

    private String buildBubbleName(Bubble bubble, int pageNumber, boolean includePagePrefix) {
        String label = (bubble.getLabel() == null || bubble.getLabel().isBlank())
                ? "Bubble " + bubble.getSequenceNumber()
                : bubble.getLabel().trim();
        String characteristic = bubble.getCharacteristic() == null ? "" : bubble.getCharacteristic().trim();
        String bubbleText = characteristic.isBlank() ? label : label + " - " + characteristic;

        if (includePagePrefix && pageNumber > 0) {
            return "Page " + pageNumber + " | " + bubbleText;
        }

        return bubbleText;
    }

    private String sanitizeLotName(String proposedLotName, InspectionPlan plan) {
        if (proposedLotName == null || proposedLotName.isBlank()) {
            return displayPlanName(plan) + " Lot " + LocalDateTime.now().withNano(0);
        }
        return proposedLotName.trim();
    }

    private String displayPlanName(InspectionPlan plan) {
        if (plan == null || plan.getName() == null || plan.getName().isBlank()) {
            return "Untitled Plan";
        }
        return plan.getName().trim();
    }

    private String formatNullableNumber(Double value) {
        if (value == null) {
            return "";
        }
        if (value == Math.rint(value)) {
            return String.valueOf(value.intValue());
        }
        return value.toString();
    }
}
