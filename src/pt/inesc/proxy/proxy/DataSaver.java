package pt.inesc.proxy.proxy;

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

    public Collection<HTTPackage> log;
    public int id;
    private static Logger logger = LogManager.getLogger("DataSaver");
    private String type;


    public DataSaver(LinkedList<HTTPackage> requests, int id) {
        log = requests;
        this.id = id;
        type = "req";

    }


    public DataSaver(Map<Integer, HTTPackage> responsesToSave, int id) {
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
            String string;
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            for (HTTPackage pack : log) {
                string = pack.header;
                string = string.replace("Connection: close", "");
                out.write(string);
                if (pack.body != null) {
                    out.write(pack.body);
                }
                out.write("\n================================\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
