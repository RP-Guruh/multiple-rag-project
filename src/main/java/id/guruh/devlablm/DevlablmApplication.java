package id.guruh.devlablm;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DevlablmApplication {

	public static void main(String[] args) {
		// Launch JavaFX Application, yang kemudian menginisialisasi Spring Context
		Application.launch(JavaFxApplication.class, args);
	}

}
