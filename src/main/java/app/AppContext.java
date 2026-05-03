package app;

import service.PdfPageRenderingService;
import service.asset.AssetStore;
import service.asset.TempFileAssetStore;
import service.auth.AuthService;
import service.auth.FirebaseAuthService;
import service.config.FileBackedFirebaseProjectConfigStore;
import service.config.FirebaseProjectConfigStore;
import service.firestore.FirestoreLotRepository;
import service.firestore.FirestorePlanRepository;
import service.firestore.FirestoreRestClient;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.session.FileBackedSessionManager;
import service.session.SessionManager;

import java.lang.reflect.Constructor;

public class AppContext {
    private final FirebaseProjectConfigStore projectConfigStore;
    private final SessionManager sessionManager;
    private final AuthService authService;
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final AssetStore assetStore;
    private final PdfPageRenderingService pdfPageRenderingService;

    public AppContext(
            FirebaseProjectConfigStore projectConfigStore,
            SessionManager sessionManager,
            AuthService authService,
            PlanRepository planRepository,
            LotRepository lotRepository,
            AssetStore assetStore,
            PdfPageRenderingService pdfPageRenderingService
    ) {
        this.projectConfigStore = projectConfigStore;
        this.sessionManager = sessionManager;
        this.authService = authService;
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
        this.assetStore = assetStore;
        this.pdfPageRenderingService = pdfPageRenderingService;
    }

    public static AppContext createDefault() {
        FirebaseProjectConfigStore projectConfigStore = new FileBackedFirebaseProjectConfigStore();
        SessionManager sessionManager = new FileBackedSessionManager();

        AssetStore assetStore = new TempFileAssetStore();
        PdfPageRenderingService pdfPageRenderingService = new PdfPageRenderingService();
        AuthService authService = new FirebaseAuthService(sessionManager, projectConfigStore);
        FirestoreRestClient firestoreRestClient = new FirestoreRestClient(sessionManager, authService, projectConfigStore);
        PlanRepository planRepository = new FirestorePlanRepository(firestoreRestClient);
        LotRepository lotRepository = new FirestoreLotRepository(firestoreRestClient, planRepository);

        return new AppContext(
                projectConfigStore,
                sessionManager,
                authService,
                planRepository,
                lotRepository,
                assetStore,
                pdfPageRenderingService
        );
    }

    public FirebaseProjectConfigStore getProjectConfigStore() {
        return projectConfigStore;
    }

    public boolean hasUsableProjectConfig() {
        try {
            return projectConfigStore.load().isPresent();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String getStartupFxmlPath() {
        return hasUsableProjectConfig() ? "/fxml/login.fxml" : "/fxml/firebase-config.fxml";
    }

    public String getStartupTitle() {
        return hasUsableProjectConfig() ? "PartPlan - Sign In" : "PartPlan - Firebase Setup";
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public PlanRepository getPlanRepository() {
        return planRepository;
    }

    public LotRepository getLotRepository() {
        return lotRepository;
    }

    public AssetStore getAssetStore() {
        return assetStore;
    }

    public PdfPageRenderingService getPdfPageRenderingService() {
        return pdfPageRenderingService;
    }

    public Object createController(Class<?> controllerType) {
        try {
            Constructor<?> contextConstructor = findContextConstructor(controllerType);
            if (contextConstructor != null) {
                contextConstructor.setAccessible(true);
                return contextConstructor.newInstance(this);
            }

            Constructor<?> defaultConstructor = controllerType.getDeclaredConstructor();
            defaultConstructor.setAccessible(true);
            return defaultConstructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create controller: " + controllerType.getName(), exception);
        }
    }

    private Constructor<?> findContextConstructor(Class<?> controllerType) {
        for (Constructor<?> constructor : controllerType.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].equals(AppContext.class)) {
                return constructor;
            }
        }
        return null;
    }
}
