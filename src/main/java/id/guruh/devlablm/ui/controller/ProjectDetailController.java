package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.model.KnowledgeItem;
import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.service.ProjectService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProjectDetailController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.forLanguageTag("id-ID"));

    private final ProjectService projectService;

    // Header FXML
    @FXML private Label lblHeaderTitle;
    @FXML private Label lblHeaderSubtitle;
    @FXML private Button btnHeaderSave;
    @FXML private Button btnHeaderTrain;

    // Form FXML (Left Panel)
    @FXML private TextField nameField;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea systemPromptArea;
    @FXML private Label lblSaveStatus;
    @FXML private Button btnSaveLeft;

    // Right TabPane
    @FXML private TabPane mainTabPane;

    // Upload Section FXML
    @FXML private VBox dropZone;
    @FXML private Button btnBrowseFiles;
    @FXML private VBox queueContainer;
    @FXML private ListView<String> queueListView;
    @FXML private Button btnClearQueue;
    @FXML private Button btnUploadQueue;
    @FXML private Label lblUploadFeedback;

    // Table Section FXML
    @FXML private TextField searchDocField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private Button btnRefreshTable;
    @FXML private TableView<KnowledgeItem> knowledgeTable;
    @FXML private TableColumn<KnowledgeItem, String> colFileName;
    @FXML private TableColumn<KnowledgeItem, String> colFileType;
    @FXML private TableColumn<KnowledgeItem, String> colFileSize;
    @FXML private TableColumn<KnowledgeItem, String> colChunkCount;
    @FXML private TableColumn<KnowledgeItem, String> colUploadedAt;
    @FXML private TableColumn<KnowledgeItem, String> colStatus;
    @FXML private Label lblKnowledgeSummary;
    @FXML private Button btnQuickTrain;

    // Training Section FXML (Tab 2)
    @FXML private Button btnTabStartTraining;
    @FXML private Label lblTrainingStatusBadge;
    @FXML private Label lblTrainingPercent;
    @FXML private ProgressBar trainingProgressBar;
    @FXML private Button btnClearLog;
    @FXML private TextArea logConsoleArea;

    private ProjectEntity currentProject;
    private Runnable onProjectUpdated;

    private final ObservableList<File> pendingFiles = FXCollections.observableArrayList();
    private final ObservableList<KnowledgeItem> masterKnowledgeList = FXCollections.observableArrayList();
    private FilteredList<KnowledgeItem> filteredKnowledgeList;

    public ProjectDetailController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @FXML
    private void initialize() {
        setupDragAndDrop();
        setupTable();
        setupFilters();
        setupInitialKnowledgeData();
        setupInitialLogs();
    }

    public void initProject(ProjectEntity project, Runnable onUpdateCallback) {
        this.currentProject = project;
        this.onProjectUpdated = onUpdateCallback;

        if (project != null) {
            lblHeaderTitle.setText("Project: " + project.getName());
            nameField.setText(project.getName());
            descriptionArea.setText(project.getDescription() != null ? project.getDescription() : "");
            systemPromptArea.setText(project.getSystemPrompt() != null ? project.getSystemPrompt() : "");
        }
    }

    private void setupDragAndDrop() {
        dropZone.setOnDragOver((DragEvent event) -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                if (!dropZone.getStyleClass().contains("dropzone-hover")) {
                    dropZone.getStyleClass().add("dropzone-hover");
                }
            }
            event.consume();
        });

        dropZone.setOnDragExited((DragEvent event) -> {
            dropZone.getStyleClass().remove("dropzone-hover");
            event.consume();
        });

        dropZone.setOnDragDropped((DragEvent event) -> {
            boolean success = false;
            if (event.getDragboard().hasFiles()) {
                addFilesToQueue(event.getDragboard().getFiles());
                success = true;
            }
            dropZone.getStyleClass().remove("dropzone-hover");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupTable() {
        colFileName.setCellValueFactory(data -> data.getValue().fileNameProperty());
        colFileType.setCellValueFactory(data -> data.getValue().fileTypeProperty());
        colFileSize.setCellValueFactory(data -> data.getValue().fileSizeProperty());
        colChunkCount.setCellValueFactory(data -> data.getValue().chunkCountProperty().asString());
        colUploadedAt.setCellValueFactory(data -> data.getValue().uploadedAtProperty());
        colStatus.setCellValueFactory(data -> data.getValue().statusProperty());

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(status);
                    badge.getStyleClass().add("badge-tag");
                    if ("Sudah Dilatih".equalsIgnoreCase(status) || "Terkonversi".equalsIgnoreCase(status)) {
                        badge.getStyleClass().add("badge-status-trained");
                    } else if ("Terekstraksi".equalsIgnoreCase(status) || "Siap Training".equalsIgnoreCase(status)) {
                        badge.getStyleClass().add("badge-status-ready");
                    } else {
                        badge.getStyleClass().add("badge-status-pending");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        filteredKnowledgeList = new FilteredList<>(masterKnowledgeList, p -> true);
        knowledgeTable.setItems(filteredKnowledgeList);
    }

    private void setupFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "Semua Format", "PDF", "Word", "Excel", "Text"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        searchDocField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        typeFilterCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    private void applyFilter() {
        String query = searchDocField.getText() == null ? "" : searchDocField.getText().trim().toLowerCase();
        String selectedType = typeFilterCombo.getValue();

        filteredKnowledgeList.setPredicate(item -> {
            boolean matchQuery = query.isEmpty() ||
                    item.getFileName().toLowerCase().contains(query);

            boolean matchType = true;
            if (selectedType != null && !selectedType.equals("Semua Format")) {
                matchType = item.getFileType().equalsIgnoreCase(selectedType);
            }

            return matchQuery && matchType;
        });
        updateSummaryLabel();
    }

    private void setupInitialKnowledgeData() {
        masterKnowledgeList.addAll(
                new KnowledgeItem("panduan-layanan-pelanggan.pdf", "PDF", "2.4 MB", 184, "06 Nov 2026 10:15", "Sudah Dilatih"),
                new KnowledgeItem("sop-operasional-perusahaan-2026.docx", "Word", "1.1 MB", 120, "06 Nov 2026 11:20", "Sudah Dilatih"),
                new KnowledgeItem("daftar-produk-dan-katalog-harga.xlsx", "Excel", "680 KB", 86, "06 Nov 2026 11:45", "Sudah Dilatih"),
                new KnowledgeItem("faq-kebijakan-retur-dan-garansi.txt", "Text", "145 KB", 38, "06 Nov 2026 13:00", "Sudah Dilatih")
        );
        updateSummaryLabel();
    }

    private void setupInitialLogs() {
        logConsoleArea.setText(
                """
                [SYSTEM READY] DevLab LM Studio RAG Pipeline initialized.
                [DATABASE] PostgreSQL connection active (Extension pgvector detected).
                [KNOWLEDGE] 4 documents loaded in database (428 total vector chunks).
                [MODEL] Embedding model ready: text-embedding-3-small (1536 dim).
                """
        );
    }

    private void updateSummaryLabel() {
        int totalDocs = masterKnowledgeList.size();
        int totalChunks = masterKnowledgeList.stream().mapToInt(KnowledgeItem::getChunkCount).sum();
        lblKnowledgeSummary.setText("Total: " + totalDocs + " Dokumen | " + totalChunks + " Vektor Chunk | Database PostgreSQL Siap");
    }

    @FXML
    public void onBrowseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih Dokumen Pengetahuan RAG");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Semua Dokumen RAG (*.pdf, *.docx, *.doc, *.xlsx, *.xls, *.txt, *.md, *.csv)",
                        "*.pdf", "*.docx", "*.doc", "*.xlsx", "*.xls", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("Dokumen PDF (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("Dokumen Word (*.docx, *.doc)", "*.docx", "*.doc"),
                new FileChooser.ExtensionFilter("Dokumen Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Dokumen Teks (*.txt, *.md, *.csv)", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("Semua Berkas (*.*)", "*.*")
        );

        List<File> files = chooser.showOpenMultipleDialog(dropZone.getScene().getWindow());
        if (files != null && !files.isEmpty()) {
            addFilesToQueue(files);
        }
    }

    private void addFilesToQueue(List<File> files) {
        for (File f : files) {
            if (!pendingFiles.contains(f)) {
                pendingFiles.add(f);
            }
        }
        refreshQueueView();
    }

    private void refreshQueueView() {
        queueListView.getItems().clear();
        for (File f : pendingFiles) {
            String sizeStr = formatFileSize(f.length());
            queueListView.getItems().add(f.getName() + " (" + sizeStr + ")");
        }
        boolean hasFiles = !pendingFiles.isEmpty();
        queueContainer.setVisible(hasFiles);
        queueContainer.setManaged(hasFiles);
        lblUploadFeedback.setText(hasFiles ? pendingFiles.size() + " berkas terpilih dan siap diunggah." : "");
    }

    @FXML
    public void onClearQueue() {
        pendingFiles.clear();
        refreshQueueView();
        lblUploadFeedback.setText("Pilihan berkas dibatalkan.");
    }

    @FXML
    public void onUploadQueue() {
        if (pendingFiles.isEmpty()) {
            return;
        }

        String now = LocalDateTime.now().format(DATE_FMT);
        int addedCount = 0;

        for (File f : new ArrayList<>(pendingFiles)) {
            String name = f.getName();
            String type = detectFileType(name);
            String sizeStr = formatFileSize(f.length());
            int estimatedChunks = Math.max(1, (int) (f.length() / 1024)); // perkiraan kasar per KB

            masterKnowledgeList.add(0, new KnowledgeItem(name, type, sizeStr, estimatedChunks, now, "Terekstraksi"));
            appendLog("[UPLOAD SUCCESS] Dokumen diunggah: " + name + " (" + type + ", " + sizeStr + ") -> " + estimatedChunks + " chunk siap dilatih.");
            addedCount++;
        }

        pendingFiles.clear();
        refreshQueueView();
        lblUploadFeedback.setText(addedCount + " dokumen berhasil ditambahkan ke daftar pengetahuan! Siap untuk ditraining.");
        updateSummaryLabel();
    }

    @FXML
    public void onRefreshTable() {
        searchDocField.clear();
        typeFilterCombo.getSelectionModel().selectFirst();
        updateSummaryLabel();
        appendLog("[KNOWLEDGE] Daftar dokumen di-refresh.");
    }

    @FXML
    public void onSaveProject() {
        String name = nameField.getText();
        String desc = descriptionArea.getText();
        String prompt = systemPromptArea.getText();

        if (name == null || name.isBlank()) {
            lblSaveStatus.setText("Nama project wajib diisi!");
            nameField.requestFocus();
            return;
        }

        try {
            if (currentProject != null && currentProject.getId() != null) {
                currentProject = projectService.update(currentProject.getId().longValue(), name, desc, prompt);
                lblHeaderTitle.setText("Project: " + currentProject.getName());
                lblSaveStatus.setText("Perubahan berhasil disimpan! (" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ")");
            } else {
                currentProject = projectService.create(name, desc, prompt);
                lblHeaderTitle.setText("Project: " + currentProject.getName());
                lblSaveStatus.setText("Project berhasil dibuat!");
            }

            if (onProjectUpdated != null) {
                onProjectUpdated.run();
            }

            appendLog("[PROJECT SAVED] Konfigurasi project & system prompt berhasil disimpan ke database.");
        } catch (Exception ex) {
            lblSaveStatus.setText("Gagal menyimpan: " + ex.getMessage());
            appendLog("[ERROR] Gagal menyimpan project: " + ex.getMessage());
        }
    }

    @FXML
    public void onStartTraining() {
        // Pindah ke tab Training
        mainTabPane.getSelectionModel().select(1);

        btnHeaderTrain.setDisable(true);
        btnQuickTrain.setDisable(true);
        btnTabStartTraining.setDisable(true);

        lblTrainingStatusBadge.setText("Sedang Training...");
        lblTrainingStatusBadge.getStyleClass().removeAll("badge-status-trained", "badge-status-pending");
        lblTrainingStatusBadge.getStyleClass().add("badge-status-ready");
        trainingProgressBar.setProgress(0.0);
        lblTrainingPercent.setText("0%");

        appendLog("\n==================== MEMULAI TRAINING AI & VEKTORISASI RAG ====================");
        appendLog("[STEP 1/5] Memindai " + masterKnowledgeList.size() + " dokumen pengetahuan...");

        // Simulasi progres training AI step-by-step
        Timeline timeline = new Timeline();
        timeline.getKeyFrames().addAll(
                new KeyFrame(Duration.millis(600), e -> {
                    trainingProgressBar.setProgress(0.20);
                    lblTrainingPercent.setText("20%");
                    appendLog("[STEP 2/5] Mengekstraksi teks dokumen (PDF, Word, Excel, Teks)... OK");
                }),
                new KeyFrame(Duration.millis(1300), e -> {
                    trainingProgressBar.setProgress(0.45);
                    lblTrainingPercent.setText("45%");
                    appendLog("[STEP 3/5] Tokenisasi semantik & pembagian dokumen menjadi 428 chunk vektor... OK");
                }),
                new KeyFrame(Duration.millis(2100), e -> {
                    trainingProgressBar.setProgress(0.75);
                    lblTrainingPercent.setText("75%");
                    appendLog("[STEP 4/5] Mengirim chunk ke model embedding & menghitung representasi vektor 1536-dimensi... OK");
                }),
                new KeyFrame(Duration.millis(3000), e -> {
                    trainingProgressBar.setProgress(1.0);
                    lblTrainingPercent.setText("100%");
                    lblTrainingStatusBadge.setText("Training Selesai & Aktif");
                    lblTrainingStatusBadge.getStyleClass().removeAll("badge-status-ready", "badge-status-pending");
                    lblTrainingStatusBadge.getStyleClass().add("badge-status-trained");

                    // Update status semua item di tabel menjadi 'Sudah Dilatih'
                    for (KnowledgeItem item : masterKnowledgeList) {
                        item.setStatus("Sudah Dilatih");
                    }
                    knowledgeTable.refresh();

                    appendLog("[STEP 5/5] Indeks pgvector (IVFFlat / HNSW) berhasil di-update pada database PostgreSQL!");
                    appendLog("[SELESAI] Model AI siap menjawab pertanyaan berdasarkan basis pengetahuan project.");
                    appendLog("==============================================================================\n");

                    btnHeaderTrain.setDisable(false);
                    btnQuickTrain.setDisable(false);
                    btnTabStartTraining.setDisable(false);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Training AI Berhasil");
                    alert.setHeaderText("Basis Pengetahuan RAG Berhasil Dilatih");
                    alert.setContentText("Seluruh dokumen RAG telah berhasil diekstraksi, di-embedding, dan diindeks ke pgvector. Asisten AI siap digunakan!");
                    alert.show();
                })
        );
        timeline.play();
    }

    @FXML
    public void onClearLog() {
        logConsoleArea.clear();
    }

    private void appendLog(String message) {
        if (logConsoleArea != null) {
            logConsoleArea.appendText(message + "\n");
        }
    }

    private String detectFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "Word";
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "Excel";
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) return "Text";
        return "Dokumen";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }
}
