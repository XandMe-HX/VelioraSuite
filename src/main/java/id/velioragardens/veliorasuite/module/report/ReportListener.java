package id.velioragardens.veliorasuite.module.report;

import org.bukkit.event.Listener;

public final class ReportListener implements Listener {

    private final ReportManager reportManager;

    public ReportListener(ReportManager reportManager) {
        this.reportManager = reportManager;
    }

    public ReportManager getReportManager() {
        return reportManager;
    }
}
