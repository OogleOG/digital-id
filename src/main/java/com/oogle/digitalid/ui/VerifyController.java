package com.oogle.digitalid.ui;

import com.oogle.digitalid.service.IdIssuer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

public class VerifyController {

    @FXML private TextField tfPath;
    @FXML private Label lbResult;

    private final IdIssuer issuer = new IdIssuer(Path.of("keys"), "Oogle ID Authority");

    @FXML
    public void initialize() {
        lbResult.setText("Choose an ID file (.did.json) to verify.");
    }

    @FXML
    void onBrowse(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Digital ID (*.did.json)", "*.did.json"));
        File f = fc.showOpenDialog(getWindow(e));
        if (f != null) tfPath.setText(f.getAbsolutePath());
    }

    @FXML
    void onVerify(ActionEvent e) {
        try {
            if (tfPath.getText().trim().isEmpty()) {
                alert("Select a .did.json file.");
                return;
            }
            boolean ok = issuer.verify(Path.of(tfPath.getText().trim()));
            lbResult.setText(ok ? "VALID ✅" : "INVALID ❌");
        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Failed to verify: " + ex.getMessage());
        }
    }

    @FXML
    void onBack(ActionEvent e) {
        try {
            Parent issue = FXMLLoader.load(getClass().getResource("/ui/issue_view.fxml"));
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            stage.setScene(new Scene(issue, 960, 640));
            stage.getScene().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        } catch (Exception ex) {
            alert("Failed to go back: " + ex.getMessage());
        }
    }

    private Stage getWindow(ActionEvent e) {
        return (Stage) ((Node)e.getSource()).getScene().getWindow();
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
