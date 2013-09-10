package pt.inesc.manager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;

public class SQLSnapshoter
        implements SnapshotAPI {
    private Connection conn;
    private String url = "jdbc:mysql://localhost/wiki";
    private String user = "root";
    private String password = "";

    public SQLSnapshoter() {
        try {
            conn = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }




    @Override
    public LinkedList<String> shot(Integer id) throws SQLException {
        LinkedList<String> snapshot = new LinkedList<String>();
        try {

            // Simulate row by row snapshot
            // Set new entry on log
            Statement stat1 = conn.createStatement();
            ResultSet rtimes = stat1.executeQuery("select max(event_time) from mysql.general_log;");
            rtimes.next();
            String lastLogEntry = rtimes.getString(0);

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
                    stat = conn.prepareStatement("insert into Pages(url,author,title,date,content,id,snap) values(?,?,?,?,?,?,?)");
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


            // Read the log
            PreparedStatement stat3 = conn.prepareStatement("select * from mysql.general_log where event_time >= ?");
            stat3.setString(1, lastLogEntry);
            ResultSet pendentLog = stat3.executeQuery();

            while (pendentLog.next()) {
                String command = pendentLog.getString("argument");
                System.out.println(command);
                snapshot.add(command);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return snapshot;
    }

    @Override
    public void load(LinkedList<String> pendentOperations, int id) throws SQLException {
        // Delete all versions more recent than snapshot
        PreparedStatement stat = conn.prepareStatement("delete FROM Pages WHERE snap <> ?");
        stat.setInt(1, id);
        stat.execute();

        String op;
        while ((op = pendentOperations.getFirst()) != null) {
            Statement pendentOp = conn.createStatement();
            pendentOp.execute(op);
        }
    }
}
