package service.autoballoon;

public record AutoBalloonCandidate(
        String characteristic,
        String detectedText,
        Double nominal,
        Double lowerTolerance,
        Double upperTolerance,
        double anchorX,
        double anchorY,
        String noteText
) {
}
