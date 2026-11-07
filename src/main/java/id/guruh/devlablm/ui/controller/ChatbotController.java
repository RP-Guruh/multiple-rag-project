package id.guruh.devlablm.ui.controller;

import id.guruh.devlablm.postgres.entity.ProjectEntity;
import id.guruh.devlablm.postgres.service.PostgresConnectionService;
import id.guruh.devlablm.postgres.service.ProjectService;
import id.guruh.devlablm.rag.service.ChatbotInferenceService;
import id.guruh.devlablm.rag.service.RagRetrievalService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ChatbotController {

    private final ProjectService projectService;
    private final PostgresConnectionService postgresConnectionService;
    private final ChatbotInferenceService chatbotInferenceService;

    @FXML private ComboBox<ProjectEntity> projectComboBox;
    @FXML private Button btnRefreshProjects;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox messageContainer;
    @FXML private Label lblChatStatus;
    @FXML private Button btnClearChat;
    @FXML private TextArea chatInputField;
    @FXML private Button btnSendMessage;

    public ChatbotController(
            ProjectService projectService,
            PostgresConnectionService postgresConnectionService,
            ChatbotInferenceService chatbotInferenceService) {
        this.projectService = projectService;
        this.postgresConnectionService = postgresConnectionService;
        this.chatbotInferenceService = chatbotInferenceService;
    }

    @FXML
    private void initialize() {
        setupProjectComboBox();
        loadProjects();

        // Auto-scroll to bottom on new messages
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) ->
                chatScrollPane.setVvalue(1.0));
    }

    private void setupProjectComboBox() {
        projectComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProjectEntity project) {
                return project == null ? "" : project.getName();
            }

            @Override
            public ProjectEntity fromString(String string) {
                return null;
            }
        });

        projectComboBox.valueProperty().addListener((obs, oldProj, newProj) -> {
            if (newProj != null) {
                lblChatStatus.setText("Active Project: " + newProj.getName() + " (Strict knowledge isolation enabled)");
                appendSystemMessage("Switched to project: " + newProj.getName() + ". Only documents in this project will be referenced.");
            } else {
                lblChatStatus.setText("No project selected.");
            }
        });
    }

    @FXML
    public void onRefreshProjects() {
        loadProjects();
    }

    private void loadProjects() {
        try {
            if (!postgresConnectionService.isConnected()) {
                postgresConnectionService.initConnection();
            }
            List<ProjectEntity> projects = projectService.findAll();
            projectComboBox.setItems(FXCollections.observableArrayList(projects));
            if (!projects.isEmpty()) {
                projectComboBox.getSelectionModel().selectFirst();
            } else {
                lblChatStatus.setText("No projects available. Create a project in Dashboard first.");
            }
        } catch (Exception ex) {
            lblChatStatus.setText("Failed to load projects: " + ex.getMessage());
        }
    }

    @FXML
    public void onInputKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            onSendMessage();
        }
    }

    @FXML
    public void onSendMessage() {
        String text = chatInputField.getText() == null ? "" : chatInputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        ProjectEntity selectedProject = projectComboBox.getValue();
        if (selectedProject == null) {
            appendSystemMessage("Please select a target project before sending a message.");
            return;
        }

        // Add user bubble
        appendUserMessage(text);
        chatInputField.clear();

        // Disable input while generating
        btnSendMessage.setDisable(true);
        chatInputField.setDisable(true);
        lblChatStatus.setText("Retrieving isolated knowledge & querying AI model...");

        new Thread(() -> {
            try {
                ChatbotInferenceService.ChatResponse response =
                        chatbotInferenceService.chat(selectedProject, text);

                Platform.runLater(() -> {
                    appendAiMessage(response.answer(), response.contexts());
                    btnSendMessage.setDisable(false);
                    chatInputField.setDisable(false);
                    chatInputField.requestFocus();
                    lblChatStatus.setText("Active Project: " + selectedProject.getName());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    appendSystemMessage("Error querying AI: " + ex.getMessage());
                    btnSendMessage.setDisable(false);
                    chatInputField.setDisable(false);
                    lblChatStatus.setText("Failed to get response: " + ex.getMessage());
                });
            }
        }).start();
    }

    @FXML
    public void onClearChat() {
        messageContainer.getChildren().clear();
        lblChatStatus.setText("Chat cleared.");
    }

    private void appendUserMessage(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(560);

        VBox bubble = new VBox(lbl);
        bubble.getStyleClass().add("chat-bubble-user");

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        messageContainer.getChildren().add(row);
    }

    private void appendAiMessage(String answer, List<RagRetrievalService.RetrievedContext> contexts) {
        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("chat-bubble-ai");
        bubble.setMaxWidth(620);

        // Render response: formats markdown-style bold into rich JavaFX text flow
        javafx.scene.text.TextFlow textFlow = renderFormattedText(answer);
        bubble.getChildren().add(textFlow);

        // Display citation / context badges
        if (contexts != null && !contexts.isEmpty()) {
            HBox badgesBox = new HBox(6);
            badgesBox.setAlignment(Pos.CENTER_LEFT);
            Label sourceTitle = new Label("Citations:");
            sourceTitle.getStyleClass().add("text-muted");
            badgesBox.getChildren().add(sourceTitle);

            for (int i = 0; i < contexts.size(); i++) {
                RagRetrievalService.RetrievedContext ctx = contexts.get(i);
                Label badge = new Label(String.format("Chunk #%d (%.0f%%)", ctx.chunkId(), ctx.similarity() * 100));
                badge.getStyleClass().add("chat-context-badge");
                badge.setTooltip(new Tooltip(ctx.content()));
                badgesBox.getChildren().add(badge);
            }
            bubble.getChildren().add(badgesBox);
        }

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        messageContainer.getChildren().add(row);
    }

    private void appendSystemMessage(String text) {
        Label lbl = new Label(text);
        lbl.getStyleClass().addAll("text-muted", "text-small");
        lbl.setWrapText(true);

        HBox row = new HBox(lbl);
        row.setAlignment(Pos.CENTER);
        messageContainer.getChildren().add(row);
    }

    /**
     * Parses markdown bold (**text**) and renders into styled JavaFX Text elements
     * without exposing raw asterisk markup.
     */
    private javafx.scene.text.TextFlow renderFormattedText(String content) {
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        if (content == null || content.isBlank()) {
            return flow;
        }

        String[] parts = content.split("(?<=\\*\\*)|(?=\\*\\*)");
        boolean isBold = false;

        for (String part : parts) {
            if ("**".equals(part)) {
                isBold = !isBold;
                continue;
            }
            javafx.scene.text.Text textNode = new javafx.scene.text.Text(part);
            textNode.getStyleClass().add("text");
            if (isBold) {
                textNode.setStyle("-fx-font-weight: bold;");
            }
            flow.getChildren().add(textNode);
        }
        return flow;
    }
}
