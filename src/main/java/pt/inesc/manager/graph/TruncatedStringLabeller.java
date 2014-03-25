package pt.inesc.manager.graph;

import org.apache.commons.collections15.Transformer;

public class TruncatedStringLabeller
        implements Transformer<String, String> {
    private static final int SIZE = 10;

    public String transform(String label) {
        int l = label.length();
        int start = Math.max(l - SIZE, 0);
        return label.substring(start, l);
    }
}
