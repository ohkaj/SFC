import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.URI;

public class DuplicateFileFinder {
    private static final int BUFFER_SIZE = 8192;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".tiff", ".tif", ".webp"
    );
    private Map<String, List<Path>> currentDuplicates = new HashMap<>();
    private Map<String, List<Path>> visualDuplicates = new HashMap<>();
    private Map<String, String> fileHashes = new HashMap<>();
    private Map<String, String> imageDHashes = new HashMap<>();
    private List<Path> allScannedFiles = new ArrayList<>();
    private JPanel resultPanel;
    private JScrollPane resultScrollPane;
    private List<JCheckBox> fileCheckBoxes = new ArrayList<>();
    private JTextArea logArea;
    private JPanel logPanel;
    private boolean logVisible = false;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DuplicateFileFinder().createAndShowGUI());
    }
    
    private void createAndShowGUI() {
        JFrame frame = new JFrame("Duplicate File Finder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);
        frame.setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel folderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        JTextField folderField = new JTextField(30);
        JButton browseButton = new JButton("Browse");
        
        folderPanel.add(new JLabel("Folder:"));
        folderPanel.add(folderField);
        folderPanel.add(browseButton);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
        JButton scanButton = new JButton("Find Duplicates");
        JButton deleteSelectedButton = new JButton("Delete Selected Duplicates");
        deleteSelectedButton.setEnabled(false);
        deleteSelectedButton.setBackground(new Color(180, 50, 50));
        deleteSelectedButton.setForeground(Color.WHITE);
        
        JButton toggleLogButton = new JButton("Show Log");
        JButton exportLogButton = new JButton("Export Log");
        exportLogButton.setEnabled(false);
        
        buttonPanel.add(scanButton);
        buttonPanel.add(deleteSelectedButton);
        buttonPanel.add(toggleLogButton);
        buttonPanel.add(exportLogButton);
        
        topPanel.add(folderPanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
        resultPanel.setBackground(Color.WHITE);
        resultScrollPane = new JScrollPane(resultPanel);
        resultScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        resultScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        resultScrollPane.getVerticalScrollBar().setBlockIncrement(64);
        
        // Create log panel
        logArea = new JTextArea(10, 0);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(new Color(248, 248, 248));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        
        logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Selected Files Log"));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        logPanel.setVisible(false);
        
        // Create split pane for results and log
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultScrollPane, logPanel);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.7);
        
        displayMessage("Select a folder and click 'Find Duplicates' to start scanning.");
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                folderField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        
        scanButton.addActionListener(e -> {
            String folderPath = folderField.getText().trim();
            if (folderPath.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please select a folder first.");
                return;
            }
            
            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                JOptionPane.showMessageDialog(frame, "Invalid folder path.");
                return;
            }
            
            displayMessage("Scanning for duplicates...");
            scanButton.setEnabled(false);
            
            SwingWorker<String, String> worker = new SwingWorker<String, String>() {
                @Override
                protected String doInBackground() throws Exception {
                    fileHashes.clear(); // Clear previous hashes
                    imageDHashes.clear(); // Clear previous dHashes
                    allScannedFiles.clear(); // Clear previous file list
                    publish("Scanning files in directory...");
                    Map<Long, List<Path>> sizeGroups = groupFilesBySize(folder.toPath());
                    Map<String, List<Path>> duplicates = new HashMap<>();
                    
                    int totalFiles = 0;
                    int duplicateGroups = 0;
                    int duplicateFiles = 0;
                    
                    for (List<Path> files : sizeGroups.values()) {
                        totalFiles += files.size();
                        allScannedFiles.addAll(files); // Store all scanned files
                    }
                    
                    publish(String.format("Found %d files. Analyzing for duplicates...", totalFiles));
                    
                    int processedGroups = 0;
                    int totalGroups = (int) sizeGroups.values().stream().filter(files -> files.size() > 1).count();
                    
                    for (List<Path> files : sizeGroups.values()) {
                        if (files.size() > 1) {
                            processedGroups++;
                            publish(String.format("Checking duplicates... (%d/%d groups)", processedGroups, totalGroups));
                            Map<String, List<Path>> hashGroups = groupFilesByHash(files);
                            for (Map.Entry<String, List<Path>> entry : hashGroups.entrySet()) {
                                String hash = entry.getKey();
                                // Store hash for ALL files, not just duplicates
                                for (Path file : entry.getValue()) {
                                    fileHashes.put(file.toString(), hash);
                                }
                                
                                if (entry.getValue().size() > 1) {
                                    duplicates.put(entry.getKey(), entry.getValue());
                                    duplicateGroups++;
                                    duplicateFiles += entry.getValue().size();
                                }
                            }
                        } else {
                            // Calculate hash for unique files too
                            Path uniqueFile = files.get(0);
                            try {
                                String hash = calculateFileHash(uniqueFile);
                                fileHashes.put(uniqueFile.toString(), hash);
                            } catch (Exception e) {
                                System.err.println("Error calculating hash for unique file: " + uniqueFile + " - " + e.getMessage());
                            }
                        }
                    }
                    
                    // Phase 2: Visual duplicate detection for images (excluding those already in exact duplicates)
                    publish("Analyzing images for visual similarity...");
                    
                    // Get all files already in exact duplicate groups
                    Set<String> exactDuplicateFiles = new HashSet<>();
                    for (List<Path> files : duplicates.values()) {
                        for (Path file : files) {
                            exactDuplicateFiles.add(file.toString());
                        }
                    }
                    
                    // Filter image files, excluding those already found as exact duplicates
                    List<Path> imageFiles = allScannedFiles.stream()
                        .filter(file -> isImageFile(file) && !exactDuplicateFiles.contains(file.toString()))
                        .collect(Collectors.toList());
                    
                    if (!imageFiles.isEmpty()) {
                        visualDuplicates = findVisualDuplicates(imageFiles);
                        publish(String.format("Found %d visual duplicate groups", visualDuplicates.size()));
                    }
                    
                    currentDuplicates = duplicates;
                    boolean hasAnyDuplicates = !duplicates.isEmpty() || !visualDuplicates.isEmpty();
                    deleteSelectedButton.setEnabled(hasAnyDuplicates);
                    exportLogButton.setEnabled(hasAnyDuplicates);
                    return ""; // Results handled by displayResults()
                }
                
                @Override
                protected void process(List<String> chunks) {
                    if (!chunks.isEmpty()) {
                        displayMessage(chunks.get(chunks.size() - 1));
                    }
                }
                
                @Override
                protected void done() {
                    try {
                        get(); // This will be empty now, results are handled differently
                        displayResults();
                    } catch (Exception ex) {
                        displayError("Error occurred during scan: " + ex.getMessage());
                    }
                    scanButton.setEnabled(true);
                }
            };
            worker.execute();
        });
        
        deleteSelectedButton.addActionListener(e -> {
            List<Path> selectedFiles = new ArrayList<>();
            for (JCheckBox checkbox : fileCheckBoxes) {
                if (checkbox.isSelected()) {
                    selectedFiles.add(Path.of(checkbox.getActionCommand()));
                }
            }
            
            if (selectedFiles.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "No files selected for deletion.");
                return;
            }
            
            int confirm = JOptionPane.showConfirmDialog(frame,
                String.format("Are you sure you want to permanently delete %d selected files?\n\nThis action cannot be undone!",
                    selectedFiles.size()),
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            
            deleteSelectedFiles(selectedFiles, frame, scanButton);
        });
        
        toggleLogButton.addActionListener(e -> {
            logVisible = !logVisible;
            logPanel.setVisible(logVisible);
            toggleLogButton.setText(logVisible ? "Hide Log" : "Show Log");
            splitPane.setDividerLocation(logVisible ? 300 : 400);
            frame.revalidate();
        });
        
        exportLogButton.addActionListener(e -> exportLog(frame));
        
        frame.add(mainPanel);
        frame.setVisible(true);
    }
    
    private void displayMessage(String message) {
        resultPanel.removeAll();
        fileCheckBoxes.clear();
        
        JLabel messageLabel = new JLabel("<html><h3>" + message + "</h3></html>");
        messageLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        resultPanel.add(messageLabel);
        
        resultPanel.revalidate();
        resultPanel.repaint();
    }
    
    private void displayError(String error) {
        resultPanel.removeAll();
        fileCheckBoxes.clear();
        
        JLabel errorLabel = new JLabel("<html><h3 style='color: red;'>Error occurred during scan:</h3>" +
            "<p>" + error + "</p>" +
            "<p>Please check:</p><ul>" +
            "<li>Folder permissions</li>" +
            "<li>Folder path is correct</li>" +
            "<li>Files are not locked by other programs</li>" +
            "</ul></html>");
        errorLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        resultPanel.add(errorLabel);
        
        resultPanel.revalidate();
        resultPanel.repaint();
    }
    
    private void displayResults() {
        resultPanel.removeAll();
        fileCheckBoxes.clear();
        
        boolean hasExactDuplicates = !currentDuplicates.isEmpty();
        boolean hasVisualDuplicates = !visualDuplicates.isEmpty();
        
        if (!hasExactDuplicates && !hasVisualDuplicates) {
            JLabel noResultsLabel = new JLabel("<html><h3>No duplicate files found.</h3></html>");
            noResultsLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            resultPanel.add(noResultsLabel);
        } else {
            JLabel headerLabel = new JLabel("<html><h3>Duplicate Files Found - Check files to delete:</h3></html>");
            headerLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            resultPanel.add(headerLabel);
            
            JPanel selectAllPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            JButton selectAllButton = new JButton("Select All");
            JButton deselectAllButton = new JButton("Deselect All");
            
            selectAllButton.addActionListener(e -> {
                for (JCheckBox cb : fileCheckBoxes) {
                    cb.setSelected(true);
                }
            });
            
            deselectAllButton.addActionListener(e -> {
                for (JCheckBox cb : fileCheckBoxes) {
                    cb.setSelected(false);
                }
            });
            
            selectAllPanel.add(selectAllButton);
            selectAllPanel.add(deselectAllButton);
            resultPanel.add(selectAllPanel);
            
            int groupNum = 1;
            
            // Display exact duplicates (same file hash)
            if (hasExactDuplicates) {
                JLabel exactLabel = new JLabel("<html><h4 style='color: blue;'>Exact Duplicates (Same Content):</h4></html>");
                exactLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
                resultPanel.add(exactLabel);
                
                for (Map.Entry<String, List<Path>> entry : currentDuplicates.entrySet()) {
                    groupNum = addDuplicateGroup(entry.getValue(), groupNum, "Exact", false);
                }
            }
            
            // Display visual duplicates (similar images)
            if (hasVisualDuplicates) {
                JLabel visualLabel = new JLabel("<html><h4 style='color: green;'>Visual Duplicates (Similar Images):</h4></html>");
                visualLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
                resultPanel.add(visualLabel);
                
                for (Map.Entry<String, List<Path>> entry : visualDuplicates.entrySet()) {
                    groupNum = addDuplicateGroup(entry.getValue(), groupNum, "Visual", true);
                }
            }
        }
        
        resultPanel.revalidate();
        resultPanel.repaint();
        
        // Update log after displaying results
        updateLog();
    }
    
    private int addDuplicateGroup(List<Path> files, int groupNum, String groupType, boolean isVisual) {
        long fileSize = 0;
        try {
            fileSize = Files.size(files.get(0));
        } catch (IOException e) {
            fileSize = -1;
        }
        
        JPanel groupPanel = new JPanel();
        groupPanel.setLayout(new BoxLayout(groupPanel, BoxLayout.Y_AXIS));
        String titleText = String.format("%s Group %d (%d files, %s)", groupType, groupNum, files.size(), formatFileSize(fileSize));
        groupPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(titleText),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            JPanel filePanel = new JPanel(new BorderLayout());
            filePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            filePanel.setPreferredSize(new Dimension(0, 25));
            
            JCheckBox checkbox = new JCheckBox();
            checkbox.setActionCommand(file.toString());
            if (i > 0) { // Pre-select duplicates (keep first file unchecked)
                checkbox.setSelected(true);
            }
            checkbox.addChangeListener(e -> updateLog());
            fileCheckBoxes.add(checkbox);
            
            JButton fileButton = new JButton(file.getFileName().toString());
            fileButton.setToolTipText(file.toString());
            fileButton.addActionListener(e -> openFileInExplorer(file.toString()));
            fileButton.setBorderPainted(false);
            fileButton.setContentAreaFilled(false);
            fileButton.setForeground(isVisual ? new Color(0, 120, 0) : Color.BLUE);
            fileButton.setHorizontalAlignment(SwingConstants.LEFT);
            fileButton.setPreferredSize(new Dimension(200, 20));
            
            JLabel pathLabel = new JLabel(file.getParent().toString());
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 9f));
            pathLabel.setForeground(Color.GRAY);
            
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            leftPanel.add(checkbox);
            leftPanel.add(fileButton);
            
            filePanel.add(leftPanel, BorderLayout.WEST);
            filePanel.add(pathLabel, BorderLayout.CENTER);
            
            groupPanel.add(filePanel);
        }
        
        resultPanel.add(groupPanel);
        return groupNum + 1;
    }
    
    private void updateLog() {
        StringBuilder logContent = new StringBuilder();
        logContent.append("ALL FILES LOG\n");
        logContent.append("=============\n");
        logContent.append(String.format("Generated: %s\n", java.time.LocalDateTime.now().toString()));
        logContent.append("\nFormat: [STATUS] FILENAME | HASH | DHASH | FULL_PATH\n");
        logContent.append("STATUS: [CHECKED] = Selected for deletion, [UNCHECKED] = Not selected, [UNIQUE] = No duplicates\n");
        logContent.append("HASH: SHA-256 file content hash\n");
        logContent.append("DHASH: Image perceptual hash (only for images)\n\n");
        
        List<String> allFiles = new ArrayList<>();
        int checkedCount = 0;
        int uncheckedCount = 0;
        int uniqueCount = 0;
        
        // Get all checked file paths for quick lookup
        Set<String> checkedFilePaths = new HashSet<>();
        for (JCheckBox checkbox : fileCheckBoxes) {
            if (checkbox.isSelected()) {
                checkedFilePaths.add(checkbox.getActionCommand());
            }
        }
        
        // Get all duplicate file paths for quick lookup (both exact and visual)
        Set<String> duplicateFilePaths = new HashSet<>();
        for (List<Path> files : currentDuplicates.values()) {
            for (Path file : files) {
                duplicateFilePaths.add(file.toString());
            }
        }
        for (List<Path> files : visualDuplicates.values()) {
            for (Path file : files) {
                duplicateFilePaths.add(file.toString());
            }
        }
        
        // Process all scanned files
        for (Path file : allScannedFiles) {
            String filePath = file.toString();
            String hash = fileHashes.get(filePath);
            String dHash = imageDHashes.get(filePath);
            String fileName = file.getFileName().toString();
            String status;
            
            if (checkedFilePaths.contains(filePath)) {
                status = "[CHECKED]  ";
                checkedCount++;
            } else if (duplicateFilePaths.contains(filePath)) {
                status = "[UNCHECKED]";
                uncheckedCount++;
            } else {
                status = "[UNIQUE]   ";
                uniqueCount++;
            }
            
            String fileInfo = String.format("%s %s | %s | %s | %s", 
                status, fileName, hash, 
                (dHash != null ? dHash : "N/A"), filePath);
            allFiles.add(fileInfo);
        }
        
        if (allFiles.isEmpty()) {
            logContent.append("No files scanned yet.\n");
        } else {
            logContent.append(String.format("Total files: %d (Checked: %d, Unchecked: %d, Unique: %d)\n\n", 
                allFiles.size(), checkedCount, uncheckedCount, uniqueCount));
            
            // Sort files alphabetically by name for easier reading
            allFiles.sort((a, b) -> {
                String nameA = a.substring(a.indexOf(']') + 2).split(" \\| ")[0];
                String nameB = b.substring(b.indexOf(']') + 2).split(" \\| ")[0];
                return nameA.compareToIgnoreCase(nameB);
            });
            
            int counter = 1;
            for (String fileInfo : allFiles) {
                logContent.append(String.format("%4d. %s\n", counter++, fileInfo));
            }
        }
        
        logArea.setText(logContent.toString());
        logArea.setCaretPosition(0);
    }
    
    private void exportLog(JFrame parent) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Log File");
        String timestamp = java.time.LocalDateTime.now().toString().replaceAll(":", "-").substring(0, 19);
        fileChooser.setSelectedFile(new File("duplicate_files_log_" + timestamp + ".txt"));
        
        if (fileChooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(logArea.getText());
                JOptionPane.showMessageDialog(parent, 
                    "Log exported successfully to: " + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(parent, 
                    "Error exporting log: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void deleteSelectedFiles(List<Path> filesToDelete, JFrame parent, JButton scanButton) {
        SwingWorker<Void, String> deleteWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                int successCount = 0;
                StringBuilder errors = new StringBuilder();
                
                for (Path filePath : filesToDelete) {
                    try {
                        File file = filePath.toFile();
                        if (file.exists()) {
                            if (file.delete()) {
                                successCount++;
                                publish("Deleted: " + file.getName());
                            } else {
                                errors.append("Failed to delete: ").append(filePath).append("\n");
                            }
                        } else {
                            errors.append("File not found: ").append(filePath).append("\n");
                        }
                    } catch (Exception ex) {
                        errors.append("Error deleting ").append(filePath).append(": ").append(ex.getMessage()).append("\n");
                    }
                }
                
                final String finalMessage;
                if (errors.length() > 0) {
                    finalMessage = String.format("Deleted %d files successfully.\n\nErrors:\n%s", successCount, errors.toString());
                } else {
                    finalMessage = String.format("Deleted %d files successfully.", successCount);
                }
                
                final boolean hasErrors = errors.length() > 0;
                
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(parent, finalMessage, "Deletion Results", 
                        hasErrors ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                    scanButton.doClick(); // Refresh the results
                });
                
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) {
                    displayMessage("Deleting files... " + chunks.get(chunks.size() - 1));
                }
            }
        };
        
        deleteWorker.execute();
    }
    
    private void openFileInExplorer(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec("explorer.exe /select,\"" + file.getAbsolutePath() + "\"");
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec("open -R \"" + file.getAbsolutePath() + "\"");
                } else {
                    Desktop.getDesktop().open(file.getParentFile());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Could not open file location: " + ex.getMessage());
        }
    }
    
    private String formatResults(int totalFiles, int duplicateGroups, int duplicateFiles, Map<String, List<Path>> duplicates) {
        StringBuilder result = new StringBuilder();
        
        result.append("<html><body>");
        result.append(String.format("<h3>Scan completed!</h3>"));
        result.append(String.format("<p><b>Total files scanned:</b> %d<br>", totalFiles));
        result.append(String.format("<b>Duplicate groups found:</b> %d<br>", duplicateGroups));
        result.append(String.format("<b>Total duplicate files:</b> %d</p>", duplicateFiles));
        
        if (totalFiles == 0) {
            result.append("<p><b>No files found in the selected folder.</b></p>");
            result.append("<p>This could be because:</p>");
            result.append("<ul>");
            result.append("<li>The folder is empty</li>");
            result.append("<li>Files are not readable due to permissions</li>");
            result.append("<li>The folder contains only subfolders with no files</li>");
            result.append("<li>Access is restricted by the operating system</li>");
            result.append("</ul>");
        } else if (duplicates.isEmpty()) {
            result.append("<p>No duplicate files found.</p>");
        } else {
            result.append("<h3>DUPLICATE FILES:</h3>");
            result.append("<hr>");
            
            int groupNum = 1;
            for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
                List<Path> files = entry.getValue();
                long fileSize = 0;
                try {
                    fileSize = Files.size(files.get(0));
                } catch (IOException e) {
                    fileSize = -1;
                }
                
                result.append(String.format("<h4>Group %d (%d files, %s):</h4>", 
                    groupNum++, files.size(), formatFileSize(fileSize)));
                
                result.append("<ul>");
                for (Path file : files) {
                    String fileName = file.getFileName().toString();
                    String fullPath = file.toString();
                    result.append(String.format("<li><a href=\"%s\">%s</a><br><small>%s</small></li>", 
                        fullPath, fileName, fullPath));
                }
                result.append("</ul>");
            }
        }
        
        result.append("</body></html>");
        return result.toString();
    }
    
    private Map<Long, List<Path>> groupFilesBySize(Path rootPath) throws IOException {
        Map<Long, List<Path>> sizeGroups = new HashMap<>();
        
        if (!Files.exists(rootPath)) {
            throw new IOException("Path does not exist: " + rootPath);
        }
        
        if (!Files.isDirectory(rootPath)) {
            throw new IOException("Path is not a directory: " + rootPath);
        }
        
        if (!Files.isReadable(rootPath)) {
            throw new IOException("Cannot read directory: " + rootPath);
        }
        
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(path -> {
                try {
                    return Files.isRegularFile(path) && Files.isReadable(path);
                } catch (Exception e) {
                    System.err.println("Error checking file: " + path + " - " + e.getMessage());
                    return false;
                }
            })
            .forEach(path -> {
                try {
                    long size = Files.size(path);
                    sizeGroups.computeIfAbsent(size, k -> new ArrayList<>()).add(path);
                } catch (IOException e) {
                    System.err.println("Error reading file size: " + path + " - " + e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new IOException("Error walking directory tree: " + e.getMessage(), e);
        }
        
        return sizeGroups;
    }
    
    private Map<String, List<Path>> groupFilesByHash(List<Path> files) {
        Map<String, List<Path>> hashGroups = new HashMap<>();
        
        for (Path file : files) {
            try {
                String hash = calculateFileHash(file);
                hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
            } catch (Exception e) {
                System.err.println("Error calculating hash for: " + file + " - " + e.getMessage());
            }
        }
        
        return hashGroups;
    }
    
    private String calculateFileHash(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        try (InputStream inputStream = Files.newInputStream(filePath);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }
    
    private boolean isImageFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        return IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
    
    private String calculateDHash(Path imagePath) throws Exception {
        try {
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image == null) {
                throw new Exception("Could not read image: " + imagePath);
            }
            
            // Resize to 9x8 for dHash (we need 9 columns to compare 8 differences)
            BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, 9, 8, null);
            g.dispose();
            
            // Calculate dHash by comparing adjacent pixels
            StringBuilder hash = new StringBuilder();
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int leftPixel = resized.getRGB(x, y) & 0xFF;
                    int rightPixel = resized.getRGB(x + 1, y) & 0xFF;
                    hash.append(leftPixel < rightPixel ? '1' : '0');
                }
            }
            
            // Convert binary string to hexadecimal
            StringBuilder hexHash = new StringBuilder();
            for (int i = 0; i < hash.length(); i += 4) {
                String fourBits = hash.substring(i, Math.min(i + 4, hash.length()));
                while (fourBits.length() < 4) fourBits += "0"; // Pad if needed
                int value = Integer.parseInt(fourBits, 2);
                hexHash.append(Integer.toHexString(value));
            }
            
            return hexHash.toString();
        } catch (Exception e) {
            throw new Exception("Error calculating dHash for " + imagePath + ": " + e.getMessage());
        }
    }
    
    private int calculateHammingDistance(String hash1, String hash2) {
        if (hash1.length() != hash2.length()) {
            return Integer.MAX_VALUE;
        }
        
        int distance = 0;
        for (int i = 0; i < hash1.length(); i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }
    
    private Map<String, List<Path>> findVisualDuplicates(List<Path> imageFiles) {
        Map<String, List<Path>> visualDups = new HashMap<>();
        
        // Calculate dHash for all images
        for (Path imageFile : imageFiles) {
            try {
                String dHash = calculateDHash(imageFile);
                imageDHashes.put(imageFile.toString(), dHash);
            } catch (Exception e) {
                System.err.println("Error calculating dHash for: " + imageFile + " - " + e.getMessage());
            }
        }
        
        // Find similar images (Hamming distance <= 5 is considered similar)
        List<Path> processedImages = new ArrayList<>();
        for (Path imageFile : imageFiles) {
            String currentHash = imageDHashes.get(imageFile.toString());
            if (currentHash == null) continue;
            
            boolean foundGroup = false;
            for (Map.Entry<String, List<Path>> entry : visualDups.entrySet()) {
                String groupHash = entry.getKey();
                if (calculateHammingDistance(currentHash, groupHash) <= 5) {
                    entry.getValue().add(imageFile);
                    foundGroup = true;
                    break;
                }
            }
            
            if (!foundGroup && !processedImages.contains(imageFile)) {
                List<Path> similarImages = new ArrayList<>();
                similarImages.add(imageFile);
                
                // Find all similar images to this one
                for (Path otherImage : imageFiles) {
                    if (!otherImage.equals(imageFile) && !processedImages.contains(otherImage)) {
                        String otherHash = imageDHashes.get(otherImage.toString());
                        if (otherHash != null && calculateHammingDistance(currentHash, otherHash) <= 5) {
                            similarImages.add(otherImage);
                        }
                    }
                }
                
                if (similarImages.size() > 1) {
                    visualDups.put(currentHash, similarImages);
                }
                processedImages.addAll(similarImages);
            }
        }
        
        return visualDups;
    }
    
    private String formatFileSize(long size) {
        return formatFileSizeStatic(size);
    }
    
    private static String formatFileSizeStatic(long size) {
        if (size < 0) return "Unknown size";
        if (size < 1024) return size + " bytes";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }
}