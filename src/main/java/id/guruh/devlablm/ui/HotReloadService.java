package id.guruh.devlablm.ui;

import id.guruh.devlablm.ui.controller.MainLayoutController;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class HotReloadService {

    private static final Logger log = LoggerFactory.getLogger(HotReloadService.class);

    private final FxmlViewLoader fxmlViewLoader;

    @Value("${devlablm.hot-reload.enabled:true}")
    private boolean enabled;

    private Stage stage;
    private Scene scene;
    private MainLayoutController mainLayoutController;

    private WatchService watchService;
    private volatile boolean running = false;
    private ScheduledExecutorService debounceExecutor;
    private ScheduledFuture<?> pendingReloadTask;

    private final AtomicBoolean cssChanged = new AtomicBoolean(false);
    private final AtomicBoolean fxmlChanged = new AtomicBoolean(false);
    private final AtomicBoolean mainFxmlChanged = new AtomicBoolean(false);

    public HotReloadService(FxmlViewLoader fxmlViewLoader) {
        this.fxmlViewLoader = fxmlViewLoader;
    }

    public void init(Stage stage, Scene scene) {
        this.stage = stage;
        this.scene = scene;

        if (!enabled) {
            log.info("[HotReload] Disabled via configuration.");
            return;
        }

        Path resourcesDir = Paths.get("src/main/resources");
        if (!Files.exists(resourcesDir)) {
            log.info("[HotReload] Directory src/main/resources tidak ditemukan. Mode produksi/JAR, hot reload nonaktif.");
            return;
        }

        setupKeyShortcuts(scene);
        startFileWatcher(resourcesDir);
        log.info("[HotReload] Aktif! Shortcut: F5 / Ctrl+R (Reload View+CSS), Ctrl+Shift+R (Full Shell Reload).");
    }

    public void registerMainLayout(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    private void setupKeyShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F5) {
                log.info("[HotReload] Shortcut F5 ditekan -> Reload view & CSS");
                reloadAll();
                event.consume();
            } else if (event.isShortcutDown() && !event.isShiftDown() && event.getCode() == KeyCode.R) {
                log.info("[HotReload] Shortcut Ctrl+R ditekan -> Reload view & CSS");
                reloadAll();
                event.consume();
            } else if (event.isShortcutDown() && event.isShiftDown() && event.getCode() == KeyCode.R) {
                log.info("[HotReload] Shortcut Ctrl+Shift+R ditekan -> Full reload main");
                reloadMain();
                event.consume();
            }
        });
    }

    private void startFileWatcher(Path rootDir) {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "hotreload-debounce");
                t.setDaemon(true);
                return t;
            });

            registerRecursive(rootDir);

            this.running = true;
            Thread thread = new Thread(this::watchLoop, "hotreload-file-watcher");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            log.warn("[HotReload] Gagal inisialisasi file watcher: {}", e.getMessage());
        }
    }

    private void registerRecursive(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                try {
                    p.register(watchService,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_CREATE);
                } catch (IOException e) {
                    log.debug("[HotReload] Gagal register watch dir {}: {}", p, e.getMessage());
                }
            });
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            Path watchedDir = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = watchedDir.resolve(filename);

                // Auto register folder baru
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                    try {
                        registerRecursive(fullPath);
                    } catch (IOException ignored) {}
                }

                String name = filename.toString();
                if (name.endsWith(".css")) {
                    cssChanged.set(true);
                    scheduleDebouncedReload("CSS: " + name);
                } else if (name.equals("main.fxml")) {
                    mainFxmlChanged.set(true);
                    scheduleDebouncedReload("Main FXML: " + name);
                } else if (name.endsWith(".fxml")) {
                    fxmlChanged.set(true);
                    scheduleDebouncedReload("FXML: " + name);
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                // key expired / deleted
            }
        }
    }

    private synchronized void scheduleDebouncedReload(String triggerReason) {
        if (debounceExecutor == null || debounceExecutor.isShutdown()) return;

        if (pendingReloadTask != null && !pendingReloadTask.isDone()) {
            pendingReloadTask.cancel(false);
        }

        pendingReloadTask = debounceExecutor.schedule(() -> {
            boolean doMain = mainFxmlChanged.getAndSet(false);
            boolean doFxml = fxmlChanged.getAndSet(false);
            boolean doCss = cssChanged.getAndSet(false);

            log.info("[HotReload] Perubahan terdeteksi ({}) -> reloading...", triggerReason);

            Platform.runLater(() -> {
                if (doMain) {
                    reloadMain();
                } else {
                    if (doFxml) {
                        reloadCurrentView();
                    }
                    if (doCss) {
                        reloadCss();
                    }
                }
            });
        }, 200, TimeUnit.MILLISECONDS);
    }

    public void reloadCss() {
        if (scene == null) return;
        try {
            String cssUrl = fxmlViewLoader.resolveCssUrl("/css/app.css");
            if (!cssUrl.isEmpty()) {
                scene.getStylesheets().clear();
                scene.getStylesheets().add(cssUrl);
                log.info("[HotReload] CSS reloaded!");
            }
        } catch (Exception e) {
            log.error("[HotReload] Gagal reload CSS: {}", e.getMessage());
        }
    }

    public void reloadCurrentView() {
        if (mainLayoutController != null) {
            try {
                mainLayoutController.reloadCurrentPage();
            } catch (Exception e) {
                log.error("[HotReload] Gagal reload halaman aktif: {}", e.getMessage());
            }
        }
    }

    public void reloadAll() {
        reloadCss();
        reloadCurrentView();
    }

    public void reloadMain() {
        if (scene == null) return;
        try {
            String lastFxml = mainLayoutController != null ? mainLayoutController.getCurrentFxmlPath() : null;
            var viewNode = fxmlViewLoader.loadView("/fxml/main.fxml");
            scene.setRoot(viewNode.root());
            reloadCss();
            if (lastFxml != null && mainLayoutController != null) {
                mainLayoutController.navigateTo(lastFxml);
            }
            log.info("[HotReload] Main shell reloaded!");
        } catch (Exception e) {
            log.error("[HotReload] Gagal reload main shell: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        running = false;
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
        }
    }
}
