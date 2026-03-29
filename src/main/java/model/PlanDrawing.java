package model;

public class PlanDrawing {
    private String fileName;
    private String storedPath;
    private String fileType;

    public PlanDrawing() {
    }

    public PlanDrawing(String fileName, String storedPath, String fileType) {
        this.fileName = fileName;
        this.storedPath = storedPath;
        this.fileType = fileType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
