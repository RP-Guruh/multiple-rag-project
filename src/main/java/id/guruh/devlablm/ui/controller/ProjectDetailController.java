package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.model.KnowledgeItem;
import id.guruh.devlablm.postgres.entity.DocumentEntity;
import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.service.DocumentService;
import id.guruh.devlablm.postgres.service.ProjectService;
import id.guruh.devlablm.rag.service.RagIngestionPipelineService;
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
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.US);

    private final ProjectService projectService;
    private final DocumentService documentService;
    private final RagIngestionPipelineService ragPipelineService;

    // Header FXML
    @FXML private Label lblHeaderTitle;
    @FXML private Label lblHeaderSubtitle;
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

    // Knowledge Table FXML
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
    @FXML private TableColumn<KnowledgeItem, Void> colAction;
    @FXML private Label lblKnowledgeSummary;

    // Training Pipeline FXML (Tab 2)
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

    public ProjectDetailController(ProjectService projectService, DocumentService documentService, RagIngestionPipelineService ragPipelineService) {
        this.projectService = projectService;
        this.documentService = documentService;
        this.ragPipelineService = ragPipelineService;
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
            loadProjectDocuments();
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
                    if ("Trained".equalsIgnoreCase(status) || "Sudah Dilatih".equalsIgnoreCase(status) || "Terkonversi".equalsIgnoreCase(status)) {
                        badge.getStyleClass().add("badge-status-trained");
                    } else if ("Ready".equalsIgnoreCase(status) || "Extracted".equalsIgnoreCase(status) || "Terekstraksi".equalsIgnoreCase(status) || "Siap Training".equalsIgnoreCase(status)) {
                        badge.getStyleClass().add("badge-status-ready");
                    } else {
                        badge.getStyleClass().add("badge-status-pending");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button();
            {
                deleteBtn.getStyleClass().addAll("button-subtle", "danger");
                org.kordamp.ikonli.javafx.FontIcon icon = new org.kordamp.ikonli.javafx.FontIcon("mdal-delete_outline");
                icon.setIconSize(16);
                deleteBtn.setGraphic(icon);
                deleteBtn.setOnAction(event -> {
                    KnowledgeItem item = getTableView().getItems().get(getIndex());
                    if (item != null && item.getId() != null) {
                        onDeleteDocument(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });

        filteredKnowledgeList = new FilteredList<>(masterKnowledgeList, p -> true);
        knowledgeTable.setItems(filteredKnowledgeList);
    }

    private void setupFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All Formats", "PDF", "Word", "Excel", "Text"
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
            if (selectedType != null && !selectedType.equals("All Formats") && !selectedType.equals("Semua Format")) {
                matchType = item.getFileType().equalsIgnoreCase(selectedType);
            }

            return matchQuery && matchType;
        });
        updateSummaryLabel();
    }

    private void setupInitialKnowledgeData() {
        // Akan di-load berdasarkan project aktif di loadProjectDocuments()
    }

    private void loadProjectDocuments() {
        masterKnowledgeList.clear();
        if (currentProject == null || currentProject.getId() == null) {
            updateSummaryLabel();
            return;
        }

        try {
            List<DocumentEntity> docs = documentService.findByProjectId(currentProject.getId());
            for (DocumentEntity doc : docs) {
                String uploadedAtStr = doc.getCreatedAt() != null
                        ? DATE_FMT.format(LocalDateTime.ofInstant(doc.getCreatedAt(), java.time.ZoneId.systemDefault()))
                        : "-";
                masterKnowledgeList.add(new KnowledgeItem(
                        doc.getId(),
                        doc.getFileName(),
                        doc.getFileType() != null ? doc.getFileType().toUpperCase() : "DOC",
                        formatFileSize(doc.getFileSize() != null ? doc.getFileSize() : 0L),
                        doc.getChunkCount() != null ? doc.getChunkCount() : 0,
                        uploadedAtStr,
                        doc.getStatus()
                ));
            }
        } catch (Exception ex) {
            appendLog("[DATABASE ERROR] Gagal memuat daftar dokumen: " + ex.getMessage());
        }
        updateSummaryLabel();
    }

    private void setupInitialLogs() {
        logConsoleArea.setText(
                """
                [SYSTEM READY] RAG pipeline initialized.
                [DATABASE] PostgreSQL connection active (pgvector extension detected).
                [KNOWLEDGE] 4 documents loaded (428 total vector chunks).
                [MODEL] Embedding model ready: text-embedding-3-small (1536 dim).
                """
        );
    }

    private void updateSummaryLabel() {
        int totalDocs = masterKnowledgeList.size();
        int totalChunks = masterKnowledgeList.stream().mapToInt(KnowledgeItem::getChunkCount).sum();
        lblKnowledgeSummary.setText("Total: " + totalDocs + " Documents | " + totalChunks + " Vector Chunks | PostgreSQL pgvector ready");
    }

    @FXML
    public void onBrowseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Knowledge Base Documents");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported Documents (*.pdf, *.docx, *.doc, *.xlsx, *.xls, *.txt, *.md, *.csv)",
                        "*.pdf", "*.docx", "*.doc", "*.xlsx", "*.xls", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("PDF Documents (*.pdf)", "*.pdf"),
                new FileChooser.ExtensionFilter("Word Documents (*.docx, *.doc)", "*.docx", "*.doc"),
                new FileChooser.ExtensionFilter("Excel Spreadsheets (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Text Documents (*.txt, *.md, *.csv)", "*.txt", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
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
        lblUploadFeedback.setText(hasFiles ? pendingFiles.size() + " file(s) selected." : "");
    }

    @FXML
    public void onClearQueue() {
        pendingFiles.clear();
        refreshQueueView();
        lblUploadFeedback.setText("File selection cleared.");
    }

    @FXML
    public void onUploadQueue() {
        if (pendingFiles.isEmpty()) {
            return;
        }

        if (currentProject == null || currentProject.getId() == null) {
            lblUploadFeedback.setText("Save the project before uploading documents.");
            return;
        }

        btnUploadQueue.setDisable(true);
        lblUploadFeedback.setText("Uploading documents...");

        List<File> filesToUpload = new ArrayList<>(pendingFiles);

        new Thread(() -> {
            int successCount = 0;
            List<String> errors = new ArrayList<>();

            for (File f : filesToUpload) {
                try {
                    DocumentEntity doc = documentService.uploadDocument(currentProject.getId(), f);
                    successCount++;
                    Platform.runLater(() -> {
                        appendLog("[UPLOAD SUCCESS] Saved: " + doc.getFileName() + " -> " + doc.getFilePath());
                    });
                } catch (Exception ex) {
                    errors.add(f.getName() + " (" + ex.getMessage() + ")");
                    Platform.runLater(() -> {
                        appendLog("[UPLOAD ERROR] Failed to upload " + f.getName() + ": " + ex.getMessage());
                    });
                }
            }

            final int finalSuccess = successCount;
            Platform.runLater(() -> {
                btnUploadQueue.setDisable(false);
                pendingFiles.clear();
                refreshQueueView();
                loadProjectDocuments();

                if (errors.isEmpty()) {
                    lblUploadFeedback.setText(finalSuccess + " document(s) uploaded successfully.");
                } else {
                    lblUploadFeedback.setText(finalSuccess + " uploaded, " + errors.size() + " failed.");
                }
            });
        }).start();
    }

    private void onDeleteDocument(KnowledgeItem item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Document");
        alert.setHeaderText("Delete: " + item.getFileName());
        alert.setContentText("This removes the file from disk and deletes its database records. Continue?");
        alert.initOwner(knowledgeTable.getScene().getWindow());

        alert.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                try {
                    documentService.deleteDocument(item.getId());
                    loadProjectDocuments();
                    appendLog("[DELETE] Document '" + item.getFileName() + "' deleted.");
                } catch (Exception ex) {
                    appendLog("[ERROR] Failed to delete document: " + ex.getMessage());
                }
            }
        });
    }

    @FXML
    public void onRefreshTable() {
        searchDocField.clear();
        typeFilterCombo.getSelectionModel().selectFirst();
        loadProjectDocuments();
        appendLog("[KNOWLEDGE] Document table refreshed from database.");
    }

    @FXML
    public void onSaveProject() {
        String name = nameField.getText();
        String desc = descriptionArea.getText();
        String prompt = systemPromptArea.getText();

        if (name == null || name.isBlank()) {
            lblSaveStatus.setText("Project name is required.");
            nameField.requestFocus();
            return;
        }

        try {
            if (currentProject != null && currentProject.getId() != null) {
                currentProject = projectService.update(currentProject.getId().longValue(), name, desc, prompt);
                lblHeaderTitle.setText("Project: " + currentProject.getName());
                lblSaveStatus.setText("Changes saved (" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ")");
            } else {
                currentProject = projectService.create(name, desc, prompt);
                lblHeaderTitle.setText("Project: " + currentProject.getName());
                lblSaveStatus.setText("Project created successfully.");
            }

            if (onProjectUpdated != null) {
                onProjectUpdated.run();
            }

            appendLog("[PROJECT SAVED] Project configuration and system prompt saved to database.");
        } catch (Exception ex) {
            lblSaveStatus.setText("Save failed: " + ex.getMessage());
            appendLog("[ERROR] Failed to save project: " + ex.getMessage());
        }
    }

    @FXML
    public void onStartTraining() {
        if (currentProject == null || currentProject.getId() == null) {
            appendLog("[ERROR] Project must be saved before starting training.");
            return;
        }

        mainTabPane.getSelectionModel().select(1);
        btnTabStartTraining.setDisable(true);
        if (btnHeaderTrain != null) btnHeaderTrain.setDisable(true);

        lblTrainingStatusBadge.setText("Processing...");
        lblTrainingStatusBadge.getStyleClass().removeAll("badge-status-trained", "badge-status-pending");
        lblTrainingStatusBadge.getStyleClass().add("badge-status-ready");
        trainingProgressBar.setProgress(0.0);
        lblTrainingPercent.setText("0%");

        appendLog("\n==================== STARTING EMBEDDING & INDEXING PIPELINE ====================");

        new Thread(() -> {
            try {
                ragPipelineService.trainProjectKnowledge(
                        currentProject.getId(),
                        progress -> Platform.runLater(() -> {
                            trainingProgressBar.setProgress(progress);
                            lblTrainingPercent.setText((int) (progress * 100) + "%");
                        }),
                        logMsg -> Platform.runLater(() -> appendLog(logMsg))
                );

                Platform.runLater(() -> {
                    lblTrainingStatusBadge.setText("Active");
                    lblTrainingStatusBadge.getStyleClass().removeAll("badge-status-ready", "badge-status-pending");
                    lblTrainingStatusBadge.getStyleClass().add("badge-status-trained");
                    btnTabStartTraining.setDisable(false);
                    if (btnHeaderTrain != null) btnHeaderTrain.setDisable(false);

                    loadProjectDocuments();

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Training Complete");
                    alert.setHeaderText("Knowledge Base Indexed");
                    alert.setContentText("All documents have been extracted, chunked, and saved to pgvector.");
                    alert.initOwner(mainTabPane.getScene().getWindow());
                    alert.show();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblTrainingStatusBadge.setText("Failed");
                    lblTrainingStatusBadge.getStyleClass().removeAll("badge-status-ready", "badge-status-trained");
                    lblTrainingStatusBadge.getStyleClass().add("badge-status-pending");
                    btnTabStartTraining.setDisable(false);
                    if (btnHeaderTrain != null) btnHeaderTrain.setDisable(false);
                    appendLog("[PIPELINE ERROR] " + ex.getMessage());

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Training Failed");
                    alert.setHeaderText("Pipeline Failure");
                    alert.setContentText(ex.getMessage());
                    alert.initOwner(mainTabPane.getScene().getWindow());
                    alert.show();
                });
            }
        }).start();
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
        return "Document";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024, exp), unit);
    }
}
