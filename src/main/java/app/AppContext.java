package app;

import service.asset.ImportWorkspace;
import service.asset.TempImportWorkspace;
import service.auth.AuthService;
import service.auth.FirebaseAuthService;
import service.autoballoon.AutoBalloonDetectionService;
import service.autoballoon.OpenAiAutoBalloonService;
import service.PdfPageRenderingService;
import service.config.AutoBalloonConfigStore;
import service.config.FileBackedFirebaseProjectConfigStore;
import service.config.FileBackedAutoBalloonConfigStore;
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
    private final AutoBalloonConfigStore autoBalloonConfigStore;
    private final SessionManager sessionManager;
    private final AuthService authService;
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final ImportWorkspace assetStore;
    private final PdfPageRenderingService pdfPageRenderingService;
    private final AutoBalloonDetectionService autoBalloonDetectionService;

    public AppContext(
            FirebaseProjectConfigStore projectConfigStore,
            AutoBalloonConfigStore autoBalloonConfigStore,
            SessionManager sessionManager,
            AuthService authService,
            PlanRepository planRepository,
            LotRepository lotRepository,
            ImportWorkspace assetStore,
            PdfPageRenderingService pdfPageRenderingService,
            AutoBalloonDetectionService autoBalloonDetectionService
    ) {
        this.projectConfigStore = projectConfigStore;
        this.autoBalloonConfigStore = autoBalloonConfigStore;
        this.sessionManager = sessionManager;
        this.authService = authService;
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
        this.assetStore = assetStore;
        this.pdfPageRenderingService = pdfPageRenderingService;
        this.autoBalloonDetectionService = autoBalloonDetectionService;
    }

    public static AppContext createDefault() {
        FirebaseProjectConfigStore projectConfigStore = new FileBackedFirebaseProjectConfigStore();
        AutoBalloonConfigStore autoBalloonConfigStore = new FileBackedAutoBalloonConfigStore();
        SessionManager sessionManager = new FileBackedSessionManager();

        ImportWorkspace assetStore = new TempImportWorkspace();
        PdfPageRenderingService pdfPageRenderingService = new PdfPageRenderingService();
        AuthService authService = new FirebaseAuthService(sessionManager, projectConfigStore);
        FirestoreRestClient firestoreRestClient = new FirestoreRestClient(sessionManager, authService, projectConfigStore);
        PlanRepository planRepository = new FirestorePlanRepository(firestoreRestClient);
        LotRepository lotRepository = new FirestoreLotRepository(firestoreRestClient, planRepository);
        AutoBalloonDetectionService autoBalloonDetectionService = new OpenAiAutoBalloonService(autoBalloonConfigStore);

        return new AppContext(
                projectConfigStore,
                autoBalloonConfigStore,
                sessionManager,
                authService,
                planRepository,
                lotRepository,
                assetStore,
                pdfPageRenderingService,
                autoBalloonDetectionService
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

    public AutoBalloonConfigStore getAutoBalloonConfigStore() {
        return autoBalloonConfigStore;
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

    public ImportWorkspace getAssetStore() {
        return assetStore;
    }

    public PdfPageRenderingService getPdfPageRenderingService() {
        return pdfPageRenderingService;
    }

    public AutoBalloonDetectionService getAutoBalloonDetectionService() {
        return autoBalloonDetectionService;
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
