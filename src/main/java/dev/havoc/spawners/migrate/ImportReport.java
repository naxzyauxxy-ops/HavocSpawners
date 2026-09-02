package dev.havoc.spawners.migrate;

import java.util.ArrayList;
import java.util.List;

/** Summary of a legacy import run. */
public final class ImportReport {

    private final String source;
    private int read;
    private int imported;
    private int skipped;
    private int failed;
    private long itemsMoved;
    private final List<String> warnings = new ArrayList<>();

    public ImportReport(String source) {
        this.source = source;
    }

    public void countRead() {
        read++;
    }

    public void countImported(long items) {
        imported++;
        itemsMoved += items;
    }

    public void countSkipped() {
        skipped++;
    }

    public void countFailed(String reason) {
        failed++;
        if (warnings.size() < 25) {
            warnings.add(reason);
        }
    }

    public void warn(String reason) {
        if (warnings.size() < 25) {
            warnings.add(reason);
        }
    }

    public String source() {
        return source;
    }

    public int read() {
        return read;
    }

    public int imported() {
        return imported;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public long itemsMoved() {
        return itemsMoved;
    }

    public List<String> warnings() {
        return warnings;
    }
}
