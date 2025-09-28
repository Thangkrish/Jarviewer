import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

// RichTextFX imports for enhanced code view
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// To Run - mvn javafx:run

public class JarViewerFX extends Application {

    private TreeView<String> treeView;
    private CodeArea codeArea; // Replace TextArea with CodeArea
    private TextArea fileContentArea; // Keep this for non-code files
    private AtomicReference<JarFile> currentJarFile = new AtomicReference<>(); // Thread-safe reference to JarFile
    private Label statusBar; // Status bar for feedback
    private TextField searchField;
    private Label fileCountLabel;
    private CheckBox caseSensitiveCheckBox;
    private Stage primaryStage;
    private TabPane contentTabPane;
    private Map<String, Tab> openTabs = new HashMap<>(); // Keep track of open files

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("JavaFX JAR Viewer");

        // Menu Bar with shortcuts
        MenuBar menuBar = createMenuBar();

        // Toolbar with common actions
        ToolBar toolBar = createToolBar();

        // Search Panel
        HBox searchPanel = createSearchPanel();

        // Tree View with Explorer
        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        treeView.setCellFactory(tv -> new TreeCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);

                    // Apply style classes based on file type
                    getStyleClass().removeAll("class-file", "java-file", "xml-file", "jar-file", "folder");

                    if (item.endsWith(".class") || item.contains("🔹")) {
                        getStyleClass().add("class-file");
                    } else if (item.endsWith(".java") || item.contains("☕")) {
                        getStyleClass().add("java-file");
                    } else if (item.endsWith(".xml") || item.contains("🔶")) {
                        getStyleClass().add("xml-file");
                    } else if (item.endsWith(".jar") || item.contains("📦")) {
                        getStyleClass().add("jar-file");
                    } else if (item.contains("📁")) {
                        getStyleClass().add("folder");
                    }
                }
            }
        });

        // Initialize the code area with syntax highlighting capabilities
        codeArea = createCodeArea();

        // Create the code search panel
        HBox codeSearchPanel = createCodeSearchPanel();

        // Fallback content area for non-code files
        fileContentArea = new TextArea();
        fileContentArea.setEditable(false);
        fileContentArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 14px;");

        // Set up mouse click handler for class files
        treeView.setOnMouseClicked(event -> {
            TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                String itemValue = selectedItem.getValue();
                if (itemValue != null && itemValue.contains(".class")) {
                    handleClassFileSelection(selectedItem);
                }
            }
        });

        // Create a tabbed pane for the content
        contentTabPane = new TabPane();

        // Container for code area with search panel
        VBox codeViewContainer = new VBox(5);
        codeViewContainer.setPadding(new Insets(0, 0, 0, 0));

        // Wrap codeArea in VirtualizedScrollPane
        VirtualizedScrollPane<CodeArea> codeScrollPane = new VirtualizedScrollPane<>(codeArea);

        // Add components to container and set growth priorities
        codeViewContainer.getChildren().addAll(codeSearchPanel, codeScrollPane);
        VBox.setVgrow(codeScrollPane, Priority.ALWAYS);

        // Default tabs
        Tab codeTab = new Tab("Code View", codeViewContainer);
        codeTab.setClosable(false);

        Tab plainTextTab = new Tab("Plain Text", fileContentArea);
        plainTextTab.setClosable(false);

        contentTabPane.getTabs().addAll(codeTab, plainTextTab);

        // Package explorer in left side
        VBox leftPanel = new VBox(5);
        Label explorerLabel = new Label("Package Explorer");
        explorerLabel.setPadding(new Insets(5));
        explorerLabel.setMaxWidth(Double.MAX_VALUE);
        explorerLabel.getStyleClass().add("explorer-label");
        leftPanel.getChildren().addAll(explorerLabel, searchPanel, treeView);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // Split pane to adjust views
        SplitPane splitPane = new SplitPane(leftPanel, contentTabPane);
        splitPane.setDividerPositions(0.3);

        // Status bar with file info
        statusBar = new Label("Ready");
        fileCountLabel = new Label("0 files");
        HBox statusBox = new HBox(10);
        statusBox.setPadding(new Insets(5));
        statusBox.getStyleClass().add("status-bar");
        Separator statusSeparator = new Separator(Orientation.VERTICAL);
        statusBox.getChildren().addAll(statusBar, statusSeparator, fileCountLabel);
        HBox.setHgrow(statusBar, Priority.ALWAYS);

        // Main layout
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(menuBar, toolBar);

        BorderPane root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(splitPane);
        root.setBottom(statusBox);

        // Set up the scene
        Scene scene = new Scene(root, 1100, 750);

        // Add custom stylesheet (initial light theme); will be overridden by applyTheme
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize theme explicitly to Light for both Scene and CodeArea
        applyTheme("light");

        // Set up drag-and-drop support for JAR files
        setupDragAndDrop(root);

        // Handle closing the JAR file when app closes
        primaryStage.setOnCloseRequest(e -> {
            closeCurrentJarFile();
        });
    }

    /**
     * Creates a CodeArea with syntax highlighting capabilities
     */
    private CodeArea createCodeArea() {
        CodeArea codeArea = new CodeArea();

        // Set basic properties
        codeArea.setEditable(false);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        // Ensure theme CSS selectors (e.g., .code-area) apply to this control
        codeArea.getStyleClass().add("code-area");
        codeArea.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        // Add auto-indentation support (disabled by default since we're in view mode)
        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB && codeArea.isEditable()) {
                event.consume();
                int caretPosition = codeArea.getCaretPosition();
                codeArea.insertText(caretPosition, "    ");
            }
        });

        return codeArea;
    }

    /**
     * Apply syntax highlighting to the code and show it in the code area
     */
    private void showCodeWithSyntaxHighlighting(String content, String fileExtension) {
        // Clear current content
        codeArea.clear();

        // Show content
        codeArea.replaceText(0, 0, content);

        // Set appropriate tab
        contentTabPane.getSelectionModel().select(0); // Select the code view tab

        // Apply syntax highlighting based on file type
        if (fileExtension.endsWith(".java")) {
            applyJavaSyntaxHighlighting(content);
        } else if (fileExtension.endsWith(".xml") || fileExtension.endsWith(".html")) {
            applyXmlSyntaxHighlighting(content);
        } else if (fileExtension.endsWith(".css")) {
            applyCssSyntaxHighlighting(content);
        } else if (fileExtension.endsWith(".js")) {
            applyJavaScriptSyntaxHighlighting(content);
        }
    }

    /**
     * Regular expression patterns for Java syntax highlighting
     */
    private static final String[] JAVA_KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while"
    };

    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "(?<KEYWORD>\\b(" + String.join("|", JAVA_KEYWORDS) + ")\\b)" +
                    "|(?<STRING>\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\")" +
                    "|(?<NUMBER>\\b\\d+\\b)" +
                    "|(?<COMMENT>//[^\\n]*)|(?<MULTICOMMENT>/\\*[^*]*\\*+(?:[^*/][^*]*\\*+)*/)" +
                    "|(?<ANNOTATION>@[\\w]+)"
    );

    /**
     * Apply Java syntax highlighting to the code area
     */
    private void applyJavaSyntaxHighlighting(String content) {
        codeArea.setStyleSpans(0, computeJavaHighlighting(content));
    }

    private StyleSpans<Collection<String>> computeJavaHighlighting(String text) {
        Matcher matcher = JAVA_PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;
            if (matcher.group("KEYWORD") != null) styleClass = "keyword";
            else if (matcher.group("STRING") != null) styleClass = "string";
            else if (matcher.group("NUMBER") != null) styleClass = "number";
            else if (matcher.group("COMMENT") != null) styleClass = "comment";
            else if (matcher.group("MULTICOMMENT") != null) styleClass = "comment";
            else if (matcher.group("ANNOTATION") != null) styleClass = "annotation";

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Regular expression patterns for XML syntax highlighting
     */
    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT>(</?\\h*)(\\w+)([^<>]*)(\\h*/?>))" +
            "|(?<COMMENT><!--[^<>]+-->)");

    private static final Pattern XML_ATTRIBUTE = Pattern.compile("(\\w+\\h*)(=)(\\h*\"[^\"]+\")");

    /**
     * Apply XML syntax highlighting to the code area
     */
    private void applyXmlSyntaxHighlighting(String content) {
        codeArea.setStyleSpans(0, computeXmlHighlighting(content));
    }

    private StyleSpans<Collection<String>> computeXmlHighlighting(String text) {
        Matcher matcher = XML_TAG.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            if (matcher.group("COMMENT") != null) {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            } else {
                String attributesText = matcher.group("ELEMENT");
                spansBuilder.add(Collections.singleton("tag"), matcher.end() - matcher.start());

                // Handle XML attributes (simplified)
                Matcher attributeMatcher = XML_ATTRIBUTE.matcher(attributesText);
                while (attributeMatcher.find()) {
                    // This is a simplified implementation - in a real app you'd use more precise attribute highlighting
                }
            }
            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    /**
     * Apply CSS syntax highlighting to the code area
     */
    private void applyCssSyntaxHighlighting(String content) {
        // Simplified CSS highlighting - in a full implementation, you would use a proper CSS parser
        Pattern cssPattern = Pattern.compile(
                "(?<SELECTOR>[\\w\\.-]+\\s*\\{)" +
                        "|(?<PROPERTY>[\\w-]+\\s*:)" +
                        "|(?<VALUE>:\\s*[^;]+;)" +
                        "|(?<COMMENT>/\\*[^*]*\\*+(?:[^*/][^*]*\\*+)*/)"
        );

        // Apply highlighting (implementation omitted for brevity)
    }

    /**
     * Apply JavaScript syntax highlighting to the code area
     */
    private void applyJavaScriptSyntaxHighlighting(String content) {
        // Simplified JavaScript highlighting - in a full implementation, you would use a proper JS parser
        String[] jsKeywords = {"var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "break", "continue", "new", "try", "catch", "finally", "throw"};

        Pattern jsPattern = Pattern.compile(
                "(?<KEYWORD>\\b(" + String.join("|", jsKeywords) + ")\\b)" +
                        "|(?<STRING>['\"]([^'\"\\\\]|\\\\.)*['\"])" +
                        "|(?<NUMBER>\\b\\d+\\b)" +
                        "|(?<COMMENT>//[^\\n]*)|(?<MULTICOMMENT>/\\*[^*]*\\*+(?:[^*/][^*]*\\*+)*/)"
        );

        // Apply highlighting (implementation omitted for brevity)
    }

    private void handleTreeItemSelection(TreeItem<String> selectedItem) {
        JarFile currentJar = currentJarFile.get();
        if (currentJar == null) {
            fileContentArea.setText("JAR file is no longer open.");
            return;
        }

        try {
            String path = getFullPath(selectedItem);
            System.out.println("Selected path: " + path);

            JarEntry entry = currentJar.getJarEntry(path);
            if (entry != null && !entry.isDirectory()) {
                System.out.println("File type check: path=" + path);

                if (path.toLowerCase().endsWith(".class")) {
                    // Class files are handled by the mouse click event
                    // This is just for other file selections
                } else {
                    // Handle text files
                    try (InputStream is = currentJar.getInputStream(entry)) {
                        byte[] data = is.readAllBytes();
                        String content = new String(data, StandardCharsets.UTF_8);

                        // Check if this is a code file for syntax highlighting
                        String lowerPath = path.toLowerCase();
                        if (lowerPath.endsWith(".java") || lowerPath.endsWith(".xml") ||
                                lowerPath.endsWith(".html") || lowerPath.endsWith(".css") ||
                                lowerPath.endsWith(".js") || lowerPath.endsWith(".json") ||
                                lowerPath.endsWith(".properties")) {

                            // Show with syntax highlighting in the code area
                            showCodeWithSyntaxHighlighting(content, lowerPath);
                            statusBar.setText("Opened: " + entry.getName() + " (" + data.length + " bytes)");
                        } else {
                            // Show in the plain text area for non-code files
                            fileContentArea.setText(content);
                            contentTabPane.getSelectionModel().select(1); // Select the plain text tab
                            statusBar.setText("Opened: " + entry.getName() + " (" + data.length + " bytes)");
                        }
                    }
                }
            } else if (entry != null && entry.isDirectory()) {
                fileContentArea.setText("Directory: " + path);
                statusBar.setText("Selected directory: " + path);
            }
        } catch (IOException ex) {
            fileContentArea.setText("Error reading file: " + ex.getMessage());
            statusBar.setText("Error reading file");
        }
    }

    private void openJarFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JAR files", "*.jar")
        );
        var file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            openJarFileFromPath(file);
        }
    }

    private void openJarFileFromPath(File file) {
        // Close previous JAR file if open
        closeCurrentJarFile();

        try {
            JarFile jarFile = new JarFile(file);
            // Store the jar file reference
            currentJarFile.set(jarFile);

            TreeItem<String> rootItem = createTreeItem(file.getName(), true);
            rootItem.setExpanded(true);

            // Track the number of files
            int fileCount = 0;

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                addTreePath(rootItem, entry.getName());
                fileCount++;
            }

            treeView.setRoot(rootItem);
            statusBar.setText("Loaded: " + file.getName());
            fileCountLabel.setText(fileCount + " files");

            // Update window title to include JAR name
            primaryStage.setTitle("JavaFX JAR Viewer - " + file.getName());

            // Handle tree selection
            treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.getValue().equals(file.getName())) {
                    handleTreeItemSelection(newVal);
                }
            });
        } catch (IOException ex) {
            showAlert("Error", "Failed to open JAR file: " + ex.getMessage());
            statusBar.setText("Failed to open JAR file");
        }
    }

    private void openJarFileFromDrag(File file) {
        openJarFileFromPath(file);
    }

    private void refreshCurrentJar() {
        JarFile jarFile = currentJarFile.get();
        if (jarFile != null) {
            try {
                String jarPath = jarFile.getName();
                closeCurrentJarFile();
                openJarFileFromPath(new File(jarPath));
                statusBar.setText("Refreshed: " + jarPath);
            } catch (Exception ex) {
                showAlert("Error", "Failed to refresh JAR: " + ex.getMessage());
            }
        }
    }

    private void closeCurrentJarFile() {
        JarFile jarFile = currentJarFile.getAndSet(null);
        try {
            if (jarFile != null) {
                jarFile.close();
            }
        } catch (IOException ignored) {
        }
        // Clear UI elements regardless of whether a jar was open
        treeView.setRoot(null);
        codeArea.clear(); // Also clear the code editor content
        fileContentArea.clear();
        // Reset status/info
        statusBar.setText("Ready");
        fileCountLabel.setText("0 files");
        // Reset window title
        if (primaryStage != null) {
            primaryStage.setTitle("JavaFX JAR Viewer");
        }
        // Ensure first tab (Code View) is selected and empty
        if (contentTabPane != null) {
            contentTabPane.getSelectionModel().select(0);
        }
        // If you later track open file tabs, clear that state too
        openTabs.clear();
    }

    private void expandAllNodes(TreeItem<?> item) {
        if (item != null) {
            item.setExpanded(true);
            for (TreeItem<?> child : item.getChildren()) {
                expandAllNodes(child);
            }
        }
    }

    private void collapseAllNodes(TreeItem<?> item) {
        if (item != null) {
            if (!item.isLeaf() && item != treeView.getRoot()) {
                item.setExpanded(false);
            }
            for (TreeItem<?> child : item.getChildren()) {
                collapseAllNodes(child);
            }
        }
    }

    private void searchInTree(String searchTerm, boolean caseSensitive) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return;
        }

        if (!caseSensitive) {
            searchTerm = searchTerm.toLowerCase();
        }

        TreeItem<String> root = treeView.getRoot();
        if (root == null) {
            return;
        }

        // Clear previous selection
        treeView.getSelectionModel().clearSelection();

        // Search for matches
        List<TreeItem<String>> matches = new ArrayList<>();
        findMatches(root, searchTerm, caseSensitive, matches);

        // Update UI with results
        if (!matches.isEmpty()) {
            // Select and scroll to the first match
            TreeItem<String> firstMatch = matches.get(0);
            treeView.getSelectionModel().select(firstMatch);
            treeView.scrollTo(treeView.getSelectionModel().getSelectedIndex());

            // Expand path to the match
            expandPathTo(firstMatch);

            statusBar.setText("Found " + matches.size() + " matches for '" + searchTerm + "'");
        } else {
            statusBar.setText("No matches found for '" + searchTerm + "'");
        }
    }

    private void findMatches(TreeItem<String> item, String searchTerm, boolean caseSensitive, List<TreeItem<String>> matches) {
        if (item == null) {
            return;
        }

        String itemText = item.getValue();
        if (itemText != null) {
            String compareText = caseSensitive ? itemText : itemText.toLowerCase();
            if (compareText.contains(searchTerm)) {
                matches.add(item);
            }
        }

        for (TreeItem<String> child : item.getChildren()) {
            findMatches(child, searchTerm, caseSensitive, matches);
        }
    }

    private void expandPathTo(TreeItem<?> item) {
        if (item != null) {
            TreeItem<?> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
        }
    }

    private void handleClassFileSelection(TreeItem<String> selectedItem) {
        // Get the current jar file
        JarFile currentJar = currentJarFile.get();
        if (currentJar != null) {
            try {
                // Get the path and find the entry
                String path = getFullPath(selectedItem);
                System.out.println("Looking for JAR entry: " + path);

                // Special handling for path with spaces
                path = path.trim();
                if (path.contains(" ")) {
                    System.out.println("Path contains spaces, removing leading/trailing spaces from segments");
                    String[] parts = path.split("/");
                    StringBuilder cleanPath = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) cleanPath.append("/");
                        cleanPath.append(parts[i].trim());
                    }
                    path = cleanPath.toString();
                    System.out.println("Cleaned path: " + path);
                }

                // Try direct lookup first
                JarEntry entry = currentJar.getJarEntry(path);
                System.out.println("Direct lookup: " + (entry != null ? "Found" : "Not found"));

                // If not found, try common variations of the path
                if (entry == null) {
                    // Try without leading slash
                    if (path.startsWith("/")) {
                        String pathWithoutSlash = path.substring(1);
                        entry = currentJar.getJarEntry(pathWithoutSlash);
                        System.out.println("Without leading slash: " + pathWithoutSlash + " - " + (entry != null ? "Found" : "Not found"));
                    }

                    // Try with package-style path (convert slashes to dots)
                    if (entry == null) {
                        String dotPath = path.replace('/', '.');
                        System.out.println("Trying dot path: " + dotPath);

                        // Try a brute force search if still not found
                        if (entry == null) {
                            System.out.println("Trying brute force search for: " + path);
                            String simpleName = path;
                            if (path.contains("/")) {
                                simpleName = path.substring(path.lastIndexOf('/') + 1);
                            }
                            System.out.println("Simple name to search: " + simpleName);

                            // First list out all entries for debugging
                            System.out.println("Available entries in JAR:");
                            List<JarEntry> classEntries = new ArrayList<>();
                            for (Enumeration<JarEntry> entries = currentJar.entries(); entries.hasMoreElements(); ) {
                                JarEntry e = entries.nextElement();
                                if (e.getName().endsWith(".class")) {
                                    classEntries.add(e);
                                    System.out.println(" - " + e.getName());
                                }
                            }

                            // Now try to find matches
                            for (JarEntry e : classEntries) {
                                String entryName = e.getName();

                                // Check if entry name ends with our simple name
                                if (entryName.endsWith(simpleName)) {
                                    entry = e;
                                    System.out.println("Found match by simple name: " + entryName);
                                    break;
                                }

                                // Also check using just the class name part (without .class extension)
                                String simpleNameWithoutExt = simpleName;
                                if (simpleName.endsWith(".class")) {
                                    simpleNameWithoutExt = simpleName.substring(0, simpleName.length() - 6);
                                }

                                String entrySimpleName = entryName;
                                if (entryName.contains("/")) {
                                    entrySimpleName = entryName.substring(entryName.lastIndexOf('/') + 1);
                                }
                                if (entrySimpleName.endsWith(".class")) {
                                    entrySimpleName = entrySimpleName.substring(0, entrySimpleName.length() - 6);
                                }

                                if (entrySimpleName.equals(simpleNameWithoutExt)) {
                                    entry = e;
                                    System.out.println("Found match by simple class name: " + entryName);
                                    break;
                                }
                            }
                        }
                    }
                }

                if (entry != null && !entry.isDirectory()) {
                    System.out.println("Found class entry, calling decompiler: " + entry.getName());
                    fileContentArea.setText("Processing class file: " + entry.getName());
                    decompileAndShowClassFile(currentJar, entry);
                } else {
                    System.out.println("Class entry not found for path: " + path);

                    // Show a more helpful error with potential alternatives
                    StringBuilder errorMsg = new StringBuilder();
                    errorMsg.append("Could not find class file entry: ").append(path).append("\n\n");
                    errorMsg.append("Available similar class files:\n");

                    // Extract the class name part for comparison
                    String className = path;
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        className = path.substring(lastSlash + 1);
                    }
                    // Remove .class extension for comparison
                    if (className.endsWith(".class")) {
                        className = className.substring(0, className.length() - 6);
                    }

                    // Find similar class files to suggest
                    int count = 0;
                    for (Enumeration<JarEntry> entries = currentJar.entries(); entries.hasMoreElements() && count < 20; ) {
                        JarEntry e = entries.nextElement();
                        if (e.getName().endsWith(".class") && !e.isDirectory()) {
                            String entryName = e.getName();

                            // Extract just the class name part
                            String entryClassName = entryName;
                            int entryLastSlash = entryName.lastIndexOf('/');
                            if (entryLastSlash >= 0) {
                                entryClassName = entryName.substring(entryLastSlash + 1);
                            }
                            // Remove .class extension
                            if (entryClassName.endsWith(".class")) {
                                entryClassName = entryClassName.substring(0, entryClassName.length() - 6);
                            }

                            // Check for similarity
                            if (entryClassName.contains(className) ||
                                    className.contains(entryClassName) ||
                                    (className.length() > 3 && entryName.contains(className))) {
                                errorMsg.append("- ").append(e.getName()).append("\n");
                                count++;
                            }
                        }
                    }

                    if (count == 0) {
                        // If no similar classes found, just show some examples
                        errorMsg.append("No similar class files found. Here are some examples from the JAR:\n");
                        for (Enumeration<JarEntry> entries = currentJar.entries(); entries.hasMoreElements() && count < 10; ) {
                            JarEntry e = entries.nextElement();
                            if (e.getName().endsWith(".class")) {
                                errorMsg.append("- ").append(e.getName()).append("\n");
                                count++;
                            }
                        }
                    }

                    fileContentArea.setText(errorMsg.toString());
                }
            } catch (Exception ex) {
                System.err.println("Error handling class file: " + ex.getMessage());
                ex.printStackTrace();
                fileContentArea.setText("Error handling class file: " + ex.getMessage() + "\n\n" + getStackTraceAsString(ex));
            }
        } else {
            System.out.println("No JAR file is currently open");
            fileContentArea.setText("No JAR file is currently open");
        }
    }

    private void decompileAndShowClassFile(JarFile jarFile, JarEntry entry) {
        // Immediately show a message that we're processing
        fileContentArea.setText("Processing class file: " + entry.getName() + "...");

        try {
            // Create a temporary directory for our class file
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File tempClassFile = new File(tempDir, "temp_" + System.currentTimeMillis() + ".class");
            tempClassFile.deleteOnExit();

            // Extract the class file
            try (InputStream is = jarFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(tempClassFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.flush();
            }

            // First, try using CFR
            StringBuilder result = new StringBuilder();

            // Basic class file info
            result.append("// Class File: ").append(entry.getName()).append("\n");
            result.append("// Size: ").append(tempClassFile.length()).append(" bytes\n\n");

            // Try running the CFR decompiler as an external process
            File cfrJar = new File("lib/cfr-0.152.jar");
            if (cfrJar.exists()) {
                // Run CFR as an external Java process
                try {
                    // Build the command
                    List<String> command = new ArrayList<>();
                    command.add("java");
                    command.add("-jar");
                    command.add(cfrJar.getAbsolutePath());
                    command.add(tempClassFile.getAbsolutePath());

                    // Execute the process
                    ProcessBuilder processBuilder = new ProcessBuilder(command);
                    processBuilder.redirectErrorStream(true); // Merge stderr with stdout

                    Process process = processBuilder.start();

                    // Capture output
                    StringBuilder output = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line).append("\n");
                        }
                    }

                    // Wait for process to complete
                    int exitCode = process.waitFor();

                    if (exitCode == 0 && output.length() > 0) {
                        // Decompilation successful - use the code area with syntax highlighting
                        String decompiled = output.toString();

                        // Use our enhanced code view with syntax highlighting
                        showCodeWithSyntaxHighlighting(decompiled, ".java");
                        statusBar.setText("Decompiled: " + entry.getName() + " (" + tempClassFile.length() + " bytes)");
                        return;
                    } else {
                        // Failed to decompile with CFR
                        result.append("// CFR decompilation failed (exit code ").append(exitCode).append(")\n");
                        result.append("// Output: ").append(output.length() > 0 ? output.toString() : "No output").append("\n\n");
                    }
                } catch (Exception e) {
                    result.append("// Error running CFR: ").append(e.getMessage()).append("\n\n");
                }
            } else {
                result.append("// CFR decompiler not found at: ").append(cfrJar.getAbsolutePath()).append("\n");
                result.append("// To enable decompilation, place CFR jar in the lib directory.\n\n");
            }

            // If we got here, CFR failed or isn't available - show basic class info

            // Read basic class file information
            try (DataInputStream dis = new DataInputStream(new FileInputStream(tempClassFile))) {
                // Check magic number
                int magic = dis.readInt();
                if (magic == 0xCAFEBABE) {
                    // Read version info
                    int minor = dis.readUnsignedShort();
                    int major = dis.readUnsignedShort();
                    result.append("// Java Class Version: ").append(major).append(".").append(minor).append("\n\n");
                } else {
                    result.append("// Not a valid class file (invalid magic number)\n\n");
                }
            } catch (IOException e) {
                result.append("// Error reading class file: ").append(e.getMessage()).append("\n\n");
            }

            // Display hex dump of the class file
            result.append("// Hex dump of class file:\n");
            try (RandomAccessFile raf = new RandomAccessFile(tempClassFile, "r")) {
                byte[] buffer = new byte[16];
                long offset = 0;
                int bytesRead;

                while ((bytesRead = raf.read(buffer)) != -1) {
                    // Print offset
                    result.append(String.format("\n// %08X: ", offset));

                    // Print hex values
                    for (int i = 0; i < bytesRead; i++) {
                        result.append(String.format("%02X ", buffer[i] & 0xFF));
                    }

                    // Padding for incomplete lines
                    for (int i = bytesRead; i < 16; i++) {
                        result.append("   ");
                    }

                    // Print ASCII representation
                    result.append(" | ");
                    for (int i = 0; i < bytesRead; i++) {
                        char c = (char) (buffer[i] & 0xFF);
                        result.append(c >= 32 && c < 127 ? c : '.');
                    }

                    offset += bytesRead;
                }
            } catch (IOException e) {
                result.append("\n// Error reading file for hex dump: ").append(e.getMessage());
            }

            // Update the UI - use plain text view for this
            fileContentArea.setText(result.toString());
            contentTabPane.getSelectionModel().select(1); // Select the plain text tab
            statusBar.setText("Class info: " + entry.getName() + " (" + tempClassFile.length() + " bytes)");

        } catch (Exception e) {
            // Show any errors that occur
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            fileContentArea.setText("Error processing class file:\n" + e.getMessage() + "\n\n" + sw.toString());
            contentTabPane.getSelectionModel().select(1); // Select the plain text tab
            statusBar.setText("Error: " + entry.getName());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();

        // File menu
        Menu fileMenu = new Menu("File");
        MenuItem openMenuItem = new MenuItem("Open JAR...");
        openMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        openMenuItem.setOnAction(e -> openJarFile(primaryStage));

        MenuItem refreshMenuItem = new MenuItem("Refresh");
        refreshMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
        refreshMenuItem.setOnAction(e -> refreshCurrentJar());

        MenuItem closeMenuItem = new MenuItem("Close");
        closeMenuItem.setOnAction(e -> closeCurrentJarFile());

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem exitMenuItem = new MenuItem("Exit");
        exitMenuItem.setOnAction(e -> primaryStage.close());

        fileMenu.getItems().addAll(openMenuItem, refreshMenuItem, closeMenuItem, separator, exitMenuItem);

        // View menu
        Menu viewMenu = new Menu("View");
        CheckMenuItem showLineNumbersItem = new CheckMenuItem("Show Line Numbers");
        showLineNumbersItem.setSelected(true);
        showLineNumbersItem.setOnAction(e -> {
            if (showLineNumbersItem.isSelected()) {
                codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
            } else {
                codeArea.setParagraphGraphicFactory(null);
            }
        });

        CheckMenuItem wrapTextItem = new CheckMenuItem("Word Wrap");
        wrapTextItem.setSelected(false);
        wrapTextItem.setOnAction(e -> {
            fileContentArea.setWrapText(wrapTextItem.isSelected());
            // Note: CodeArea doesn't support direct word wrap, would need more complex handling
        });

        // Theme submenu
        Menu themeMenu = new Menu("Theme");
        ToggleGroup themeGroup = new ToggleGroup();

        RadioMenuItem lightTheme = new RadioMenuItem("Light");
        lightTheme.setToggleGroup(themeGroup);
        lightTheme.setSelected(true);
        lightTheme.setOnAction(e -> applyTheme("light"));

        RadioMenuItem nightTheme = new RadioMenuItem("Night");
        nightTheme.setToggleGroup(themeGroup);
        nightTheme.setOnAction(e -> applyTheme("night"));

        themeMenu.getItems().addAll(lightTheme, nightTheme);

        viewMenu.getItems().addAll(showLineNumbersItem, wrapTextItem, new SeparatorMenuItem(), themeMenu);

        // Help menu
        Menu helpMenu = new Menu("Help");
        MenuItem aboutMenuItem = new MenuItem("About");
        aboutMenuItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().add(aboutMenuItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);
        return menuBar;
    }

    /**
     * Creates the toolbar with common actions
     */
    private ToolBar createToolBar() {
        ToolBar toolBar = new ToolBar();

        // Open button
        Button openButton = new Button("Open JAR");
        openButton.setOnAction(e -> openJarFile(primaryStage));

        // Refresh button
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshCurrentJar());

        // Close button (new) placed next to Refresh
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> closeCurrentJarFile());

        // Expand all button
        Button expandAllButton = new Button("Expand All");
        expandAllButton.setOnAction(e -> expandAllNodes(treeView.getRoot()));

        // Collapse all button
        Button collapseAllButton = new Button("Collapse All");
        collapseAllButton.setOnAction(e -> collapseAllNodes(treeView.getRoot()));

        // Code view enhancement buttons
        Button zoomInButton = new Button("+");
        zoomInButton.setTooltip(new Tooltip("Increase font size"));
        zoomInButton.setOnAction(e -> adjustFontSize(1));

        Button zoomOutButton = new Button("-");
        zoomOutButton.setTooltip(new Tooltip("Decrease font size"));
        zoomOutButton.setOnAction(e -> adjustFontSize(-1));

        toolBar.getItems().addAll(
                openButton,
                refreshButton,
                closeButton,
                new Separator(Orientation.VERTICAL),
                expandAllButton,
                collapseAllButton,
                new Separator(Orientation.VERTICAL),
                zoomInButton,
                zoomOutButton
        );

        return toolBar;
    }

    private HBox createSearchPanel() {
        HBox searchPanel = new HBox(5);
        searchPanel.setPadding(new Insets(5));
        searchPanel.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search in files...");

        Button searchButton = new Button("Search");

        caseSensitiveCheckBox = new CheckBox("Case Sensitive");

        // Track search state for package explorer
        final List<TreeItem<String>> treeMatches = new ArrayList<>();
        final int[] treeMatchIndex = { -1 };
        final String[] lastTreeSearchTerm = { "" };
        final boolean[] lastTreeCase = { false };

        Runnable performTreeSearch = () -> {
            String term = searchField.getText();
            if (term == null || term.trim().isEmpty()) {
                treeMatches.clear();
                treeMatchIndex[0] = -1;
                lastTreeSearchTerm[0] = "";
                treeView.getSelectionModel().clearSelection();
                statusBar.setText("Enter a term to search");
                return;
            }

            boolean cs = caseSensitiveCheckBox.isSelected();
            treeMatches.clear();
            treeMatchIndex[0] = -1;

            TreeItem<String> root = treeView.getRoot();
            if (root == null) {
                statusBar.setText("No JAR loaded");
                return;
            }

            // Collect matches
            List<TreeItem<String>> found = new ArrayList<>();
            findMatches(root, cs ? term : term.toLowerCase(), cs, found);
            treeMatches.addAll(found);

            if (!treeMatches.isEmpty()) {
                treeMatchIndex[0] = 0;
                TreeItem<String> first = treeMatches.get(0);
                treeView.getSelectionModel().select(first);
                treeView.scrollTo(treeView.getSelectionModel().getSelectedIndex());
                expandPathTo(first);
                statusBar.setText("1/" + treeMatches.size() + " matches for '" + term + "'");
            } else {
                statusBar.setText("No matches found for '" + term + "'");
            }

            lastTreeSearchTerm[0] = term;
            lastTreeCase[0] = cs;
        };

        // Enter: next match; Shift+Enter: previous match. Recompute if term/case changed.
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String term = searchField.getText();
                boolean cs = caseSensitiveCheckBox.isSelected();
                if (term != null && !term.trim().isEmpty() &&
                        term.equals(lastTreeSearchTerm[0]) && cs == lastTreeCase[0] &&
                        !treeMatches.isEmpty()) {
                    int size = treeMatches.size();
                    treeMatchIndex[0] = (treeMatchIndex[0] + (e.isShiftDown() ? -1 : 1) + size) % size;
                    TreeItem<String> item = treeMatches.get(treeMatchIndex[0]);
                    treeView.getSelectionModel().select(item);
                    treeView.scrollTo(treeView.getSelectionModel().getSelectedIndex());
                    expandPathTo(item);
                    statusBar.setText((treeMatchIndex[0] + 1) + "/" + size + " matches for '" + term + "'");
                } else {
                    performTreeSearch.run();
                }
            }
        });

        // Search button recomputes matches from start
        searchButton.setOnAction(e -> performTreeSearch.run());

        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchPanel.getChildren().addAll(searchField, searchButton, caseSensitiveCheckBox);

        return searchPanel;
    }

    private HBox createCodeSearchPanel() {
        HBox codeSearchPanel = new HBox(5);
        codeSearchPanel.setPadding(new Insets(5));
        codeSearchPanel.setAlignment(Pos.CENTER_LEFT);

        // Add search field for code
        TextField codeSearchField = new TextField();
        codeSearchField.setPromptText("Search in code...");
        HBox.setHgrow(codeSearchField, Priority.ALWAYS);

        // Case sensitive checkbox for code search
        CheckBox codeCaseSensitiveCheckBox = new CheckBox("Case Sensitive");

        // Search button
        Button codeSearchButton = new Button("Search");

        // Next/Previous buttons for navigation between search results
        Button prevMatchButton = new Button("↑");
        prevMatchButton.setTooltip(new Tooltip("Previous match"));
        Button nextMatchButton = new Button("↓");
        nextMatchButton.setTooltip(new Tooltip("Next match"));

        // Add search matches counter
        Label matchCountLabel = new Label("0/0");
        matchCountLabel.setPadding(new Insets(0, 5, 0, 5));

        // Store references to current search state
        final List<Integer> searchMatches = new ArrayList<>();
        final int[] currentMatchIndex = {-1};
        final String[] lastSearchTerm = {""};
        final boolean[] lastCaseSensitive = {false};

        // Handler for search action
        Runnable performSearch = () -> {
            String searchTerm = codeSearchField.getText();
            if (!searchTerm.isEmpty()) {
                boolean caseSensitive = codeCaseSensitiveCheckBox.isSelected();
                searchMatches.clear();
                currentMatchIndex[0] = -1;

                // Find all occurrences and store their positions
                String text = codeArea.getText();
                String searchText = caseSensitive ? text : text.toLowerCase();
                String searchFor = caseSensitive ? searchTerm : searchTerm.toLowerCase();

                int index = 0;
                while ((index = searchText.indexOf(searchFor, index)) != -1) {
                    searchMatches.add(index);
                    index += searchFor.length();
                }

                // Apply highlights to all matches
                highlightCodeSearchMatches(searchTerm, searchMatches, caseSensitive);

                // Update counter
                matchCountLabel.setText(searchMatches.isEmpty() ? "0/0" :
                    "0/" + searchMatches.size());

                // Navigate to first match if any found
                if (!searchMatches.isEmpty()) {
                    currentMatchIndex[0] = 0;
                    navigateToMatch(searchMatches.get(0), searchTerm.length());
                    matchCountLabel.setText("1/" + searchMatches.size());
                }

                // Remember last search settings
                lastSearchTerm[0] = searchTerm;
                lastCaseSensitive[0] = caseSensitive;
            } else {
                // Clear highlights if search field is empty
                codeArea.clearStyle(0, codeArea.getLength());
                matchCountLabel.setText("0/0");

                // Reset last search settings
                lastSearchTerm[0] = "";
            }
        };

        // Search on Enter key: advance to next/previous if same term/case; otherwise perform a fresh search
        codeSearchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String term = codeSearchField.getText();
                boolean cs = codeCaseSensitiveCheckBox.isSelected();
                if (!term.isEmpty() &&
                        term.equals(lastSearchTerm[0]) &&
                        cs == lastCaseSensitive[0] &&
                        !searchMatches.isEmpty()) {
                    // Navigate to next/previous match
                    if (e.isShiftDown()) {
                        currentMatchIndex[0] = (currentMatchIndex[0] - 1 + searchMatches.size()) % searchMatches.size();
                    } else {
                        currentMatchIndex[0] = (currentMatchIndex[0] + 1) % searchMatches.size();
                    }
                    navigateToMatch(searchMatches.get(currentMatchIndex[0]), term.length());
                    matchCountLabel.setText((currentMatchIndex[0] + 1) + "/" + searchMatches.size());
                } else {
                    // New term or case toggle: recompute
                    performSearch.run();
                }
            }
        });

        // Search on button click (recompute)
        codeSearchButton.setOnAction(e -> performSearch.run());

        // Next match button handler
        nextMatchButton.setOnAction(e -> {
            if (!searchMatches.isEmpty()) {
                currentMatchIndex[0] = (currentMatchIndex[0] + 1) % searchMatches.size();
                navigateToMatch(searchMatches.get(currentMatchIndex[0]), codeSearchField.getText().length());
                matchCountLabel.setText((currentMatchIndex[0] + 1) + "/" + searchMatches.size());
            }
        });

        // Previous match button handler
        prevMatchButton.setOnAction(e -> {
            if (!searchMatches.isEmpty()) {
                currentMatchIndex[0] = (currentMatchIndex[0] - 1 + searchMatches.size()) % searchMatches.size();
                navigateToMatch(searchMatches.get(currentMatchIndex[0]), codeSearchField.getText().length());
                matchCountLabel.setText((currentMatchIndex[0] + 1) + "/" + searchMatches.size());
            }
        });

        // Organize components in the search panel
        codeSearchPanel.getChildren().addAll(
            codeSearchField,
            codeCaseSensitiveCheckBox,
            codeSearchButton,
            new Separator(Orientation.VERTICAL),
            prevMatchButton,
            matchCountLabel,
            nextMatchButton
        );

        return codeSearchPanel;
    }

    private void navigateToMatch(int position, int length) {
        // Move caret to position and select the match
        codeArea.moveTo(position);
        codeArea.requestFollowCaret();
        codeArea.selectRange(position, position + length);

        // Apply a stronger highlight to the current selection using a style class
        codeArea.setStyleClass(position, position + length, "search-highlight-current");
    }

    private void highlightCodeSearchMatches(String searchTerm, List<Integer> matches, boolean caseSensitive) {
        // Clear previous highlights by reapplying existing styles (syntax highlighting)
        StyleSpans<Collection<String>> currentStyles = codeArea.getStyleSpans(0, codeArea.getLength());
        codeArea.setStyleSpans(0, currentStyles);

        // If the search term is empty, return
        if (searchTerm.isEmpty()) {
            return;
        }

        // Apply a highlight style class to all matches
        for (Integer position : matches) {
            codeArea.setStyleClass(position, position + searchTerm.length(), "search-highlight");
        }

        statusBar.setText("Found " + matches.size() + " matches for '" + searchTerm + "'");
    }

    private void setupDragAndDrop(BorderPane root) {
        root.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        root.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                for (File file : db.getFiles()) {
                    if (file.getName().toLowerCase().endsWith(".jar")) {
                        openJarFileFromDrag(file);
                        success = true;
                        break;
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private String getFullPath(TreeItem<String> item) {
        // Extract actual name without the prefix icon
        String name = item.getValue();
        if (name.startsWith("📁 ") || name.startsWith("📄 ") ||
                name.startsWith("🔹 ") || name.startsWith("📦 ") ||
                name.startsWith("🔶 ") || name.startsWith("☕ ")) {
            name = name.substring(2); // Remove icon prefix
        }

        StringBuilder path = new StringBuilder(name);
        TreeItem<String> parent = item.getParent();
        while (parent != null && parent.getParent() != null) {
            String parentName = parent.getValue();
            if (parentName.startsWith("📁 ") || parentName.startsWith("📄 ") ||
                    parentName.startsWith("🔹 ") || parentName.startsWith("📦 ") ||
                    parentName.startsWith("🔶 ") || parentName.startsWith("☕ ")) {
                parentName = parentName.substring(2); // Remove icon prefix
            }
            path.insert(0, parentName + "/");
            parent = parent.getParent();
        }

        // Remove trailing slash if present
        if (path.length() > 0 && path.charAt(path.length() - 1) == '/') {
            path.setLength(path.length() - 1);
        }

        return path.toString();
    }

    private void addTreePath(TreeItem<String> parent, String path) {
        String[] parts = path.split("/");
        TreeItem<String> currentItem = parent;

        for (String part : parts) {
            if (part.isEmpty()) continue; // Skip empty parts (in case of leading slash)

            // Check if the child already exists
            Optional<TreeItem<String>> existingChild = currentItem.getChildren().stream()
                    .filter(child -> child.getValue().equals(part))
                    .findFirst();

            if (existingChild.isPresent()) {
                // Child exists, move to this child
                currentItem = existingChild.get();
            } else {
                // Child doesn't exist, create a new one
                TreeItem<String> newItem = new TreeItem<>(part);
                currentItem.getChildren().add(newItem);
                currentItem = newItem;
            }
        }
    }

    private TreeItem<String> createTreeItem(String name, boolean isRoot) {
        TreeItem<String> item = new TreeItem<>(name);
        if (!isRoot) {
            item.setExpanded(true);
        }
        return item;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.show();
    }

    private void adjustFontSize(int delta) {
        // Get current font size from the code area's style
        String style = codeArea.getStyle();
        double currentSize = 14.0; // Default size

        // Try to extract the current font size from the style string
        if (style != null && !style.isEmpty()) {
            int fontSizeIndex = style.indexOf("-fx-font-size:");
            if (fontSizeIndex >= 0) {
                int valueStart = fontSizeIndex + "-fx-font-size:".length();
                int valueEnd = style.indexOf("px", valueStart);
                if (valueEnd >= 0) {
                    try {
                        currentSize = Double.parseDouble(style.substring(valueStart, valueEnd).trim());
                    } catch (NumberFormatException e) {
                        // Use default if parsing fails
                    }
                }
            }
        }

        // Calculate new size (min 8, max 36)
        double newSize = Math.min(Math.max(currentSize + delta, 8.0), 36.0);

        // Apply new size to code area and text area
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: " + newSize + "px;");
        fileContentArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: " + newSize + "px;");
    }

    private String getStackTraceAsString(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Apply UI theme by swapping stylesheets for Scene and CodeArea.
     * @param theme "light" or "dark"
     */
    private void applyTheme(String theme) {
        String sheet = "/style.css";
        System.out.println(theme);
        boolean isNight = "night".equalsIgnoreCase(theme);
        boolean isDark = isNight || "dark".equalsIgnoreCase(theme);
        if (isDark) {
            sheet = "/dark.css";
        }
        // Update Scene stylesheet
        if (primaryStage != null && primaryStage.getScene() != null) {
            List<String> styles = primaryStage.getScene().getStylesheets();
            styles.clear();
            URL sceneCss = getClass().getResource(sheet);
            if (sceneCss != null) {
              //  styles.add(sceneCss.toExternalForm());
            }
        }
        // Update CodeArea stylesheet to keep it in sync
        if (codeArea != null) {
            codeArea.getStylesheets().clear();
            URL codeCss = getClass().getResource(sheet);
            if (codeCss != null) {
                codeArea.getStylesheets().add(codeCss.toExternalForm());
            }
        }
        // Optional: update status text
        if (statusBar != null) {
            String label = isNight ? "Night" : (isDark ? "Dark" : "Light");
            statusBar.setText(label + " theme applied");
        }
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("JavaFX JAR Viewer");

        // Apply current theme styles to dialog
        if (primaryStage != null && primaryStage.getScene() != null) {
            alert.getDialogPane().getStylesheets().setAll(primaryStage.getScene().getStylesheets());
        }
        alert.getDialogPane().getStyleClass().add("about-dialog");

        // Small icon for header
        Label icon = new Label("ℹ️");
        icon.setStyle("-fx-font-size: 28px;");
        alert.getDialogPane().setGraphic(icon);

        // Structured content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(5, 0, 0, 0));

        Label title = new Label("JavaFX JAR Viewer");
        title.getStyleClass().add("about-title");
        Label version = new Label("Version 1.0");
        Label desc = new Label("An enhanced UI for viewing JAR file contents");

        Label devLabel = new Label("Developed by:");
        devLabel.getStyleClass().add("info-label");
        Label devValue = new Label("Thangkrish");

        Label linkLabel = new Label("LinkedIn:");
        linkLabel.getStyleClass().add("info-label");
        Hyperlink link = new Hyperlink("https://www.linkedin.com/in/thangkrish");
        link.setOnAction(ev -> getHostServices().showDocument("https://www.linkedin.com/in/thangkrish"));
        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(ev -> {
            ClipboardContent content = new ClipboardContent();
            content.putString("https://www.linkedin.com/in/thangkrish");
            Clipboard.getSystemClipboard().setContent(content);
            if (statusBar != null) statusBar.setText("LinkedIn URL copied");
        });
        HBox linkBox = new HBox(6, link, copyBtn);

        int row = 0;
        grid.add(title, 0, row++, 2, 1);
        grid.add(version, 0, row++, 2, 1);
        grid.add(desc, 0, row++, 2, 1);
        grid.add(devLabel, 0, row);
        grid.add(devValue, 1, row++);
        grid.add(linkLabel, 0, row);
        grid.add(linkBox, 1, row);

        alert.getDialogPane().setContent(grid);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.showAndWait();
        if (statusBar != null) {
            statusBar.setText("About dialog shown");
        }
    }
}
