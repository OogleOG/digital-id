package com.oogle.digitalid.ui;

import com.oogle.digitalid.service.IdIssuer;
import com.google.gson.GsonBuilder;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public class IssueController {

    @FXML private TextField tfFullName;
    @FXML private TextField tfDob;
    @FXML private TextField tfEmail;
    @FXML private TextField tfPhone;
    @FXML private Slider   slYears;
    @FXML private Label    lbYears;
    @FXML private Label    lbStatus;
    @FXML private ImageView qrPreview;
    @FXML private TextArea taJson;

    private Path outDir = Path.of("output");

    // Optional: put a URL here to make QR clickable
    private final IdIssuer issuer = new IdIssuer(
            Path.of("keys"),
            "Oogle ID Authority",
            null // "https://yourdomain.tld/verify"
    );

    @FXML
    public void initialize() {
        lbYears.setText((int)slYears.getValue() + " years");
        slYears.valueProperty().addListener((obs,ov,nv)-> lbYears.setText(nv.intValue() + " years"));
        updateStatus("Ready.");
    }

    @FXML
    void onChooseOutput(ActionEvent e) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Choose Output Folder");
        File f = dc.showDialog(getWindow(e));
        if (f != null) {
            outDir = f.toPath();
            updateStatus("Output -> " + outDir.toAbsolutePath());
        }
    }

    @FXML
    void onIssue(ActionEvent e) {
        try {
            if (tfFullName.getText().trim().isEmpty()) {
                alert("Full name is required.");
                return;
            }
            int years = (int) slYears.getValue();
            long exp = ZonedDateTime.now(ZoneOffset.UTC)
                    .plusYears(years)
                    .toInstant()
                    .getEpochSecond();

            var d = issuer.issue(
                    tfFullName.getText().trim(),
                    emptyToNull(tfDob.getText()),
                    emptyToNull(tfEmail.getText()),
                    emptyToNull(tfPhone.getText()),
                    exp
            );
            var saved = issuer.saveIdFiles(d, outDir);
            updateStatus("Issued: " + saved.getFileName());

            // Show the generated ID card preview
            var card = outDir.resolve(d.id + ".card.png").toFile();
            Image img = new Image(card.toURI().toString());
            qrPreview.setImage(img);

            // Show JSON
            taJson.setText(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(d));

        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Failed to issue ID: " + ex.getMessage());
        }
    }

    @FXML
    void onCopyJson(ActionEvent e) {
        String json = taJson.getText();
        if (json == null || json.isEmpty()) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(json);
        Clipboard.getSystemClipboard().setContent(cc);
        updateStatus("JSON copied to clipboard.");
    }

    @FXML
    void onGoVerifyTab(ActionEvent e) {
        try {
            Parent verify = FXMLLoader.load(getClass().getResource("/ui/verify_view.fxml"));
            Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();
            stage.setScene(new Scene(verify, 960, 640));
            stage.getScene().getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        } catch (Exception ex) {
            alert("Failed to open Verify view: " + ex.getMessage());
        }
    }

    private void updateStatus(String s) { lbStatus.setText(s); }
    private static String emptyToNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }
    private Stage getWindow(ActionEvent e) { return (Stage) ((Node)e.getSource()).getScene().getWindow(); }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
