package pt.inesc.manager.cluster;

public interface InfrastructureControllerInterface {

    /**
     * Add "n" new replay nodes and return their IP:PORT
     * 
     * @return
     */
    public String[] addReplayNodes(int n);


    /**
     * Add "n" new database nodes and return their IP:PORT
     * 
     * @return
     */
    public String[] addDatabaseNodes(int n);

    /**
     * Add "n" new computing nodes and return their IP:PORT
     * 
     * @return
     */
    public String[] addComputingNodes(int n);


}
