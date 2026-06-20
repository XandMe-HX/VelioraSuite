package id.velioragardens.veliorasuite.module.guide;

public enum GuideType {
    GUIDE("guide", "vguide"),
    RULES("rules", "vrules"),
    PRODUCT("product", "vproduct");

    private final String key;
    private final String shortCommand;

    GuideType(String key, String shortCommand) {
        this.key = key;
        this.shortCommand = shortCommand;
    }

    public String getKey() {
        return key;
    }

    public String getShortCommand() {
        return shortCommand;
    }
}
