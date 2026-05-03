package service.autoballoon;

import java.nio.file.Path;

public record AutoBalloonRequest(
        String pageId,
        String pageName,
        Path imagePath,
        int imageWidth,
        int imageHeight
) {
}
