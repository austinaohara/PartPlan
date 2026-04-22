package service.export;


import model.Bubble;
import model.BubbleStatus;

import java.util.List;

public record ExportSummary(
        int total,
        int open,
        int review,
        int pass,
        int fail,
        int withComments,
        int withoutComments
) {
    public static ExportSummary fromBubbles(List<Bubble> bubbles) {
        int total = bubbles.size();
        int open = 0;
        int review = 0;
        int pass = 0;
        int fail = 0;
        int withComments = 0;

        for (Bubble bubble : bubbles) {
            BubbleStatus status = bubble.getStatus();
            if (status == BubbleStatus.OPEN) {
                open++;
            } else if (status == BubbleStatus.REVIEW) {
                review++;
            } else if (status == BubbleStatus.PASS) {
                pass++;
            } else if (status == BubbleStatus.FAIL) {
                fail++;
            }

            if (hasComment(bubble)) {
                withComments++;
            }
        }

        return new ExportSummary(total, open, review, pass, fail, withComments, total - withComments);
    }

    public static boolean hasComment(Bubble bubble) {
        return bubble.getNote() != null && !bubble.getNote().isBlank();
    }
}

