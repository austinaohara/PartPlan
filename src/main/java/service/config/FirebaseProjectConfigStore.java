package service.config;

import java.util.Optional;

public interface FirebaseProjectConfigStore {
    Optional<FirebaseProjectConfig> load();

    void save(FirebaseProjectConfig config);

    void clear();
}
