package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.service.AppService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.control.Alert;
import javafx.scene.input.MouseEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DashboardController {

    private static final double MIN_CARD_WIDTH = 200;
    private static final double GAP = 16;
    private static final double PADDING = 32; // padding kiri + kanan pada grid

    private final AppService appService;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private GridPane cardGrid;

    private final List<Node> cards = new ArrayList<>();
    private int currentColumns = -1;

    // Dependency Injection dari Spring Boot
    public DashboardController(AppService appService) {
        this.appService = appService;
    }

    @FXML
    private void initialize() {
        refreshCards();
        scrollPane.viewportBoundsProperty().addListener(
                (obs, old, bounds) -> relayout(bounds.getWidth()));
    }

    private void refreshCards() {
        cards.clear();
        cards.add(createAddCard());

        // TODO: ganti dengan method asli di AppService
        // for (Project p : appService.findAllProjects()) {
        //     cards.add(createProjectCard(p.getName(), p.getDocCount()));
        // }

        // Data contoh sementara, hapus kalau service sudah siap
        for (int i = 1; i <= 100; i++) {
            cards.add(createProjectCard("Project " + i, i * 3));
        }

        currentColumns = -1; // paksa susun ulang
        relayout(scrollPane.getViewportBounds().getWidth());
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

    private VBox createProjectCard(String name, int docCount) {
        Label title = new Label(name);
        title.getStyleClass().add("title-4");
        Label info = new Label(docCount + " documents");
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

    // membuat project baru
    private void onAddProject() {
        // TODO: tampilkan dialog nama proyek, simpan lewat appService, lalu refreshCards()
    }

    // membuka project exisiting
    private void onOpenProject(String name) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Project");
        alert.setHeaderText(name);
        alert.setContentText("Membuka proyek: " + name);
        alert.initOwner(cardGrid.getScene().getWindow());
        alert.showAndWait();
    }


}
