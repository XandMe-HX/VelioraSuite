package id.velioragardens.veliorasuite.module.guide;

import java.util.List;

public final class GuidePage {

    private final int number;
    private final String title;
    private final List<String> lines;

    public GuidePage(int number, String title, List<String> lines) {
        this.number = number;
        this.title = title;
        this.lines = lines;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLines() {
        return lines;
    }
}
