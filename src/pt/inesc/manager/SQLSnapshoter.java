package pt.inesc.manager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SQLSnapshoter
        implements SnapshotAPI {
    private Connection conn;
    private final String url = "jdbc:mysql://localhost/wiki";
    private final String user = "root";
    private final String password = "";

    public SQLSnapshoter() {
        try {
            conn = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    public LinkedList<String> shot(Integer id) throws SQLException {
        LinkedList<String> snapshot = new LinkedList<String>();
        Statement clean = conn.createStatement();
        clean.execute("truncate Pages_temp");

        try {
            // Simulate row by row snapshot
            // Set new entry on log
            Statement stat1 = conn.createStatement();
            ResultSet rtimes = stat1.executeQuery("select max(event_time) from mysql.general_log;");
            rtimes.next();
            String lastLogEntry = rtimes.getString(1);

            // Get all URL
            Statement query = conn.createStatement();
            ResultSet rs = query.executeQuery("Select url from Pages");

            PreparedStatement stat;

            // Slect and copy each data
            while (rs.next()) {
                String url = rs.getString("url");
                stat = conn.prepareStatement("SELECT * FROM Pages P WHERE url=?");
                stat.setString(1, url);
                ResultSet urls = stat.executeQuery();

                while (urls.next()) {
                    stat = conn.prepareStatement("insert into Pages_temp(url,author,title,date,content,id,snap) values(?,?,?,?,?,?,?)");
                    stat.setString(1, urls.getString("url"));
                    stat.setString(2, urls.getString("author"));
                    stat.setString(3, urls.getString("title"));
                    stat.setString(4, urls.getString("date"));
                    stat.setString(5, urls.getString("content"));
                    stat.setString(6, urls.getString("id"));
                    stat.setString(7, id.toString());
                    stat.execute();
                    // Delay the copy to simulate long file delay
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
            rs.close();

            System.out.println("waiting for you");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("lets go");

            // Get the biggest ID of snap
            stat1 = conn.createStatement();
            rtimes = stat1.executeQuery("select max(id) from Pages_temp;");
            rtimes.next();
            int biggestID = rtimes.getInt(1);

            // TODO Wait for biggest ID of snap responsed


            // Get Log
            PreparedStatement stat3 = conn.prepareStatement("select * from mysql.general_log where event_time >= ?");
            stat3.setString(1, lastLogEntry);
            ResultSet pendentLog = stat3.executeQuery();


            // Program is snaped, log is copy

            // Apply log only on ID lowers than biggest ID
            while (pendentLog.next()) {
                int transactionID = -1;

                String op = pendentLog.getString("argument");
                // Remove all queries due to data copy
                if (!op.startsWith("insert into Pages_temp")
                        && (op.startsWith("insert") || op.startsWith("update"))) {

                    // Valid Operation

                    if (op.startsWith("update")) {
                        // Check-id: UPDATE
                        Pattern p = Pattern.compile("(.*)id=(\\d*)(.*)");
                        Matcher matcher = p.matcher(op);
                        if (matcher.matches()) {
                            System.out.println(matcher.group(1));
                            transactionID = Integer.parseInt(matcher.group(1));
                        }
                    }

                    // CheckID: insert
                    if (op.startsWith("insert")) {
                        // Split fields and values
                        String[] parts = op.split("\\)");


                        // parse insert fields to get the ID entry
                        String[] fields = parts[0].split("\\(")[1].split(",");
                        int i;
                        for (i = 0; i < fields.length; i++) {
                            if (fields[i].equals("id")) {
                                break;
                            }
                        }

                        String[] values = parts[1].split("\\(")[1].split(",");
                        transactionID = Integer.parseInt(values[i].replaceAll("\'", ""));
                    }

                    assert (transactionID != -1);

                    if (transactionID <= biggestID) {
                        // Change op to apply on copy
                        op = op.replaceAll("Pages", "Pages_temp");
                        Statement pendentOp = conn.createStatement();
                        System.out.println(op);
                        pendentOp.execute(op);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return snapshot;
    }

    public void load(LinkedList<String> pendentOperations, int id) throws SQLException {
        // Delete all versions more recent than snapshot
        Statement stat = conn.createStatement();
        stat.execute("truncate Pages");

        // Copy the data from temp to actual
        Statement stat2 = conn.createStatement();
        stat2.execute("INSERT INTO Pages (url, author, title, date, content, id,snap) SELECT url, author, title, date, content, id, snap FROM Pages_temp");
    }
}
