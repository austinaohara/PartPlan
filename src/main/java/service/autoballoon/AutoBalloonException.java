package service.autoballoon;

public class AutoBalloonException extends RuntimeException {
    public AutoBalloonException(String message) {
        super(message);
    }

    public AutoBalloonException(String message, Throwable cause) {
        super(message, cause);
    }
}
