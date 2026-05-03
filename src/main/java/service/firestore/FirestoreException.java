package service.firestore;

public class FirestoreException extends RuntimeException {
    public FirestoreException(String message) {
        super(message);
    }

    public FirestoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
