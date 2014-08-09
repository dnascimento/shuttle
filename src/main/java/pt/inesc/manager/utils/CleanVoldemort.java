package pt.inesc.manager.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import pt.inesc.manager.Manager;
import voldemort.client.ClientConfig;
import voldemort.client.protocol.admin.AdminClient;
import voldemort.client.protocol.admin.AdminClientConfig;
import voldemort.cluster.Node;

public class CleanVoldemort {
    private static final Logger log = LogManager.getLogger(Manager.class.getName());

    static int VOLDEMORT_PORT = 6666;

    public static void clean(String... hosts) {
        for (String host : hosts) {
            String voldemortUrl = "tcp://" + host + ":" + VOLDEMORT_PORT;
            List<String> voldemortStores;

            voldemortStores = new ArrayList<String>(Arrays.asList("test",
                                                                  "questionStore",
                                                                  "answerStore",
                                                                  "commentStore",
                                                                  "index"));

            AdminClient adminClient = new AdminClient(voldemortUrl, new AdminClientConfig(), new ClientConfig());

            List<Integer> nodeIds = new ArrayList<Integer>();
            for (Node node : adminClient.getAdminClientCluster().getNodes()) {
                nodeIds.add(node.getId());
            }
            for (String storeName : voldemortStores) {
                for (Integer currentNodeId : nodeIds) {
                    log.info("Truncating " + storeName + " on node " + currentNodeId);
                    adminClient.storeMntOps.truncate(currentNodeId, storeName);
                }
            }
        }
    }

}
