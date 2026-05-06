package service.util;

import model.Bubble;
import model.InspectionPlan;
import model.PartBubbleDefinition;
import model.PlanPage;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InspectionSpecBuilder {
    private InspectionSpecBuilder() {
    }

    public static List<PartBubbleDefinition> buildBubbleDefinitions(InspectionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }

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
                            bubble.getInspectionType(),
                            bubble.getExpectedPassFail(),
                            formatNullableNumber(bubble.getNominalValue()),
                            formatNullableNumber(bubble.getLowerTolerance()),
                            formatNullableNumber(bubble.getUpperTolerance()),
                            bubble.getNote() == null ? "" : bubble.getNote().trim()
                    );
                })
                .toList();
    }

    private static String buildBubbleName(Bubble bubble, int pageNumber, boolean includePagePrefix) {
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

    private static String formatNullableNumber(Double value) {
        if (value == null) {
            return "";
        }
        if (value == Math.rint(value)) {
            return String.valueOf(value.intValue());
        }
        return value.toString();
    }
}
