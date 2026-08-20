 
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final int PORT = 5000;

    public static void main(String[] args) {
        System.out.println("Starting Logistics Server...");
        DBHelper.initDB();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(new ClientHandler(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

                String line = in.readLine();
                if (line == null) return;
                String[] parts = line.split("\\|", -1);
                String cmd = parts[0];

                switch (cmd.toUpperCase()) {
                    case "REGISTER":
                        handleRegister(parts, out);
                        break;
                    case "UPDATE":
                        handleUpdate(parts, out);
                        break;
                    case "TRACK":
                        handleTrack(parts, out);
                        break;
                    case "SHOWALL":
                        handleShowAll(out);
                        break;
                    default:
                        writeError(out, "Unknown command");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void handleRegister(String[] parts, BufferedWriter out) throws IOException {
            if (parts.length < 6) { writeError(out, "REGISTER requires 5 params"); return; }
            String id = parts[1];
            String sender = parts[2];
            String dest = parts[3];
            double weight;
            try { weight = Double.parseDouble(parts[4]); } catch (NumberFormatException e) { writeError(out, "Invalid weight"); return; }
            String status = parts[5];

            boolean ok = DBHelper.registerParcel(new Parcel(id, sender, dest, weight, status));
            if (ok) writeOk(out, "Registered"); else writeError(out, "Register failed: " + DBHelper.getLastError());
        }

        private void handleUpdate(String[] parts, BufferedWriter out) throws IOException {
            if (parts.length < 3) { writeError(out, "UPDATE requires id and status"); return; }
            String id = parts[1];
            String status = parts[2];
            boolean ok = DBHelper.updateStatus(id, status);
            if (ok) writeOk(out, "Updated"); else writeError(out, "Update failed: " + DBHelper.getLastError());
        }

        private void handleTrack(String[] parts, BufferedWriter out) throws IOException {
            if (parts.length < 2) { writeError(out, "TRACK requires id"); return; }
            String id = parts[1];
            Parcel p = DBHelper.trackParcel(id);
            if (p == null) {
                String err = DBHelper.getLastError();
                if (err == null || err.isEmpty()) err = "Not found";
                writeError(out, err);
                return;
            }
            out.write("OK\n");
            out.write(p.toString() + "\n");
            out.write("END\n");
            out.flush();
        }

        private void handleShowAll(BufferedWriter out) throws IOException {
            try {
                List<Parcel> list = DBHelper.getAllParcels();
                out.write("OK\n");
                for (Parcel p : list) {
                    out.write(p.toString() + "\n");
                }
                out.write("END\n");
                out.flush();
            } catch (SQLException e) {
                e.printStackTrace();
                writeError(out, e.getMessage());
            }
        }

        private void writeOk(BufferedWriter out, String msg) throws IOException {
            out.write("OK|" + msg + "\n");
            out.write("END\n");
            out.flush();
        }

        private void writeError(BufferedWriter out, String msg) throws IOException {
            out.write("ERROR|" + msg + "\n");
            out.write("END\n");
            out.flush();
        }
    }
}

class DBHelper {
    private static final String URL = "jdbc:sqlite:shipments.db";
    private static volatile String lastError = "";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            lastError = "JDBC driver not found: " + e.getMessage();
            System.err.println("JDBC driver not found. Put sqlite-jdbc.jar on the classpath.");
            throw new SQLException("No JDBC driver found: please add sqlite-jdbc.jar to classpath", e);
        }
        return DriverManager.getConnection(URL);
    }

    public static Connection getReadOnlyConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            lastError = "JDBC driver not found: " + e.getMessage();
            System.err.println("JDBC driver not found. Put sqlite-jdbc.jar on the classpath.");
            throw new SQLException("No JDBC driver found: please add sqlite-jdbc.jar to classpath", e);
        }
        // use mode=ro to open DB read-only to avoid write conflicts
        return DriverManager.getConnection(URL + "?mode=ro");
    }

    public static void initDB() {
        String create = "CREATE TABLE IF NOT EXISTS parcels (" +
                "id TEXT PRIMARY KEY, " +
                "sender TEXT NOT NULL, " +
                "destination TEXT NOT NULL, " +
                "weight REAL NOT NULL, " +
                "status TEXT NOT NULL)";

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(create);

            // insert sample data if empty
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM parcels")) {
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getInt(1) == 0) {
                    insertSample(conn);
                }
            }
        } catch (SQLException e) {
            lastError = e.getMessage();
            System.err.println("DB init error: " + e.getMessage());
        }
    }

    private static void insertSample(Connection conn) throws SQLException {
        String insert = "INSERT INTO parcels(id,sender,destination,weight,status) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(insert)) {
            conn.setAutoCommit(false);
            for (int i = 1; i <= 10; i++) {
                ps.setString(1, "P" + String.format("%03d", i));
                ps.setString(2, "Sender" + i);
                ps.setString(3, "City" + ((i % 5) + 1));
                ps.setDouble(4, 1.0 + i);
                ps.setString(5, i % 3 == 0 ? "Delivered" : (i % 3 == 1 ? "Pending" : "In Transit"));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    public static boolean registerParcel(Parcel p) {
        String sql = "INSERT INTO parcels(id,sender,destination,weight,status) VALUES(?,?,?,?,?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getId());
            ps.setString(2, p.getSender());
            ps.setString(3, p.getDestination());
            ps.setDouble(4, p.getWeight());
            ps.setString(5, p.getStatus());
            ps.executeUpdate();
            lastError = "";
            return true;
        } catch (SQLException e) {
            lastError = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    public static String getLastError() { return lastError; }

    public static boolean updateStatus(String id, String status) {
        String sql = "UPDATE parcels SET status = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, id);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            lastError = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    public static Parcel trackParcel(String id) {
        lastError = "";
        String sql = "SELECT id,sender,destination,weight,status FROM parcels WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Parcel(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getString(5));
                }
            }
        } catch (SQLException e) {
            lastError = e.getMessage();
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public static List<Parcel> getAllParcels() throws SQLException {
        List<Parcel> list = new ArrayList<>();
        String sql = "SELECT id,sender,destination,weight,status FROM parcels ORDER BY id";
        // try read-only first to avoid write-related conflicts
        try (Connection conn = getReadOnlyConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Parcel(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getString(5)));
            }
            return list;
        } catch (SQLException firstEx) {
            // fallback to normal connection and retry once
            try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Parcel(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getString(5)));
                }
                return list;
            } catch (SQLException secondEx) {
                // propagate the original error message for clarity
                throw firstEx;
            }
        }
    }
}

class Parcel {
    private String id;
    private String sender;
    private String destination;
    private double weight;
    private String status;

    public Parcel(String id, String sender, String destination, double weight, String status) {
        this.id = id;
        this.sender = sender;
        this.destination = destination;
        this.weight = weight;
        this.status = status;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getDestination() { return destination; }
    public double getWeight() { return weight; }
    public String getStatus() { return status; }

    @Override
    public String toString() {
        return id + "|" + sender + "|" + destination + "|" + weight + "|" + status;
    }
}

