package app;

public final class UserFacingErrorMessages {
    private UserFacingErrorMessages() {
    }

    public static String format(Throwable throwable, String fallbackMessage) {
        if (throwable == null) {
            return fallbackMessage;
        }

        Throwable root = rootCause(throwable);
        String message = firstNonBlank(root.getMessage(), throwable.getMessage(), fallbackMessage);
        if (message == null || message.isBlank()) {
            return fallbackMessage;
        }

        if (message.contains("Missing or insufficient permissions")) {
            return "Firebase denied this request. Make sure you are signed into the same Firebase project configured for this workspace and that the Firestore rules are published.";
        }
        if (message.contains("does not exist for project")) {
            return "The configured Firestore database could not be found. Check the Project ID and Database ID in Firebase Settings.";
        }
        if (message.contains("No Firebase session token is available")
                || message.contains("saved session cannot be refreshed")
                || message.contains("saved session has expired")) {
            return "Your Firebase session is no longer valid. Sign out and sign in again.";
        }
        if (message.contains("Unable to reach Firestore")) {
            return "Could not reach Firestore. Check your internet connection and try again.";
        }
        if (message.contains("Unable to reach Firebase")) {
            return "Could not reach Firebase. Check your internet connection and try again.";
        }
        if (message.contains("Firebase project settings are missing")
                || message.contains("missing or incomplete")) {
            return "Firebase settings are incomplete. Open Firebase Settings and verify the API key, Project ID, App ID, Storage Bucket, and Database ID.";
        }
        if (message.contains("Unable to read page image for upload")) {
            return "The selected page image could not be read from disk. Re-import the page and try again.";
        }
        if (message.contains("Unable to cache plan page image locally")) {
            return "The plan loaded, but the page preview could not be cached locally.";
        }
        if (message.contains("Expected page chunk bytes")) {
            return "A saved plan page is incomplete or unreadable in Firestore.";
        }
        if (message.contains("Auto-balloon settings are missing or incomplete")) {
            return "Auto-balloon settings are incomplete. Open OpenAI Settings and set the API key.";
        }
        if (message.contains("OpenAI rejected the API key")
                || message.contains("Incorrect API key")
                || message.contains("invalid_api_key")) {
            return "OpenAI rejected the configured API key. Update it in OpenAI Settings.";
        }
        if (message.contains("insufficient_quota")
                || message.contains("quota")
                || message.contains("billing")) {
            return "OpenAI rejected the request because the API project is out of credits or billing is not active.";
        }
        if (message.contains("rate limit")
                || message.contains("Rate limit")) {
            return "OpenAI rate limits were hit. Wait briefly or switch to a lower-cost model in OpenAI Settings.";
        }
        if (message.contains("Unable to reach OpenAI")) {
            return "Could not reach OpenAI. Check your internet connection and try again.";
        }
        if (message.contains("OpenAI returned no readable auto-balloon JSON")
                || message.contains("OpenAI returned malformed auto-balloon JSON")) {
            return "OpenAI responded, but the auto-balloon result could not be read. Try the request again.";
        }
        if (message.contains("OpenAI request failed: ")) {
            return message.substring("OpenAI request failed: ".length());
        }
        if (message.startsWith("Firestore request failed: ")) {
            return message.substring("Firestore request failed: ".length());
        }
        return message;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
