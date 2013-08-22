package pt.inesc.proxy.clientSide;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataSaver extends
        Thread {

    public Collection<String> log;
    public int id;
    private static Logger logger = LogManager.getLogger("DataSaver");
    private String type;


    public DataSaver(LinkedList<String> requests, int id) {
        log = requests;
        this.id = id;
        type = "req";
    }


    public DataSaver(Map<Integer, String> responsesToSave, int id) {
        log = responsesToSave.values();
        this.id = id;
        type = "res";
    }


    @Override
    public void run() {
        File file = new File("requests/" + type + id + ".txt");
        if (file.exists()) {
            logger.error("ERRO!!!! File already exists");
            return;
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            for (String string : log) {
                string = string.replace("Connection: close", "");
                out.write(string);
                out.write("\n================================\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
