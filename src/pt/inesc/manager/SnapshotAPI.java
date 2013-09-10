package pt.inesc.manager;

import java.util.LinkedList;

public interface SnapshotAPI {

    public LinkedList<String> shot(Integer id) throws Exception;

    public void load(LinkedList<String> pendentOperations, int id) throws Exception;
}
