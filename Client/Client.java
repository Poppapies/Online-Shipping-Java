 
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private JFrame frame;
    private JTextField idField, senderField, destField, weightField;
    private JComboBox<String> statusCombo;
    private JLabel statusBar;
    private DefaultTableModel tableModel;
    private JTable table;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().createAndShow());
    }

    private void createAndShow() {
        frame = new JFrame("Distributed Logistics Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(2, 6, 8, 8));
        idField = new JTextField();
        senderField = new JTextField();
        destField = new JTextField();
        weightField = new JTextField();
        statusCombo = new JComboBox<>(new String[]{"Pending", "In Transit", "Delivered"});

        form.add(new JLabel("Parcel ID")); form.add(new JLabel("Sender")); form.add(new JLabel("Destination")); form.add(new JLabel("Weight")); form.add(new JLabel("Status")); form.add(new JLabel());
        form.add(idField); form.add(senderField); form.add(destField); form.add(weightField); form.add(statusCombo);

        JButton registerBtn = new JButton("Register Parcel");
        JButton updateBtn = new JButton("Update Status");
        JButton trackBtn = new JButton("Track Parcel");
        JButton showAllBtn = new JButton("Show All Shipments");

        registerBtn.addActionListener(e -> doRegister());
        updateBtn.addActionListener(e -> doUpdate());
        trackBtn.addActionListener(e -> doTrack());
        showAllBtn.addActionListener(e -> doShowAll());

        JPanel buttons = new JPanel();
        buttons.add(registerBtn); buttons.add(updateBtn); buttons.add(trackBtn); buttons.add(showAllBtn);

        tableModel = new DefaultTableModel(new String[]{"ID","Sender","Destination","Weight","Status"}, 0);
        table = new JTable(tableModel);
        JScrollPane tablePane = new JScrollPane(table);

        statusBar = new JLabel("Not connected");

        // put form and buttons together at the top so buttons are visible
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(form, BorderLayout.NORTH);
        topPanel.add(buttons, BorderLayout.SOUTH);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(tablePane, BorderLayout.CENTER);
        frame.add(statusBar, BorderLayout.PAGE_END);

        frame.setVisible(true);
    }

    private void setStatus(String s) {
        SwingUtilities.invokeLater(() -> statusBar.setText(s));
    }

    private void doRegister() {
        String id = idField.getText().trim();
        String sender = senderField.getText().trim();
        String dest = destField.getText().trim();
        String weight = weightField.getText().trim();
        String status = (String) statusCombo.getSelectedItem();
        if (id.isEmpty() || sender.isEmpty() || dest.isEmpty() || weight.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "All fields required for register");
            return;
        }

        String cmd = String.join("|", "REGISTER", id, sender, dest, weight, status);
        sendCommand(cmd, resp -> JOptionPane.showMessageDialog(frame, resp));
    }

    private void doUpdate() {
        String id = idField.getText().trim();
        String status = (String) statusCombo.getSelectedItem();
        if (id.isEmpty()) { JOptionPane.showMessageDialog(frame, "Parcel ID required"); return; }
        String cmd = String.join("|", "UPDATE", id, status);
        sendCommand(cmd, resp -> JOptionPane.showMessageDialog(frame, resp));
    }

    private void doTrack() {
        String id = idField.getText().trim();
        if (id.isEmpty()) { JOptionPane.showMessageDialog(frame, "Parcel ID required"); return; }
        String cmd = String.join("|", "TRACK", id);
        sendCommand(cmd, lines -> {
            if (lines.size() >= 1) {
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    for (String line : lines) {
                        if (line.isEmpty()) continue;
                        if (line.equals("OK") || line.equals("END")) continue;
                        String[] f = line.split("\\|", -1);
                        tableModel.addRow(new Object[]{f[0], f[1], f[2], f[3], f[4]});
                    }
                    table.repaint();
                    table.revalidate();
                });
            }
        });
    }

    private void doShowAll() {
        String cmd = "SHOWALL";
        sendCommand(cmd, lines -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                for (String line : lines) {
                    if (line.isEmpty()) continue;
                    if (line.equals("OK") || line.equals("END")) continue;
                    String[] f = line.split("\\|", -1);
                    tableModel.addRow(new Object[]{f[0], f[1], f[2], f[3], f[4]});
                }
            });
        });
    }

    private interface ResponseHandler { void handle(java.util.List<String> lines); }

    private void sendCommand(String command, ResponseHandler handler) {
        setStatus("Connecting...");
        new SwingWorker<Void, Void>() {
            List<String> lines = new ArrayList<>();

            @Override
            protected Void doInBackground() {
                try (Socket s = new Socket(HOST, PORT);
                     BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

                    out.write(command + "\n");
                    out.flush();

                    String ln;
                    while ((ln = in.readLine()) != null) {
                        if (ln.equals("END")) break;
                        lines.add(ln);
                    }
                } catch (IOException e) {
                    lines.clear();
                    lines.add("ERROR|" + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                if (!lines.isEmpty()) {
                    if (lines.get(0).startsWith("ERROR|")) {
                        setStatus("Error: " + lines.get(0).substring(6));
                        JOptionPane.showMessageDialog(frame, lines.get(0).substring(6));
                    } else if (lines.get(0).startsWith("OK|")) {
                        setStatus(lines.get(0).substring(3));
                        handler.handle(lines);
                    } else {
                        setStatus("OK");
                        handler.handle(lines);
                    }
                } else {
                    setStatus("No response");
                }
            }
        }.execute();
    }
}
