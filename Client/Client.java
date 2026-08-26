 
import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 5000;

    private JFrame formFrame;
    private JFrame tableFrame;
    JTextField idField = new JTextField(15);
    JTextField senderField = new JTextField(15);
    JTextField destField = new JTextField(15);
    JTextField weightField = new JTextField(15);
    JTable table = new JTable();
    DefaultTableModel tableModel = new DefaultTableModel();
    JComboBox<String> statusCombo;
    JLabel statusBar;


private void CreateForm() {

    formFrame = new JFrame("Client Form");
    formFrame.setSize(800, 800);
    formFrame.setLocationRelativeTo(null);
    formFrame.setResizable(false);
    formFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    formFrame.setLayout(new BorderLayout());

    JPanel form = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    statusCombo = new JComboBox<>(new String[]{"Pending", "In Transit", "Delivered"});


    gbc.gridx = 0;
    gbc.gridy = 0;
    form.add(new JLabel("Parcel ID:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    form.add(idField, gbc);


    gbc.gridx = 0;
    gbc.gridy = 1;
    form.add(new JLabel("Sender:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 1;
    form.add(senderField, gbc);


    gbc.gridx = 0;
    gbc.gridy = 2;
    form.add(new JLabel("Destination:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 2;
    form.add(destField, gbc);


    gbc.gridx = 0;
    gbc.gridy = 3;
    form.add(new JLabel("Weight:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 3;
    form.add(weightField, gbc);

    gbc.gridx = 0;
    gbc.gridy = 4;
    form.add(new JLabel("Status:"), gbc);
    gbc.gridx = 1;
    gbc.gridy = 4;
    form.add(statusCombo, gbc);


    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));

    JButton registerButton = new JButton("Register");
    JButton updateButton = new JButton("Update");
    JButton trackButton = new JButton("Track");
    JButton showAllButton = new JButton("Show Shipments");

    registerButton.addActionListener(e -> doRegister());
    updateButton.addActionListener(e -> doUpdate());
    trackButton.addActionListener(e -> doTrack());
    showAllButton.addActionListener(e -> doShowAll());

    buttons.add(registerButton);
    buttons.add(updateButton);
    buttons.add(trackButton);
    buttons.add(showAllButton);
    statusBar = new JLabel("Not connected");
    statusBar.setForeground(Color.RED);

    JPanel southReagionPanel = new JPanel(new BorderLayout());
    southReagionPanel.add(buttons, BorderLayout.CENTER);
    southReagionPanel.add(statusBar, BorderLayout.PAGE_END);

    formFrame.add(form, BorderLayout.CENTER);
    formFrame.add(southReagionPanel, BorderLayout.SOUTH);

    formFrame.setVisible(true);

    tableFrame = new JFrame("Table Frame");
    tableFrame.setSize(400,400);
    tableFrame.setResizable(false);
    tableFrame.setLocationRelativeTo(formFrame);
    tableFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

    tableModel = new DefaultTableModel(new String[]{"ID","Sender","Destination","Weight","Status"}, 0);
    table = new JTable(tableModel);
    JScrollPane tablePane = new JScrollPane(table);
    tableFrame.add(tablePane, BorderLayout.CENTER);
}

    private void setStatus(String s) {
        SwingUtilities.invokeLater(() -> {
            statusBar.setText(s);
            if (s == "Connecting..." || s == "OK")
                statusBar.setForeground(Color.GREEN);

            else if (s == "Error" || s == "No response")
                statusBar.setForeground(Color.RED);

        });
    }

    private void doRegister() {
        String id = idField.getText().trim();
        String sender = senderField.getText().trim();
        String dest = destField.getText().trim();
        String weight = weightField.getText().trim();
        String status = (String) statusCombo.getSelectedItem();

        if (id.isEmpty() || sender.isEmpty() || dest.isEmpty() || weight.isEmpty()) {
            JOptionPane.showMessageDialog(formFrame, "All fields required");
            return;
        }

        String cmd = String.join("|", "REGISTER", id, sender, dest, weight, status);
        sendCommand(cmd, resp -> JOptionPane.showMessageDialog(formFrame, resp));
    }

    private void doUpdate() {

        String id = idField.getText().trim();
        String status = (String) statusCombo.getSelectedItem();
        if (id.isEmpty()) { JOptionPane.showMessageDialog(formFrame, "Parcel ID required"); return; }
        String cmd = String.join("|", "UPDATE", id, status);
        sendCommand(cmd, resp -> JOptionPane.showMessageDialog(formFrame, resp));

    }

    private void doTrack() {
        String id = idField.getText().trim();
        if (id.isEmpty()) { JOptionPane.showMessageDialog(formFrame, "Parcel ID required"); return; }
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
                    tableFrame.setVisible(true);
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
                        JOptionPane.showMessageDialog(formFrame, lines.get(0).substring(6));
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
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Client().CreateForm());
    }
}
