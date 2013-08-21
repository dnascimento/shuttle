package pt.inesc.proxy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        StringBuilder builder = new StringBuilder();
        for (String string : log) {
            builder.append(string);
            builder.append("================================\n");
        }
        Charset charset = Charset.forName("US-ASCII");
        Path path = Paths.get("requests/" + type + id + ".txt");
        BufferedWriter writer;
        try {
            writer = Files.newBufferedWriter(path, charset);
            writer.write(builder.toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            logger.error(e.getStackTrace());
        }
    }
}
