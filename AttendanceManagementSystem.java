import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.table.*;

/**
 * Attendance Management System (Java Swing + SQLite)
 *
 * Tab 1 - Students   : register / update / delete students
 * Tab 2 - Take Attendance : pick a date, mark every student, save
 * Tab 3 - Records & Reports : search, edit, delete, summary, export CSV
 *
 * Data is stored in the file "attendance.db" (created in the folder you run the program from).
 */
public class AttendanceManagementSystem extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final String DB_URL = "jdbc:sqlite:attendance.db";
    private static final String[] STATUSES = {"Present", "Absent", "Late"};

    private static final Color PRIMARY = new Color(41, 128, 185);
    private static final Color BACKGROUND = new Color(236, 240, 241);
    private static final Color BORDER = new Color(189, 195, 199);
    private static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);

    // ---- Students tab ----
    private JTextField txtStudentId, txtStudentName;
    private JTable studentTable;
    private DefaultTableModel studentModel;

    // ---- Take attendance tab ----
    private JTextField txtSheetDate;
    private JTable sheetTable;
    private DefaultTableModel sheetModel;
    private JLabel lblSheetInfo;

    // ---- Records tab ----
    private JTextField txtFilterId, txtFromDate, txtToDate;
    private JComboBox<String> cmbNewStatus;
    private JTable recordTable;
    private DefaultTableModel recordModel;
    private JLabel lblSummary;

    public AttendanceManagementSystem() {
        createDatabaseAndTables();
        setupUI();
        loadStudents();
        loadSheet();
        loadRecords();
    }

    // =====================================================================
    //  UI
    // =====================================================================
    private void setupUI() {
        setTitle("Attendance Management System");
        setSize(1000, 700);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY);
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel title = new JLabel("Attendance Management System", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JLabel date = new JLabel(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")),
                JLabel.CENTER);
        date.setFont(FONT);
        date.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);
        header.add(date, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(FONT_BOLD);
        tabs.addTab("1. Students", buildStudentsTab());
        tabs.addTab("2. Take Attendance", buildAttendanceTab());
        tabs.addTab("3. Records & Reports", buildRecordsTab());
        tabs.addChangeListener(e -> {
            // refresh data whenever user switches tab
            int i = tabs.getSelectedIndex();
            if (i == 1) {
                loadSheet();
            } else if (i == 2) {
                loadRecords();
            }
        });

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    // ---------------- Tab 1 : Students ----------------
    private JPanel buildStudentsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel form = new JPanel(new GridLayout(2, 1));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        row1.setBackground(Color.WHITE);
        row2.setBackground(Color.WHITE);

        txtStudentId = createTextField(12);
        txtStudentName = createTextField(20);

        JButton btnAdd = createButton("Add Student", new Color(46, 204, 113));
        JButton btnUpdate = createButton("Update Name", new Color(52, 152, 219));
        JButton btnDelete = createButton("Delete", new Color(231, 76, 60));
        JButton btnClear = createButton("Clear", new Color(149, 165, 166));
        JButton btnImport = createButton("Import CSV", new Color(230, 126, 34));

        btnAdd.addActionListener(e -> addStudent());
        btnUpdate.addActionListener(e -> updateStudent());
        btnDelete.addActionListener(e -> deleteStudent());
        btnClear.addActionListener(e -> clearStudentFields());
        btnImport.addActionListener(e -> importCsv());

        row1.add(createLabel("Student ID:"));
        row1.add(txtStudentId);
        row1.add(createLabel("Student Name:"));
        row1.add(txtStudentName);
        row2.add(btnAdd);
        row2.add(btnUpdate);
        row2.add(btnDelete);
        row2.add(btnClear);
        row2.add(btnImport);
        form.add(row1);
        form.add(row2);

        studentModel = new DefaultTableModel(new String[] {"Student ID", "Student Name"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        studentTable = new JTable(studentModel);
        styleTable(studentTable);
        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = studentTable.getSelectedRow();
                if (r >= 0) {
                    txtStudentId.setText(studentModel.getValueAt(r, 0).toString());
                    txtStudentName.setText(studentModel.getValueAt(r, 1).toString());
                    txtStudentId.setEditable(false); // ID is the key, only the name can change
                }
            }
        });

        JLabel hint = new JLabel("Step 1: Add students one by one, or use Import CSV (columns: Student ID, Student Name). "
                + "Click a row to update or delete it.");
        hint.setFont(FONT);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setBackground(BACKGROUND);
        top.add(hint, BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(wrap(studentTable), BorderLayout.CENTER);
        return panel;
    }

    // ---------------- Tab 2 : Take attendance ----------------
    private JPanel buildAttendanceTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel bar = new JPanel(new GridLayout(2, 1));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel barRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        JPanel barRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        barRow1.setBackground(Color.WHITE);
        barRow2.setBackground(Color.WHITE);

        txtSheetDate = createTextField(10);
        txtSheetDate.setText(LocalDate.now().toString());

        JButton btnToday = createButton("Today", new Color(149, 165, 166));
        JButton btnLoad = createButton("Load", new Color(52, 152, 219));
        JButton btnAllPresent = createButton("All Present", new Color(46, 204, 113));
        JButton btnAllAbsent = createButton("All Absent", new Color(231, 76, 60));
        JButton btnSave = createButton("Save Attendance", new Color(39, 174, 96));
        btnSave.setPreferredSize(new Dimension(160, 35));

        btnToday.addActionListener(e -> {
            txtSheetDate.setText(LocalDate.now().toString());
            loadSheet();
        });
        btnLoad.addActionListener(e -> loadSheet());
        btnAllPresent.addActionListener(e -> setAllStatus("Present"));
        btnAllAbsent.addActionListener(e -> setAllStatus("Absent"));
        btnSave.addActionListener(e -> saveSheet());

        barRow1.add(createLabel("Date (YYYY-MM-DD):"));
        barRow1.add(txtSheetDate);
        barRow1.add(btnToday);
        barRow1.add(btnLoad);
        barRow2.add(btnAllPresent);
        barRow2.add(btnAllAbsent);
        barRow2.add(btnSave);
        bar.add(barRow1);
        bar.add(barRow2);

        sheetModel = new DefaultTableModel(new String[] {"Student ID", "Student Name", "Status"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2; // only status can be changed
            }
        };
        sheetTable = new JTable(sheetModel);
        styleTable(sheetTable);
        sheetTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(new JComboBox<>(STATUSES)));

        lblSheetInfo = new JLabel(" ");
        lblSheetInfo.setFont(FONT);

        JLabel hint = new JLabel("Step 2: Choose the date, click the Status cell of each student to change it, "
                + "then press Save Attendance.");
        hint.setFont(FONT);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setBackground(BACKGROUND);
        top.add(hint, BorderLayout.NORTH);
        top.add(bar, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(wrap(sheetTable), BorderLayout.CENTER);
        panel.add(lblSheetInfo, BorderLayout.SOUTH);
        return panel;
    }

    // ---------------- Tab 3 : Records & reports ----------------
    private JPanel buildRecordsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filter.setBackground(Color.WHITE);
        filter.setBorder(BorderFactory.createLineBorder(BORDER));

        txtFilterId = createTextField(8);
        txtFromDate = createTextField(9);
        txtToDate = createTextField(9);

        JButton btnSearch = createButton("Search", new Color(155, 89, 182));
        JButton btnAll = createButton("Show All", new Color(52, 152, 219));
        btnSearch.addActionListener(e -> loadRecords());
        btnAll.addActionListener(e -> {
            txtFilterId.setText("");
            txtFromDate.setText("");
            txtToDate.setText("");
            loadRecords();
        });

        filter.add(createLabel("Student ID:"));
        filter.add(txtFilterId);
        filter.add(createLabel("From:"));
        filter.add(txtFromDate);
        filter.add(createLabel("To:"));
        filter.add(txtToDate);
        filter.add(btnSearch);
        filter.add(btnAll);

        recordModel = new DefaultTableModel(new String[] {"ID", "Student ID", "Student Name", "Date", "Status"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        recordTable = new JTable(recordModel);
        styleTable(recordTable);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actions.setBackground(BACKGROUND);
        cmbNewStatus = new JComboBox<>(STATUSES);
        cmbNewStatus.setFont(FONT);
        JButton btnChange = createButton("Set Status", new Color(52, 152, 219));
        JButton btnDelete = createButton("Delete Record", new Color(231, 76, 60));
        JButton btnDeleteAll = createButton("Delete Many...", new Color(169, 50, 38));
        JButton btnExport = createButton("Export CSV", new Color(230, 126, 34));
        btnDeleteAll.addActionListener(e -> deleteAllRecords());
        btnChange.addActionListener(e -> changeRecordStatus());
        btnDelete.addActionListener(e -> deleteRecord());
        btnExport.addActionListener(e -> exportCsv());

        lblSummary = new JLabel(" ");
        lblSummary.setFont(FONT_BOLD);

        actions.add(createLabel("Change selected record to:"));
        actions.add(cmbNewStatus);
        actions.add(btnChange);
        actions.add(btnDelete);
        actions.add(btnDeleteAll);
        actions.add(btnExport);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BACKGROUND);
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(lblSummary, BorderLayout.SOUTH);

        panel.add(filter, BorderLayout.NORTH);
        panel.add(wrap(recordTable), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    // ---------------- UI helpers ----------------
    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BOLD);
        return l;
    }

    private JTextField createTextField(int cols) {
        JTextField t = new JTextField(cols);
        t.setFont(FONT);
        t.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return t;
    }

    private JButton createButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(130, 35));
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                b.setBackground(bg.darker());
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                b.setBackground(bg);
            }
        });
        return b;
    }

    private void styleTable(JTable t) {
        t.setFont(FONT);
        t.setRowHeight(30);
        t.setGridColor(BORDER);
        t.setSelectionBackground(new Color(52, 152, 219));
        t.setSelectionForeground(Color.WHITE);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);

        // Custom header renderer (the Windows look & feel ignores header colours otherwise)
        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer();
        headerRenderer.setBackground(new Color(52, 73, 94));
        headerRenderer.setForeground(Color.WHITE);
        headerRenderer.setFont(FONT_BOLD);
        headerRenderer.setHorizontalAlignment(JLabel.CENTER);
        headerRenderer.setOpaque(true);
        t.getTableHeader().setDefaultRenderer(headerRenderer);
        t.getTableHeader().setPreferredSize(new Dimension(100, 34));

        // Colour the Status column
        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.CENTER);
                if (!isSelected) {
                    String s = String.valueOf(value);
                    if ("Present".equals(s)) {
                        c.setForeground(new Color(39, 174, 96));
                    } else if ("Absent".equals(s)) {
                        c.setForeground(new Color(192, 57, 43));
                    } else if ("Late".equals(s)) {
                        c.setForeground(new Color(211, 84, 0));
                    }
                    c.setFont(FONT_BOLD);
                }
                return c;
            }
        };
        int statusCol = t.getColumnCount() - 1;
        t.getColumnModel().getColumn(statusCol).setCellRenderer(statusRenderer);
    }

    private JScrollPane wrap(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // =====================================================================
    //  DATABASE
    // =====================================================================
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void createDatabaseAndTables() {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS students ("
                    + "student_id TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS attendance_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "student_id TEXT NOT NULL,"
                    + "date TEXT NOT NULL,"
                    + "status TEXT NOT NULL,"
                    + "UNIQUE(student_id, date))");
        } catch (SQLException e) {
            error("Database error: " + e.getMessage());
        }
    }

    // =====================================================================
    //  STUDENTS
    // =====================================================================
    private void loadStudents() {
        studentModel.setRowCount(0);
        try (Connection conn = connect();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT student_id, name FROM students ORDER BY student_id")) {
            while (rs.next()) {
                studentModel.addRow(new Object[] {rs.getString(1), rs.getString(2)});
            }
        } catch (SQLException e) {
            error("Error loading students: " + e.getMessage());
        }
    }

    private void addStudent() {
        String id = txtStudentId.getText().trim();
        String name = txtStudentName.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            warn("Please enter both Student ID and Student Name!");
            return;
        }
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO students(student_id, name) VALUES(?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
            clearStudentFields();
            loadStudents();
            info("Student added successfully!");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")
                    || e.getMessage() != null && e.getMessage().contains("PRIMARYKEY")) {
                warn("A student with ID '" + id + "' already exists!");
            } else {
                error("Error adding student: " + e.getMessage());
            }
        }
    }

    private void updateStudent() {
        String id = txtStudentId.getText().trim();
        String name = txtStudentName.getText().trim();
        if (studentTable.getSelectedRow() == -1) {
            warn("Please select a student from the table first!");
            return;
        }
        if (name.isEmpty()) {
            warn("Student name cannot be empty!");
            return;
        }
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement("UPDATE students SET name=? WHERE student_id=?")) {
            ps.setString(1, name);
            ps.setString(2, id);
            ps.executeUpdate();
            clearStudentFields();
            loadStudents();
            info("Student updated successfully!");
        } catch (SQLException e) {
            error("Error updating student: " + e.getMessage());
        }
    }

    private void deleteStudent() {
        int row = studentTable.getSelectedRow();
        if (row == -1) {
            warn("Please select a student to delete!");
            return;
        }
        String id = studentModel.getValueAt(row, 0).toString();
        int c = JOptionPane.showConfirmDialog(this,
                "Delete student " + id + " and ALL of their attendance records?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement p1 = conn.prepareStatement("DELETE FROM attendance_records WHERE student_id=?");
                    PreparedStatement p2 = conn.prepareStatement("DELETE FROM students WHERE student_id=?")) {
                p1.setString(1, id);
                p1.executeUpdate();
                p2.setString(1, id);
                p2.executeUpdate();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            clearStudentFields();
            loadStudents();
            info("Student deleted.");
        } catch (SQLException e) {
            error("Error deleting student: " + e.getMessage());
        }
    }

    /** Reads a CSV file (Student ID, Student Name) and adds every student to the database. */
    private void importCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a CSV file (columns: Student ID, Student Name)");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        int added = 0;
        int duplicates = 0;
        int invalid = 0;

        try (Connection conn = connect();
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn
                    .prepareStatement("INSERT OR IGNORE INTO students(student_id, name) VALUES(?, ?)")) {
                String line;
                boolean isFirstLine = true;
                while ((line = br.readLine()) != null) {
                    line = line.replace("\uFEFF", "").trim(); // remove hidden BOM that Excel adds
                    if (line.isEmpty()) {
                        continue;
                    }
                    List<String> cells = splitCsvLine(line);

                    // skip the header row if there is one
                    if (isFirstLine) {
                        isFirstLine = false;
                        String h = cells.get(0).toLowerCase();
                        if (h.equals("student id") || h.equals("studentid") || h.equals("id")) {
                            continue;
                        }
                    }

                    if (cells.size() < 2 || cells.get(0).isEmpty() || cells.get(1).isEmpty()) {
                        invalid++;
                        continue;
                    }
                    ps.setString(1, cells.get(0));
                    ps.setString(2, cells.get(1));
                    if (ps.executeUpdate() > 0) {
                        added++;
                    } else {
                        duplicates++; // this Student ID already exists
                    }
                }
            }
            conn.commit();
        } catch (IOException | SQLException e) {
            error("Error importing CSV (nothing was saved): " + e.getMessage());
            return;
        }

        loadStudents();
        info("Import finished!\n\nAdded: " + added + "\nSkipped (ID already exists): " + duplicates
                + "\nSkipped (invalid/blank rows): " + invalid);
    }

    /** Splits one CSV line into cells. Understands "quoted, values" and , or ; separators. */
    private List<String> splitCsvLine(String line) {
        char delimiter = line.indexOf(',') >= 0 ? ',' : ';';
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); // escaped quote ""
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                cells.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cells.add(cur.toString().trim());
        return cells;
    }

    private void clearStudentFields() {
        txtStudentId.setText("");
        txtStudentName.setText("");
        txtStudentId.setEditable(true);
        studentTable.clearSelection();
        txtStudentId.requestFocusInWindow();
    }

    // =====================================================================
    //  TAKE ATTENDANCE
    // =====================================================================
    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void loadSheet() {
        if (sheetTable != null && sheetTable.isEditing()) {
            sheetTable.getCellEditor().stopCellEditing();
        }
        LocalDate d = parseDate(txtSheetDate.getText());
        if (d == null) {
            warn("Invalid date! Use the format YYYY-MM-DD, e.g. " + LocalDate.now());
            return;
        }
        sheetModel.setRowCount(0);
        int saved = 0;
        String sql = "SELECT s.student_id, s.name, r.status FROM students s "
                + "LEFT JOIN attendance_records r ON r.student_id = s.student_id AND r.date = ? "
                + "ORDER BY s.student_id";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString(3);
                    if (status != null) {
                        saved++;
                    } else {
                        status = "Present"; // default for not-yet-marked students
                    }
                    sheetModel.addRow(new Object[] {rs.getString(1), rs.getString(2), status});
                }
            }
        } catch (SQLException e) {
            error("Error loading attendance sheet: " + e.getMessage());
            return;
        }
        int total = sheetModel.getRowCount();
        if (total == 0) {
            lblSheetInfo.setText("No students yet. Go to the 'Students' tab and add some first.");
        } else if (saved == 0) {
            lblSheetInfo.setText("Attendance for " + d + " has NOT been saved yet (" + total
                    + " students, all set to Present by default).");
        } else {
            lblSheetInfo.setText("Attendance for " + d + " was already saved for " + saved + " of " + total
                    + " students. You can edit and save again.");
        }
    }

    private void setAllStatus(String status) {
        if (sheetTable.isEditing()) {
            sheetTable.getCellEditor().stopCellEditing();
        }
        for (int i = 0; i < sheetModel.getRowCount(); i++) {
            sheetModel.setValueAt(status, i, 2);
        }
    }

    private void saveSheet() {
        if (sheetTable.isEditing()) {
            sheetTable.getCellEditor().stopCellEditing();
        }
        LocalDate d = parseDate(txtSheetDate.getText());
        if (d == null) {
            warn("Invalid date! Use the format YYYY-MM-DD.");
            return;
        }
        if (sheetModel.getRowCount() == 0) {
            warn("There are no students to save. Add students first.");
            return;
        }
        String sql = "INSERT INTO attendance_records(student_id, date, status) VALUES(?, ?, ?) "
                + "ON CONFLICT(student_id, date) DO UPDATE SET status = excluded.status";
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < sheetModel.getRowCount(); i++) {
                    ps.setString(1, sheetModel.getValueAt(i, 0).toString());
                    ps.setString(2, d.toString());
                    ps.setString(3, sheetModel.getValueAt(i, 2).toString());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            info("Attendance for " + d + " saved successfully!");
            loadSheet();
        } catch (SQLException e) {
            error("Error saving attendance: " + e.getMessage());
        }
    }

    // =====================================================================
    //  RECORDS & REPORTS
    // =====================================================================
    private void loadRecords() {
        String from = txtFromDate.getText().trim();
        String to = txtToDate.getText().trim();
        if (!from.isEmpty() && parseDate(from) == null || !to.isEmpty() && parseDate(to) == null) {
            warn("Invalid From/To date! Use the format YYYY-MM-DD or leave blank.");
            return;
        }
        recordModel.setRowCount(0);
        String sql = "SELECT r.id, r.student_id, s.name, r.date, r.status "
                + "FROM attendance_records r JOIN students s ON s.student_id = r.student_id "
                + "WHERE r.student_id LIKE ? AND r.date >= ? AND r.date <= ? "
                + "ORDER BY r.date DESC, r.student_id";
        int present = 0, absent = 0, late = 0;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + txtFilterId.getText().trim() + "%");
            ps.setString(2, from.isEmpty() ? "0000-01-01" : from);
            ps.setString(3, to.isEmpty() ? "9999-12-31" : to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString(5);
                    switch (status) {
                        case "Present":
                            present++;
                            break;
                        case "Absent":
                            absent++;
                            break;
                        default:
                            late++;
                            break;
                    }
                    recordModel.addRow(new Object[] {rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), status});
                }
            }
        } catch (SQLException e) {
            error("Error loading records: " + e.getMessage());
            return;
        }
        int total = present + absent + late;
        if (total == 0) {
            lblSummary.setText("No records found.");
        } else {
            double pct = (present + late) * 100.0 / total;
            lblSummary.setText(String.format(
                    "Total: %d   |   Present: %d   |   Late: %d   |   Absent: %d   |   Attendance: %.1f%% (Present + Late)",
                    total, present, late, absent, pct));
        }
    }

    private void changeRecordStatus() {
        int row = recordTable.getSelectedRow();
        if (row == -1) {
            warn("Please select a record first!");
            return;
        }
        int id = Integer.parseInt(recordModel.getValueAt(row, 0).toString());
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement("UPDATE attendance_records SET status=? WHERE id=?")) {
            ps.setString(1, cmbNewStatus.getSelectedItem().toString());
            ps.setInt(2, id);
            ps.executeUpdate();
            loadRecords();
        } catch (SQLException e) {
            error("Error updating record: " + e.getMessage());
        }
    }

    private void deleteRecord() {
        int row = recordTable.getSelectedRow();
        if (row == -1) {
            warn("Please select a record to delete!");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete this attendance record?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        int id = Integer.parseInt(recordModel.getValueAt(row, 0).toString());
        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance_records WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadRecords();
        } catch (SQLException e) {
            error("Error deleting record: " + e.getMessage());
        }
    }

    /** Deletes many attendance records at once: either the rows currently shown, or everything. */
    private void deleteAllRecords() {
        int shown = recordModel.getRowCount();
        Object[] options = {"Delete shown records (" + shown + ")", "Delete ALL records", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "What do you want to delete?\n\n"
                        + "- Shown records: only the rows currently in the table (use the filters to narrow them down)\n"
                        + "- ALL records: every attendance record in the database (students are kept)",
                "Delete Many Records", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options,
                options[2]);
        if (choice != 0 && choice != 1) {
            return; // cancelled
        }
        if (choice == 0 && shown == 0) {
            warn("There are no records shown to delete!");
            return;
        }

        String question = choice == 0
                ? "Permanently delete the " + shown + " records shown in the table?"
                : "Permanently delete EVERY attendance record?\nThis cannot be undone!";
        if (JOptionPane.showConfirmDialog(this, question, "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            if (choice == 1) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM attendance_records");
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance_records WHERE id=?")) {
                    for (int i = 0; i < recordModel.getRowCount(); i++) {
                        ps.setInt(1, Integer.parseInt(recordModel.getValueAt(i, 0).toString()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            error("Error deleting records: " + e.getMessage());
            return;
        }
        loadRecords();
        loadSheet();
        info("Records deleted successfully.");
    }

    private void exportCsv() {
        // make sure the table matches the From / To / Student ID boxes before exporting
        String from = txtFromDate.getText().trim();
        String to = txtToDate.getText().trim();
        if (!from.isEmpty() && parseDate(from) == null || !to.isEmpty() && parseDate(to) == null) {
            warn("Invalid From/To date! Use the format YYYY-MM-DD or leave blank.");
            return;
        }
        loadRecords();

        if (recordModel.getRowCount() == 0) {
            warn("Nothing to export!");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("attendance_report.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (PrintWriter pw = new PrintWriter(fc.getSelectedFile(), StandardCharsets.UTF_8)) {
            pw.println("Student ID,Student Name,Date,Status");
            for (int i = 0; i < recordModel.getRowCount(); i++) {
                pw.println(csv(recordModel.getValueAt(i, 1)) + "," + csv(recordModel.getValueAt(i, 2)) + ","
                        + csv(recordModel.getValueAt(i, 3)) + "," + csv(recordModel.getValueAt(i, 4)));
            }

            // ---- Summary at the bottom: attendance percentage of every student in this report ----
            Map<String, int[]> stats = new TreeMap<>(); // student id -> {present, late, absent}
            Map<String, String> names = new TreeMap<>(); // student id -> name
            String firstDate = null;
            String lastDate = null;
            for (int i = 0; i < recordModel.getRowCount(); i++) {
                String id = recordModel.getValueAt(i, 1).toString();
                names.put(id, recordModel.getValueAt(i, 2).toString());
                String d = recordModel.getValueAt(i, 3).toString();
                if (firstDate == null || d.compareTo(firstDate) < 0) {
                    firstDate = d;
                }
                if (lastDate == null || d.compareTo(lastDate) > 0) {
                    lastDate = d;
                }
                int[] s = stats.computeIfAbsent(id, k -> new int[3]);
                String status = recordModel.getValueAt(i, 4).toString();
                if ("Present".equals(status)) {
                    s[0]++;
                } else if ("Late".equals(status)) {
                    s[1]++;
                } else {
                    s[2]++;
                }
            }

            pw.println();
            pw.println();
            pw.println(csv("ATTENDANCE SUMMARY (from " + (from.isEmpty() ? firstDate : from) + " to "
                    + (to.isEmpty() ? lastDate : to) + ")"));
            pw.println("Student ID,Student Name,Total Days,Present,Late,Absent,Attendance %");
            for (Map.Entry<String, int[]> en : stats.entrySet()) {
                int[] s = en.getValue();
                int total = s[0] + s[1] + s[2];
                double pct = (s[0] + s[1]) * 100.0 / total; // Present + Late counts as attended
                pw.println(csv(en.getKey()) + "," + csv(names.get(en.getKey())) + "," + total + "," + s[0] + ","
                        + s[1] + "," + s[2] + "," + String.format(Locale.US, "%.1f%%", pct));
            }

            info("Exported to " + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException e) {
            error("Error exporting file: " + e.getMessage());
        }
    }

    private String csv(Object o) {
        return "\"" + String.valueOf(o).replace("\"", "\"\"") + "\"";
    }

    // =====================================================================
    //  MAIN
    // =====================================================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new AttendanceManagementSystem().setVisible(true));
    }
}