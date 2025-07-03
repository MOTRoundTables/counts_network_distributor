package com.golan;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.*;
import org.geotools.swing.JMapPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import javax.swing.*;
import java.awt.Color;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LinkDistributorUI extends Application {

    // --- UI Components ---
    private TextField inputFileField;
    private TextField outputDirField;
    private TextField epsgField;
    private CheckBox filterRampsCheckbox;
    private TextField rampData1ValuesField;
    private TextField centralityRoadTypesField;
    private CheckBox combineTwoSidedCheckbox;
    private TextArea groupRmseArea;
    private TextArea logArea;
    private TableView<GroupStats> statsTable;
    private Button runButton;
    private ProgressIndicator runProgressIndicator;
    private BorderPane rootPane;
    private ToggleButton themeToggle;

    // --- Map Components ---
    private JMapPane mapPane;
    private MapContent mapContent;

    // --- Theme Resources ---
    private final String lightTheme = getClass().getResource("/light-theme.css").toExternalForm();
    private final String darkTheme = getClass().getResource("/dark-theme.css").toExternalForm();

    private final boolean debugMode = true;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Link Distributor");

        rootPane = new BorderPane();
        rootPane.setPadding(new Insets(15));

        // --- Build UI Sections ---
        rootPane.setTop(createHeaderPane(primaryStage));
        rootPane.setLeft(createParametersPane());
        rootPane.setCenter(createCenterPane());
        rootPane.setBottom(createFooterPane());

        // --- Scene and Stage Setup ---
        Scene scene = new Scene(rootPane); // Create scene without fixed size

        // Set initial theme to light
        setTheme(false, scene);

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(750);
        primaryStage.setMaximized(true); // Start maximized for better use of space
        primaryStage.show();
    }

    // --- UI Builder Methods ---

    private Node createHeaderPane(Stage stage) {
        // Main container for the header area
        BorderPane headerPane = new BorderPane();
        headerPane.setPadding(new Insets(0, 0, 15, 0));

        // --- Input fields grid (left/center part of header) ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        inputFileField = new TextField("E:\\git\\catalog\\counts\\counts\\input\\tlvm\\tlvm.shp");
        outputDirField = new TextField("E:\\git\\catalog\\counts\\counts\\output\\tlvm\\edge\\gui_test_27_06");

        Button browseInputButton = new Button("Browse");
        browseInputButton.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        browseInputButton.setOnAction(e -> browseForInputFile(stage));

        Button browseOutputButton = new Button("Browse");
        browseOutputButton.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        browseOutputButton.setOnAction(e -> browseForOutputDirectory(stage));

        grid.add(new Label("Input Shapefile:"), 0, 0);
        grid.add(inputFileField, 1, 0);
        grid.add(browseInputButton, 2, 0);
        grid.add(new Label("Output Directory:"), 0, 1);
        grid.add(outputDirField, 1, 1);
        grid.add(browseOutputButton, 2, 1);
        GridPane.setHgrow(inputFileField, Priority.ALWAYS);
        GridPane.setHgrow(outputDirField, Priority.ALWAYS);

        headerPane.setCenter(grid);

        // --- Theme Toggle Button (right part of header) ---
        themeToggle = new ToggleButton();
        themeToggle.getStyleClass().add("theme-toggle");
        themeToggle.setOnAction(e -> setTheme(themeToggle.isSelected(), themeToggle.getScene()));

        StackPane toggleContainer = new StackPane(themeToggle);
        toggleContainer.setAlignment(Pos.TOP_RIGHT);
        headerPane.setRight(toggleContainer);

        return headerPane;
    }

    private Node createParametersPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        epsgField = new TextField("2039");
        filterRampsCheckbox = new CheckBox("Filter Ramps");
        filterRampsCheckbox.setSelected(true);
        rampData1ValuesField = new TextField("13, 14, 15");
        centralityRoadTypesField = new TextField("1, 2, 3, 4, 5, 6");
        combineTwoSidedCheckbox = new CheckBox("Combine Two-Sided Links");
        combineTwoSidedCheckbox.setSelected(true);
        groupRmseArea = new TextArea("Group1:0.15\nGroup2:0.20\nGroup3:0.25\nGroup4:0.30\nGroup5:0.30\nGroup6:0.40\nOther:0.0");
        groupRmseArea.setPrefRowCount(6);

        grid.add(new Label("EPSG Code:"), 0, 0);
        grid.add(epsgField, 1, 0);
        grid.add(filterRampsCheckbox, 0, 1, 2, 1);
        grid.add(new Label("Ramp DATA1 Values:"), 0, 2);
        grid.add(rampData1ValuesField, 1, 2);
        grid.add(new Label("Centrality Road Types:"), 0, 3);
        grid.add(centralityRoadTypesField, 1, 3);
        grid.add(combineTwoSidedCheckbox, 0, 4, 2, 1);
        grid.add(new Label("Group RMSE Values:"), 0, 5);
        grid.add(groupRmseArea, 0, 6, 2, 1);

        TitledPane paramsPane = new TitledPane("Parameters", grid);
        paramsPane.setCollapsible(false);
        paramsPane.setMaxWidth(300);
        BorderPane.setMargin(paramsPane, new Insets(0, 15, 0, 0));
        return paramsPane;
    }

    private Node createCenterPane() {
        // --- Top part: Map and Statistics Tabs ---
        mapContent = new MapContent();
        mapPane = new JMapPane(mapContent);
        mapPane.setRenderer(new StreamingRenderer());

        SwingNode swingNode = new SwingNode();
        SwingUtilities.invokeLater(() -> swingNode.setContent(mapPane));

        Tab mapTab = new Tab("Map", swingNode);
        mapTab.setClosable(false);
        mapTab.setGraphic(new FontIcon(FontAwesomeSolid.MAP_MARKED_ALT));

        statsTable = new TableView<>();
        setupStatsTable();
        Tab statsTab = new Tab("Statistics", statsTable);
        statsTab.setClosable(false);
        statsTab.setGraphic(new FontIcon(FontAwesomeSolid.CHART_BAR));

        TabPane mainTabs = new TabPane(mapTab, statsTab);

        // --- Bottom part: Log View ---
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);

        // The ScrollPane wraps the logArea to handle overflow
        ScrollPane logScrollPane = new ScrollPane(logArea);
        logScrollPane.setFitToWidth(true);

        // --- SplitPane to combine them ---
        SplitPane centerSplitPane = new SplitPane();
        centerSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        centerSplitPane.getItems().addAll(mainTabs, logScrollPane);
        centerSplitPane.setDividerPositions(0.8); // Give 80% of space to the map/stats initially

        return centerSplitPane;
    }

    private Node createFooterPane() {
        runButton = new Button("Run Analysis");
        runButton.setMaxWidth(Double.MAX_VALUE);
        runButton.getStyleClass().add("run-button");
        runButton.setGraphic(new FontIcon(FontAwesomeSolid.PLAY_CIRCLE));
        runButton.setOnAction(e -> runAnalysisTask());

        runProgressIndicator = new ProgressIndicator();
        runProgressIndicator.setVisible(false);
        runProgressIndicator.setMaxSize(25, 25);

        HBox runButtonContainer = new HBox(10, runProgressIndicator, runButton);
        runButtonContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(runButton, Priority.ALWAYS);
        runButtonContainer.setPadding(new Insets(15, 0, 0, 0)); // Keep padding consistent

        return runButtonContainer;
    }

    // --- Theme Management ---

    private void setTheme(boolean isDark, Scene scene) {
        scene.getStylesheets().clear();
        if (isDark) {
            scene.getStylesheets().add(darkTheme);
            themeToggle.setGraphic(new FontIcon(FontAwesomeSolid.SUN));
            themeToggle.setSelected(true);
            SwingUtilities.invokeLater(() -> mapPane.setBackground(new Color(43, 43, 43)));
        } else {
            scene.getStylesheets().add(lightTheme);
            themeToggle.setGraphic(new FontIcon(FontAwesomeSolid.MOON));
            themeToggle.setSelected(false);
            SwingUtilities.invokeLater(() -> mapPane.setBackground(new Color(229, 229, 229)));
        }
    }

    // --- Event Handlers and Logic ---

    private void browseForInputFile(Stage owner) {
        // ... (code is unchanged)
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Input Shapefile");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shapefiles", "*.shp"));
        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null) {
            inputFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void browseForOutputDirectory(Stage owner) {
        // ... (code is unchanged)
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");
        File selectedDirectory = directoryChooser.showDialog(owner);
        if (selectedDirectory != null) {
            outputDirField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    private void runAnalysisTask() {
        // ... (code is unchanged)
        logArea.clear();
        logArea.appendText("Preparing analysis...\n");

        String inputFile = inputFileField.getText();
        String outputDir = outputDirField.getText();
        if (inputFile.isEmpty() || outputDir.isEmpty()) {
            logArea.appendText("ERROR: Input file and output directory must be specified.\n");
            return;
        }

        Set<Integer> rampData1Values = new HashSet<>();
        try {
            for (String val : rampData1ValuesField.getText().split(",")) {
                rampData1Values.add(Integer.parseInt(val.trim()));
            }
        } catch (NumberFormatException e) {
            logArea.appendText("ERROR: Invalid Ramp DATA1 values. Please use comma-separated integers.\n");
            return;
        }

        Set<String> centralityRoadTypes = new HashSet<>(Arrays.asList(centralityRoadTypesField.getText().split("\\s*,\\s*")));

        Map<String, Double> groupRmseMap = new HashMap<>();
        try {
            for (String line : groupRmseArea.getText().split("\n")) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(":");
                groupRmseMap.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
            }
        } catch (Exception e) {
            logArea.appendText("ERROR: Invalid Group RMSE values. Use format 'Group:Value' on each line.\n");
            return;
        }

        LinkDistributorLogic logic = new LinkDistributorLogic(
                inputFile, outputDir, epsgField.getText(),
                filterRampsCheckbox.isSelected(), rampData1Values,
                centralityRoadTypes, combineTwoSidedCheckbox.isSelected(),
                groupRmseMap, debugMode, 100
        );

        Task<Void> analysisTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Analysis in progress...");
                String runDateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fullOutputFolder = outputDir + File.separator + runDateTime;
                new File(fullOutputFolder).mkdirs();
                File logFile = new File(fullOutputFolder, "application.log");

                try (FileOutputStream fos = new FileOutputStream(logFile)) {
                    OutputStream teeStream = new LinkDistributorLogic.TeeOutputStream(new TextAreaOutputStream(logArea), fos);
                    logic.setPrintStream(new PrintStream(teeStream, true));
                    logic.run();
                }
                updateMessage("Analysis finished. Loading results...");
                Platform.runLater(() -> displayResults(outputDir));
                return null;
            }
        };

        analysisTask.setOnSucceeded(e -> {
            logArea.appendText("\nAnalysis task completed successfully.\n");
            setUIState(false);
        });

        analysisTask.setOnFailed(e -> {
            logArea.appendText("\n--- ANALYSIS FAILED ---\n");
            Throwable ex = analysisTask.getException();
            ex.printStackTrace(new PrintStream(new TextAreaOutputStream(logArea)));
            setUIState(false);
        });

        setUIState(true);
        new Thread(analysisTask).start();
    }

    private void setUIState(boolean isRunning) {
        runProgressIndicator.setVisible(isRunning);
        runButton.setDisable(isRunning);
        rootPane.getLeft().setDisable(isRunning);
        // Disable only the grid part of the header, not the theme toggle
        ((BorderPane) rootPane.getTop()).getCenter().setDisable(isRunning);
    }

    private void displayResults(String outputDir) {
        log("Attempting to display results from: " + outputDir);
        try {
            File dir = new File(outputDir);
            Optional<File> latestDirOpt = Arrays.stream(dir.listFiles(File::isDirectory))
                    .max(Comparator.comparingLong(File::lastModified));

            if (latestDirOpt.isEmpty()) {
                log("No output subdirectories found in: " + outputDir);
                return;
            }
            File latestDir = latestDirOpt.get();
            log("Latest output directory: " + latestDir.getAbsolutePath());

            Optional<File> shpFileOpt = Arrays.stream(latestDir.listFiles((d, name) -> name.endsWith("_shapefile.shp"))).findFirst();
            if (shpFileOpt.isPresent()) {
                File shapefile = shpFileOpt.get();
                log("Found shapefile: " + shapefile.getAbsolutePath());
                FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);
                SimpleFeatureSource featureSource = store.getFeatureSource();

                // Choose map style based on current theme
                Style style = themeToggle.isSelected() ? createDarkMapStyle() : createLightMapStyle();

                FeatureLayer layer = new FeatureLayer(featureSource, style);

                SwingUtilities.invokeLater(() -> {
                    mapContent.layers().clear();
                    mapContent.addLayer(layer);
                    mapPane.setDisplayArea(mapContent.getMaxBounds());
                    log("Map updated with new layer.");
                });
            } else {
                log("No '*_shapefile.shp' found in the latest output directory.");
            }

            Optional<File> csvFileOpt = Arrays.stream(latestDir.listFiles((d, name) -> name.endsWith("summary.csv"))).findFirst();
            if (csvFileOpt.isPresent()) {
                File summaryCsvFile = csvFileOpt.get();
                log("Found summary CSV: " + summaryCsvFile.getAbsolutePath());
                List<GroupStats> stats = parseSummaryCsv(summaryCsvFile);
                log("Parsed " + stats.size() + " stats entries.");
                Platform.runLater(() -> {
                    statsTable.setItems(FXCollections.observableArrayList(stats));
                    log("Statistics table updated.");
                });
            } else {
                log("No 'summary.csv' found in the latest output directory.");
            }

        } catch (Exception e) {
            log("Error displaying results: " + e.getMessage());
            e.printStackTrace(new PrintStream(new TextAreaOutputStream(logArea)));
        }
    }

    private void setupStatsTable() {
        // ... (code is unchanged)
        statsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<GroupStats, String> groupCol = new TableColumn<>("Group");
        groupCol.setCellValueFactory(new PropertyValueFactory<>("group"));
        TableColumn<GroupStats, Long> nCol = new TableColumn<>("N_g");
        nCol.setCellValueFactory(new PropertyValueFactory<>("N_g"));
        TableColumn<GroupStats, Double> rmseCol = new TableColumn<>("RMSE");
        rmseCol.setCellValueFactory(new PropertyValueFactory<>("rmse"));
        TableColumn<GroupStats, Double> wCol = new TableColumn<>("w_g");
        wCol.setCellValueFactory(new PropertyValueFactory<>("w_g"));
        TableColumn<GroupStats, Integer> ngCol = new TableColumn<>("n_g");
        ngCol.setCellValueFactory(new PropertyValueFactory<>("ng"));
        TableColumn<GroupStats, Double> avgCenCol = new TableColumn<>("Avg Centrality");
        avgCenCol.setCellValueFactory(new PropertyValueFactory<>("avgCentrality"));
        TableColumn<GroupStats, Double> maxCenCol = new TableColumn<>("Max Centrality");
        maxCenCol.setCellValueFactory(new PropertyValueFactory<>("maxCentrality"));
        TableColumn<GroupStats, Double> minCenCol = new TableColumn<>("Min Centrality");
        minCenCol.setCellValueFactory(new PropertyValueFactory<>("minCentrality"));
        statsTable.getColumns().setAll(groupCol, nCol, rmseCol, wCol, ngCol, avgCenCol, maxCenCol, minCenCol);
    }

    private List<GroupStats> parseSummaryCsv(File csvFile) throws IOException {
        // ... (code is unchanged)
        List<GroupStats> statsList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerFound = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("Group,N_g")) {
                    headerFound = true;
                    continue;
                }
                if (!headerFound) continue;

                String[] values = line.split(",");
                if (values.length == 8) {
                    try {
                        statsList.add(new GroupStats(
                            values[0], Long.parseLong(values[1]), Double.parseDouble(values[2]),
                            Double.parseDouble(values[3]), Integer.parseInt(values[4]),
                            Double.parseDouble(values[5]), Double.parseDouble(values[6]),
                            Double.parseDouble(values[7])
                        ));
                    } catch (NumberFormatException e) {
                        log("Skipping malformed number in CSV line: " + line);
                    }
                } else {
                    log("Skipping malformed CSV line (expected 8 values): " + line);
                }
            }
        }
        return statsList;
    }

    // --- Map Styling Methods ---

    private Style createLightMapStyle() {
        StyleFactory sf = CommonFactoryFinder.getStyleFactory();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Map<String, Color> typeColors = new HashMap<>();
        typeColors.put("1", Color.decode("#0033A0")); // Strong Blue
        typeColors.put("2", Color.decode("#D50032")); // Strong Red
        typeColors.put("3", Color.decode("#008751")); // Strong Green
        typeColors.put("4", Color.decode("#F39C12")); // Orange
        typeColors.put("5", Color.decode("#8E44AD")); // Purple
        typeColors.put("6", Color.decode("#009999")); // Teal
        return createStyleFromColors(sf, ff, typeColors);
    }

    private Style createDarkMapStyle() {
        StyleFactory sf = CommonFactoryFinder.getStyleFactory();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Map<String, Color> typeColors = new HashMap<>();
        typeColors.put("1", Color.decode("#17becf")); // Bright Cyan
        typeColors.put("2", Color.decode("#e377c2")); // Bright Pink
        typeColors.put("3", Color.decode("#d62728")); // Bright Red
        typeColors.put("4", Color.decode("#ff7f0e")); // Bright Orange
        typeColors.put("5", Color.decode("#bcbd22")); // Lime Green
        typeColors.put("6", Color.decode("#9467bd")); // Bright Purple
        return createStyleFromColors(sf, ff, typeColors);
    }

    private Style createStyleFromColors(StyleFactory sf, FilterFactory2 ff, Map<String, Color> typeColors) {
        List<Rule> rules = new ArrayList<>();
        for (Map.Entry<String, Color> entry : typeColors.entrySet()) {
            Filter filter = ff.equals(ff.property("TYPE"), ff.literal(entry.getKey()));
            LineSymbolizer symbolizer = sf.createLineSymbolizer(sf.createStroke(ff.literal(entry.getValue()), ff.literal(2.5)), null);
            Rule rule = sf.createRule();
            rule.setFilter(filter);
            rule.symbolizers().add(symbolizer);
            rules.add(rule);
        }

        Color defaultColor = themeToggle.isSelected() ? Color.LIGHT_GRAY : Color.DARK_GRAY;
        LineSymbolizer defaultSymbolizer = sf.createLineSymbolizer(sf.createStroke(ff.literal(defaultColor), ff.literal(1)), null);
        Rule defaultRule = sf.createRule();
        defaultRule.setFilter(Filter.INCLUDE);
        defaultRule.symbolizers().add(defaultSymbolizer);
        rules.add(defaultRule);

        FeatureTypeStyle fts = sf.createFeatureTypeStyle(rules.toArray(new Rule[0]));
        Style style = sf.createStyle();
        style.featureTypeStyles().add(fts);
        return style;
    }

    private void log(String message) {
        if (debugMode) {
            Platform.runLater(() -> logArea.appendText(message + "\n"));
        }
    }

    // --- Data Holder and Main Method ---
    public static class GroupStats {
        // ... (This class remains unchanged)
        private final String group;
        private final long N_g;
        private final double rmse;
        private final double w_g;
        private final int n_g;
        private final double avgCentrality;
        private final double maxCentrality;
        private final double minCentrality;

        public GroupStats(String group, long N_g, double rmse, double w_g, int n_g, double avgCentrality, double maxCentrality, double minCentrality) {
            this.group = group;
            this.N_g = N_g;
            this.rmse = rmse;
            this.w_g = w_g;
            this.n_g = n_g;
            this.avgCentrality = avgCentrality;
            this.maxCentrality = maxCentrality;
            this.minCentrality = minCentrality;
        }

        public String getGroup() { return group; }
        public long getN_g() { return N_g; }
        public double getRmse() { return rmse; }
        public double getW_g() { return w_g; }
        public int getNg() { return n_g; }
        public double getAvgCentrality() { return avgCentrality; }
        public double getMaxCentrality() { return maxCentrality; }
        public double getMinCentrality() { return minCentrality; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}