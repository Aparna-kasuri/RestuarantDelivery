import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class RestaurantBackend {

    static final int PORT = 9090;
    static final String DB_URL = "jdbc:sqlite:restaurant.db";

    public static void main(String[] args) throws Exception {

        Class.forName("org.sqlite.JDBC");

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS Customers (" +
                    "customer_id INTEGER PRIMARY KEY, name TEXT, phone TEXT, address TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS Orders (" +
                    "order_id INTEGER PRIMARY KEY, customer_id INTEGER, order_date TEXT, status TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS DeliveryStaff (" +
                    "staff_id INTEGER PRIMARY KEY, name TEXT, phone TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS Delivery (" +
                    "delivery_id INTEGER PRIMARY KEY, order_id INTEGER, staff_id INTEGER, delivery_time TEXT, delivery_date TEXT)");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/restaurant", new ApiHandler());
        server.createContext("/", new StaticHandler("public"));
        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:" + PORT);
    }

    // ===================== STATIC FILE HANDLER =====================
    static class StaticHandler implements HttpHandler {
        private final String rootDir;

        StaticHandler(String rootDir) {
            this.rootDir = rootDir;
        }

        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            File file = new File(rootDir, path);

            if (!file.exists() || file.isDirectory()) {
                String msg = "404 - File not found: " + path;
                byte[] bytes = msg.getBytes("UTF-8");
                ex.sendResponseHeaders(404, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".ico")) contentType = "image/x-icon";
            else if (path.endsWith(".json")) contentType = "application/json";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) contentType = "image/jpeg";
            else if (path.endsWith(".svg")) contentType = "image/svg+xml";

            ex.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class ApiHandler implements HttpHandler {

        public void handle(HttpExchange ex) throws IOException {

            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if (ex.getRequestMethod().equals("OPTIONS")) {
                ex.sendResponseHeaders(204, -1);
                return;
            }

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);

            String[] parts = path.split("/");
            String endpoint = (parts.length >= 4) ? parts[3] : "";
            String idParam  = (parts.length >= 5) ? parts[4] : "";

            System.out.println("PATH: " + path + " | METHOD: " + method + " | ID: " + idParam);

            try (Connection conn = DriverManager.getConnection(DB_URL)) {

                // ===== DELETE =====
                if (method.equals("DELETE") && !idParam.isEmpty()) {
                    int id = Integer.parseInt(idParam);
                    String sql = null;
                    switch (endpoint) {
                        case "customers":  sql = "DELETE FROM Customers WHERE customer_id = ?";     break;
                        case "orders":     sql = "DELETE FROM Orders WHERE order_id = ?";            break;
                        case "staff":      sql = "DELETE FROM DeliveryStaff WHERE staff_id = ?";     break;
                        case "deliveries": sql = "DELETE FROM Delivery WHERE delivery_id = ?";       break;
                    }
                    if (sql != null) {
                        PreparedStatement ps = conn.prepareStatement(sql);
                        ps.setInt(1, id);
                        int rows = ps.executeUpdate();
                        send(ex, rows > 0 ? "Deleted successfully" : "Record not found");
                    } else {
                        send(ex, "Unknown endpoint");
                    }
                    return;
                }

                // ===== PUT (UPDATE) =====
                if (method.equals("PUT") && !idParam.isEmpty()) {
                    int id = Integer.parseInt(idParam);
                    String body = read(ex);

                    try {
                        PreparedStatement ps = null;

                        switch (endpoint) {
                            case "customers":
                                ps = conn.prepareStatement(
                                    "UPDATE Customers SET name=?, phone=?, address=? WHERE customer_id=?");
                                ps.setString(1, get(body, "name"));
                                ps.setString(2, get(body, "phone"));
                                ps.setString(3, get(body, "address"));
                                ps.setInt(4, id);
                                break;

                            case "orders":
                                ps = conn.prepareStatement(
                                    "UPDATE Orders SET customer_id=?, order_date=?, status=? WHERE order_id=?");
                                ps.setInt(1, Integer.parseInt(get(body, "customer_id")));
                                ps.setString(2, get(body, "order_date"));
                                ps.setString(3, get(body, "status"));
                                ps.setInt(4, id);
                                break;

                            case "staff":
                                ps = conn.prepareStatement(
                                    "UPDATE DeliveryStaff SET name=?, phone=? WHERE staff_id=?");
                                ps.setString(1, get(body, "name"));
                                ps.setString(2, get(body, "phone"));
                                ps.setInt(3, id);
                                break;

                            case "deliveries":
                                ps = conn.prepareStatement(
                                    "UPDATE Delivery SET order_id=?, staff_id=?, delivery_time=?, delivery_date=? WHERE delivery_id=?");
                                ps.setInt(1, Integer.parseInt(get(body, "order_id")));
                                ps.setInt(2, Integer.parseInt(get(body, "staff_id")));
                                ps.setString(3, get(body, "delivery_time"));
                                ps.setString(4, get(body, "delivery_date"));
                                ps.setInt(5, id);
                                break;
                        }

                        if (ps != null) {
                            int rows = ps.executeUpdate();
                            send(ex, rows > 0 ? "Updated successfully" : "Record not found");
                        } else {
                            send(ex, "Unknown endpoint");
                        }
                    } catch (SQLException e) {
                        send(ex, "DB Error: " + e.getMessage());
                    }
                    return;
                }

                // ===== GET / POST =====
                switch (endpoint) {

                    case "customers":
                        if (method.equals("GET")) {
                            sendJSON(ex, getAll(conn, "Customers",
                                "{\"customer_id\":%s,\"name\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\"}"));
                        } else if (method.equals("POST")) {
                            try {
                                String body = read(ex);
                                PreparedStatement ps = conn.prepareStatement("INSERT INTO Customers VALUES (?, ?, ?, ?)");
                                ps.setInt(1, Integer.parseInt(get(body, "customer_id")));
                                ps.setString(2, get(body, "name"));
                                ps.setString(3, get(body, "phone"));
                                ps.setString(4, get(body, "address"));
                                ps.executeUpdate();
                                send(ex, "Customer Added");
                            } catch (SQLException e) { send(ex, "Duplicate ID or DB Error: " + e.getMessage()); }
                        }
                        break;

                    case "orders":
                        if (method.equals("GET")) {
                            sendJSON(ex, getAll(conn, "Orders",
                                "{\"order_id\":%s,\"customer_id\":%s,\"order_date\":\"%s\",\"status\":\"%s\"}"));
                        } else if (method.equals("POST")) {
                            try {
                                String body = read(ex);
                                PreparedStatement ps = conn.prepareStatement("INSERT INTO Orders VALUES (?, ?, ?, ?)");
                                ps.setInt(1, Integer.parseInt(get(body, "order_id")));
                                ps.setInt(2, Integer.parseInt(get(body, "customer_id")));
                                ps.setString(3, get(body, "order_date"));
                                ps.setString(4, get(body, "status"));
                                ps.executeUpdate();
                                send(ex, "Order Added");
                            } catch (SQLException e) { send(ex, "Duplicate ID or DB Error: " + e.getMessage()); }
                        }
                        break;

                    case "staff":
                        if (method.equals("GET")) {
                            sendJSON(ex, getAll(conn, "DeliveryStaff",
                                "{\"staff_id\":%s,\"name\":\"%s\",\"phone\":\"%s\"}"));
                        } else if (method.equals("POST")) {
                            try {
                                String body = read(ex);
                                PreparedStatement ps = conn.prepareStatement("INSERT INTO DeliveryStaff VALUES (?, ?, ?)");
                                ps.setInt(1, Integer.parseInt(get(body, "staff_id")));
                                ps.setString(2, get(body, "name"));
                                ps.setString(3, get(body, "phone"));
                                ps.executeUpdate();
                                send(ex, "Staff Added");
                            } catch (SQLException e) { send(ex, "Duplicate ID or DB Error: " + e.getMessage()); }
                        }
                        break;

                    case "deliveries":
                        if (method.equals("GET")) {
                            sendJSON(ex, getAll(conn, "Delivery",
                                "{\"delivery_id\":%s,\"order_id\":%s,\"staff_id\":%s,\"delivery_time\":\"%s\",\"delivery_date\":\"%s\"}"));
                        } else if (method.equals("POST")) {
                            try {
                                String body = read(ex);
                                PreparedStatement ps = conn.prepareStatement("INSERT INTO Delivery VALUES (?, ?, ?, ?, ?)");
                                ps.setInt(1, Integer.parseInt(get(body, "delivery_id")));
                                ps.setInt(2, Integer.parseInt(get(body, "order_id")));
                                ps.setInt(3, Integer.parseInt(get(body, "staff_id")));
                                ps.setString(4, get(body, "delivery_time"));
                                ps.setString(5, get(body, "delivery_date"));
                                ps.executeUpdate();
                                send(ex, "Delivery Added");
                            } catch (SQLException e) { send(ex, "Duplicate ID or DB Error: " + e.getMessage()); }
                        }
                        break;

                    default:
                        String notFound = "404 - Endpoint not found: " + endpoint;
                        byte[] nfBytes = notFound.getBytes("UTF-8");
                        ex.sendResponseHeaders(404, nfBytes.length);
                        ex.getResponseBody().write(nfBytes);
                        ex.getResponseBody().close();
                }

            } catch (Exception e) {
                e.printStackTrace();
                ex.sendResponseHeaders(500, -1);
            }
        }

        private String getAll(Connection conn, String table, String format) throws SQLException {
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM " + table);
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<String> list = new ArrayList<>();
            while (rs.next()) {
                Object[] values = new Object[cols];
                for (int i = 0; i < cols; i++) {
                    Object val = rs.getObject(i + 1);
                    values[i] = (val == null) ? "" : val;
                }
                list.add(String.format(format, values));
            }
            return "[" + String.join(",", list) + "]";
        }

        private String read(HttpExchange ex) throws IOException {
            InputStream is = ex.getRequestBody();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        }

        private void send(HttpExchange ex, String msg) throws IOException {
            byte[] response = msg.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, response.length);
            OutputStream os = ex.getResponseBody();
            os.write(response);
            os.flush();
            os.close();
        }

        private void sendJSON(HttpExchange ex, String json) throws IOException {
            byte[] bytes = json.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.flush();
            os.close();
        }

        private String get(String json, String key) {
            Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}]+)\"?");
            Matcher m = p.matcher(json);
            return m.find() ? m.group(1).trim() : "";
        }
    }
}