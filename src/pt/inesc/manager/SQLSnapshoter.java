package pt.inesc.manager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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
    public List<URLVersion> shot(Integer id) throws SQLException {
        List<URLVersion> snapshot = new ArrayList<URLVersion>();
        try {
            PreparedStatement stat = conn.prepareStatement("SELECT * FROM Pages P WHERE id = (SELECT MAX(id) FROM Pages WHERE URL = P.URL and id <= ?)");
            stat.setString(1, id.toString());
            ResultSet rs = stat.executeQuery();
            // Get the ID's for snapshot
            while (rs.next()) {
                URLVersion entry = new URLVersion(rs.getString("url"), rs.getString("id"));
                snapshot.add(entry);
                stat = conn.prepareStatement("delete FROM Pages WHERE id < ? and url = ?");
                stat.setString(1, entry.version);
                stat.setString(2, entry.url);
                stat.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return snapshot;
    }



    @Override
    public void load(List<URLVersion> snapshot) throws SQLException {
        // Delete all versions more recent than snapshot
        StringBuilder sb = new StringBuilder();
        sb.append("delete from Pages where url not in (");
        for (URLVersion v : snapshot) {
            sb.append("'");
            sb.append(v.url);
            sb.append("',");
            PreparedStatement stat = conn.prepareStatement("delete FROM Pages WHERE id > ? and url = ?");
            stat.setString(1, v.version);
            stat.setString(2, v.url);
            stat.execute();

        }
        String query = sb.toString();
        query = query.substring(0, query.length() - 1);
        query = query + ")";

        // Delete all URL not in snapshot
        System.out.println(query);
        Statement stat = conn.createStatement();
        stat.execute(query);
    }
}
