package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.service.ProjectService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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

    @FXML private ScrollPane scrollPane;
    @FXML private GridPane cardGrid;

    private final List<Node> cards = new ArrayList<>();
    private int currentColumns = -1;

    public DashboardController(ProjectService projectService) {
        this.projectService = projectService;
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
            List<ProjectEntity> projects = projectService.findAll();
            cards.add(createAddCard());
            for (ProjectEntity p : projects) {
                String created = p.getCreatedAt() == null ? "-" : DATE_FMT.format(p.getCreatedAt());
                cards.add(createProjectCard(p.getName(), created));
            }
        } catch (Exception e) {
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

    private VBox createProjectCard(String name, String created) {
        Label title = new Label(name);
        title.getStyleClass().add("title-4");
        Label info = new Label(created);
        info.getStyleClass().add("text-muted");

        VBox card = new VBox(6, title, info);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setPrefHeight(140);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("project-card");
        card.setOnMouseClicked(e -> onOpenProject(name));
        return card;
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

    // membuka project existing
    private void onOpenProject(String name) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Project");
        alert.setHeaderText(name);
        alert.setContentText("Membuka proyek: " + name);
        alert.initOwner(cardGrid.getScene().getWindow());
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(cardGrid.getScene().getWindow());
        alert.showAndWait();
    }
}