package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.service.ProjectService;
import id.guruh.devlablm.ui.FxmlViewLoader;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class DashboardController {

    private static final double MIN_CARD_WIDTH = 200;
    private static final double GAP = 16;
    private static final double PADDING = 32; // padding kiri + kanan pada grid

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
                    .withZone(ZoneId.systemDefault());

    private final ProjectService projectService;
    private final id.guruh.devlablm.postgres.service.PostgresConnectionService postgresConnectionService;
    private final FxmlViewLoader fxmlViewLoader;

    @FXML private ScrollPane scrollPane;
    @FXML private GridPane cardGrid;

    private final List<Node> cards = new ArrayList<>();
    private int currentColumns = -1;

    public DashboardController(
            ProjectService projectService,
            id.guruh.devlablm.postgres.service.PostgresConnectionService postgresConnectionService,
            FxmlViewLoader fxmlViewLoader) {
        this.projectService = projectService;
        this.postgresConnectionService = postgresConnectionService;
        this.fxmlViewLoader = fxmlViewLoader;
    }

    @FXML
    private void initialize() {
        refreshCards();
        scrollPane.viewportBoundsProperty().addListener(
                (obs, old, bounds) -> relayout(bounds.getWidth()));
    }

    private void refreshCards() {
        cards.clear();

        try {
            if (!postgresConnectionService.isConnected()) {
                postgresConnectionService.initConnection();
            }

            List<ProjectEntity> projects = projectService.findAll();
            cards.add(createAddCard());
            for (ProjectEntity p : projects) {
                String created = p.getCreatedAt() == null ? "-" : DATE_FMT.format(p.getCreatedAt());
                cards.add(createProjectCard(p, created));
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(DashboardController.class)
                    .error("Failed to fetch projects in Dashboard: {}", e.getMessage(), e);
            cards.clear();
            cards.add(createDatabaseNotReadyCard());
        }

        currentColumns = -1;
        relayout(scrollPane.getViewportBounds().getWidth());
    }

    private VBox createDatabaseNotReadyCard() {
        Label title = new Label("Database belum diatur");
        title.getStyleClass().add("title-4");
        Label info = new Label("Atur koneksi PostgreSQL di menu Database Setting, lalu buka kembali Dashboard.");
        info.getStyleClass().add("text-muted");
        info.setWrapText(true);

        VBox card = new VBox(6, title, info);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setPrefHeight(140);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("project-card");
        return card;
    }
    private void relayout(double width) {
        if (width <= 0) return;

        int cols = Math.max(1, (int) ((width - PADDING + GAP) / (MIN_CARD_WIDTH + GAP)));
        if (cols == currentColumns) return;
        currentColumns = cols;

        cardGrid.getChildren().clear();
        cardGrid.getColumnConstraints().clear();

        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            cardGrid.getColumnConstraints().add(cc);
        }

        for (int i = 0; i < cards.size(); i++) {
            Node card = cards.get(i);
            GridPane.setHgrow(card, Priority.ALWAYS);
            cardGrid.add(card, i % cols, i / cols);
        }
    }

    private VBox createAddCard() {
        Label plus = new Label("+");
        plus.getStyleClass().add("card-plus");
        Label text = new Label("Add Project");

        VBox card = new VBox(8, plus, text);
        card.setAlignment(Pos.CENTER);
        card.setPrefHeight(140);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("project-card", "project-card-add");
        card.setOnMouseClicked(e -> onAddProject());
        return card;
    }

    private VBox createProjectCard(ProjectEntity project, String created) {
        Label title = new Label(project.getName());
        title.getStyleClass().add("title-4");
        Label info = new Label(created);
        info.getStyleClass().add("text-muted");

        org.kordamp.ikonli.javafx.FontIcon deleteIcon = new org.kordamp.ikonli.javafx.FontIcon("mdal-delete_outline");
        deleteIcon.setIconSize(16);

        javafx.scene.control.Button btnDelete = new javafx.scene.control.Button();
        btnDelete.setGraphic(deleteIcon);
        btnDelete.getStyleClass().addAll("button-subtle", "danger");
        btnDelete.setTooltip(new javafx.scene.control.Tooltip("Delete project"));
        btnDelete.setOnAction(e -> {
            e.consume(); // Hindari card terklik/terbuka
            confirmAndDeleteProject(project);
        });

        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(8, title);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, Priority.ALWAYS);
        headerBox.getChildren().addAll(spacer, btnDelete);

        VBox card = new VBox(6, headerBox, info);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14, 14, 14, 16));
        card.setPrefHeight(140);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("project-card");
        card.setOnMouseClicked(e -> onOpenProject(project));
        return card;
    }

    private void confirmAndDeleteProject(ProjectEntity project) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Project");
        alert.setHeaderText("Delete project: " + project.getName() + "?");
        alert.setContentText("All associated documents, stored files, and vector embeddings will be permanently removed. Continue?");
        alert.initOwner(cardGrid.getScene().getWindow());

        alert.showAndWait().ifPresent(res -> {
            if (res == javafx.scene.control.ButtonType.OK) {
                try {
                    projectService.delete(project.getId().longValue());
                    refreshCards();
                } catch (Exception ex) {
                    showError("Failed to delete project: " + ex.getMessage());
                }
            }
        });
    }

    private void onAddProject() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Project");
        dialog.setHeaderText("Create a new chatbot project");
        dialog.setContentText("Project name:");
        dialog.initOwner(cardGrid.getScene().getWindow());

        dialog.showAndWait()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> {
                    try {
                        projectService.create(name, null, null);
                        refreshCards();
                    } catch (IllegalArgumentException ex) {
                        showError(ex.getMessage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showError("Gagal membuat project. Periksa koneksi database.");
                    }
                });
    }

    // membuka project existing di window baru
    private void onOpenProject(ProjectEntity project) {
        try {
            Stage stage = new Stage();
            FxmlViewLoader.ViewNode node = fxmlViewLoader.loadView("/fxml/project-detail.fxml");
            if (node.controller() instanceof ProjectDetailController detailController) {
                detailController.initProject(project, this::refreshCards);
            }

            Scene scene = new Scene(node.root(), 1200, 780);
            String cssUrl = fxmlViewLoader.resolveCssUrl("/css/app.css");
            if (!cssUrl.isEmpty()) {
                scene.getStylesheets().add(cssUrl);
            }

            stage.setTitle("DevLab LM Studio - Project: " + project.getName());
            stage.setMinWidth(960);
            stage.setMinHeight(640);
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Gagal membuka window project: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(cardGrid.getScene().getWindow());
        alert.showAndWait();
    }
}