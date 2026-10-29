package id.guruh.devlablm.ui.controller;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SettingsController {

    @FXML
    private ComboBox<String> themeComboBox;

    @FXML
    public void initialize() {
        themeComboBox.getItems().addAll(
                "Primer Light (Default)",
                "Primer Dark",
                "Cupertino Light",
                "Cupertino Dark",
                "Nord Light",
                "Nord Dark",
                "Dracula"
        );

        themeComboBox.setValue("Primer Light (Default)");

        themeComboBox.setOnAction(e -> {
            String selected = themeComboBox.getValue();
            if (selected == null) return;

            switch (selected) {
                case "Primer Dark" -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                case "Cupertino Light" -> Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
                case "Cupertino Dark" -> Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
                case "Nord Light" -> Application.setUserAgentStylesheet(new NordLight().getUserAgentStylesheet());
                case "Nord Dark" -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
                case "Dracula" -> Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());
                default -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        });
    }
}
