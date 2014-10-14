package pt.inesc.manager.graph;

import java.util.List;

public class ExecListWrapper {

    public final List<List<Long>> list;
    public final long latestRequest;

    public ExecListWrapper(List<List<Long>> list, long latestRequest) {
        this.list = list;
        this.latestRequest = latestRequest;
    }
}
