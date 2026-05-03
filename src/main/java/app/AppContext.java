package app;

import service.PdfPageRenderingService;
import service.asset.AssetStore;
import service.asset.TempFileAssetStore;
import service.auth.AuthService;
import service.auth.UnsupportedAuthService;
import service.memory.InMemoryLotRepository;
import service.memory.InMemoryPlanRepository;
import service.repository.LotRepository;
import service.repository.PlanRepository;
import service.session.InMemorySessionManager;
import service.session.SessionManager;
import service.session.UserSession;

import java.lang.reflect.Constructor;

public class AppContext {
    private final SessionManager sessionManager;
    private final AuthService authService;
    private final PlanRepository planRepository;
    private final LotRepository lotRepository;
    private final AssetStore assetStore;
    private final PdfPageRenderingService pdfPageRenderingService;

    public AppContext(
            SessionManager sessionManager,
            AuthService authService,
            PlanRepository planRepository,
            LotRepository lotRepository,
            AssetStore assetStore,
            PdfPageRenderingService pdfPageRenderingService
    ) {
        this.sessionManager = sessionManager;
        this.authService = authService;
        this.planRepository = planRepository;
        this.lotRepository = lotRepository;
        this.assetStore = assetStore;
        this.pdfPageRenderingService = pdfPageRenderingService;
    }

    public static AppContext createDefault() {
        InMemorySessionManager sessionManager = new InMemorySessionManager();
        sessionManager.setCurrentSession(UserSession.localSession("local-user"));

        AssetStore assetStore = new TempFileAssetStore();
        PdfPageRenderingService pdfPageRenderingService = new PdfPageRenderingService();
        PlanRepository planRepository = new InMemoryPlanRepository(sessionManager);
        LotRepository lotRepository = new InMemoryLotRepository(sessionManager);
        AuthService authService = new UnsupportedAuthService(sessionManager);

        return new AppContext(
                sessionManager,
                authService,
                planRepository,
                lotRepository,
                assetStore,
                pdfPageRenderingService
        );
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
