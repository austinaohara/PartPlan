package service.autoballoon;

import java.util.List;

public interface AutoBalloonDetectionService {
    List<AutoBalloonCandidate> detectBalloons(AutoBalloonRequest request);
}
