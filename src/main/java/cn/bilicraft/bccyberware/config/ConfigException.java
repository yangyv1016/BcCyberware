package cn.bilicraft.bccyberware.config;

public final class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;
    private final String file;
    private final String path;

    public ConfigException(String file, String path, String message) {
        super(message);
        this.file = file;
        this.path = path;
    }

    public ConfigException(String file, String path, String message, Throwable cause) {
        super(message, cause);
        this.file = file;
        this.path = path;
    }

    public String file() {
        return file;
    }

    public String path() {
        return path;
    }

    public String detailedMessage() {
        return "文件：" + file + System.lineSeparator()
                + "路径：" + path + System.lineSeparator()
                + "原因：" + getMessage();
    }
}
