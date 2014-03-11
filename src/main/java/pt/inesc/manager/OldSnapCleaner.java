package pt.inesc.manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;

import pt.inesc.manager.redo.RedoWorker;

public class OldSnapCleaner {

    int minimumId;
    BufferedReader reqFile;
    String type;
    private final String SEPARATOR = "===";

    public void clean(int minimumId) throws IOException {
        cleanType(minimumId, "req");
        cleanType(minimumId, "res");
    }

    private void cleanType(int minimumId, String type) throws IOException {
        LinkedList<File> files = RedoWorker.getFileList(type, 0, minimumId);
        // Delete all files with ID < minimumId
        for (File f : files) {
            f.delete();
        }

        // Select file with lowest ID
        files = RedoWorker.getFileList("req", 0, -1);
        File lowest = files.getFirst();
        reqFile = new BufferedReader(new FileReader(lowest));

        // Cut all requests with ID lower
        Integer id;
        while ((id = getNextId()) < minimumId) {
            getBody();
        }

        // Copy the rest to new file
        File temp = new File(RedoWorker.DIRECTOY + "temp.txt");
        BufferedWriter tempFile = new BufferedWriter(new FileWriter(temp));

        tempFile.write(id.toString() + "\n");
        int len;
        char[] cbuf = new char[10000];
        while ((len = reqFile.read(cbuf)) > 0) {
            tempFile.write(cbuf, 0, len);
        }
        tempFile.close();
        reqFile.close();
        lowest.delete();
        temp.renameTo(lowest);
    }

    private int getNextId() throws IOException {
        String line = null;
        while ((line = reqFile.readLine()) != null) {
            return Integer.parseInt(line);
        }
        throw new EOFException();
    }


    private String getBody() throws IOException {
        String line = null;
        StringBuilder sb = new StringBuilder();
        while ((line = reqFile.readLine()) != null) {
            sb.append(line);
            if (line.equals(SEPARATOR)) {
                return sb.toString();
            }
        }
        // Last request, file is done
        reqFile.close();
        reqFile = null;
        return sb.toString();
    }


}
