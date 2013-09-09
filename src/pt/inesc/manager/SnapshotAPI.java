package pt.inesc.manager;

import java.util.List;

public interface SnapshotAPI {

    public List<URLVersion> shot(Integer id) throws Exception;

    public void load(List<URLVersion> snapshot) throws Exception;
}
