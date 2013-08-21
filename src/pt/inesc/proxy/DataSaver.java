package pt.inesc.proxy;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DataSaver extends
        Thread {

    public List<String> log;
    public int id;
    private static Logger logger = LogManager.getLogger("DataSaver");
    private String type;


    public DataSaver(String type, List<String> log, int id) {
        super();
        this.log = log;
        this.id = id;
        this.type = type;
    }


    @Override
    public void run() {
        try {
            sleep(5000);
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
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
                out.write("================================\n");
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        }
    }
}
