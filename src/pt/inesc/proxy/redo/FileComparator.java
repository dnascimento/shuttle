package pt.inesc.proxy.redo;

import java.io.File;
import java.util.Comparator;

public class FileComparator
        implements Comparator<File> {

    @Override
    public int compare(File o1, File o2) {
        int id1 = Integer.parseInt(o1.getName().replaceAll("\\D+", ""));
        int id2 = Integer.parseInt(o2.getName().replaceAll("\\D+", ""));
        return id1 - id2;
    }

}
