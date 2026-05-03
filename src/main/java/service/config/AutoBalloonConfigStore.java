package service.config;

import java.util.Optional;

public interface AutoBalloonConfigStore {
    Optional<AutoBalloonConfig> load();

    void save(AutoBalloonConfig config);

    void clear();
}
