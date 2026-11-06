package id.guruh.devlablm.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class KnowledgeItem {

    private final StringProperty fileName = new SimpleStringProperty();
    private final StringProperty fileType = new SimpleStringProperty();
    private final StringProperty fileSize = new SimpleStringProperty();
    private final IntegerProperty chunkCount = new SimpleIntegerProperty();
    private final StringProperty uploadedAt = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();

    public KnowledgeItem() {}

    public KnowledgeItem(String fileName, String fileType, String fileSize, int chunkCount, String uploadedAt, String status) {
        setFileName(fileName);
        setFileType(fileType);
        setFileSize(fileSize);
        setChunkCount(chunkCount);
        setUploadedAt(uploadedAt);
        setStatus(status);
    }

    public String getFileName() {
        return fileName.get();
    }

    public void setFileName(String fileName) {
        this.fileName.set(fileName);
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public String getFileType() {
        return fileType.get();
    }

    public void setFileType(String fileType) {
        this.fileType.set(fileType);
    }

    public StringProperty fileTypeProperty() {
        return fileType;
    }

    public String getFileSize() {
        return fileSize.get();
    }

    public void setFileSize(String fileSize) {
        this.fileSize.set(fileSize);
    }

    public StringProperty fileSizeProperty() {
        return fileSize;
    }

    public int getChunkCount() {
        return chunkCount.get();
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount.set(chunkCount);
    }

    public IntegerProperty chunkCountProperty() {
        return chunkCount;
    }

    public String getUploadedAt() {
        return uploadedAt.get();
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt.set(uploadedAt);
    }

    public StringProperty uploadedAtProperty() {
        return uploadedAt;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public StringProperty statusProperty() {
        return status;
    }
}
