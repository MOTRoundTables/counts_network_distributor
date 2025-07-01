package com.golan;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.embed.swing.SwingNode;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.map.FeatureLayer;
import org.geotools.map.MapContent;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.Stroke;
import org.geotools.styling.Rule;
import org.geotools.styling.FeatureTypeStyle;
import org.geotools.styling.LineSymbolizer;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.geotools.factory.CommonFactoryFinder;
import java.awt.Color;
import org.geotools.styling.Style;
import org.geotools.styling.Style;
import org.geotools.styling.Style;
import org.geotools.swing.JMapPane;

import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class LinkDistributorUI extends Application {

    private TextField inputFileField = new TextField("E:\\git\\catalog\\counts\\counts\\input\\tlvm\\tlvm.shp");
    private TextField outputDirField = new TextField("E:\\git\\catalog\\counts\\counts\\output\\tlvm\\edge\\gui_test_27_06");
    private TextArea logArea = new TextArea();
    private JMapPane mapPane;
    private MapContent mapContent;
    private TableView<GroupStats> statsTable = new TableView<>();

    private VBox mainLayout;

    private boolean debugMode = true; // Added for debugging UI
    private int debugPrintLimit = 100; // Added for debugging UI

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Link Distributor");

        mainLayout = new VBox(10);
        mainLayout.setPadding(new javafx.geometry.Insets(10));

        // Input/Output Selection
        GridPane fileSelectionGrid = new GridPane();
        fileSelectionGrid.setHgap(10);
        fileSelectionGrid.setVgap(5);

        fileSelectionGrid.add(new Label("Input Shapefile:"), 0, 0);
        fileSelectionGrid.add(inputFileField, 1, 0);
        Button browseInputButton = new Button("Browse...");
        browseInputButton.setOnAction(e -> browseForInputFile(primaryStage));
        fileSelectionGrid.add(browseInputButton, 2, 0);

        fileSelectionGrid.add(new Label("Output Directory:"), 0, 1);
        fileSelectionGrid.add(outputDirField, 1, 1);
        Button browseOutputButton = new Button("Browse...");
        browseOutputButton.setOnAction(e -> browseForOutputDirectory(primaryStage));
        fileSelectionGrid.add(browseOutputButton, 2, 1);

        // Parameters
        TitledPane paramsPane = new TitledPane("Parameters", createParametersGrid());
        paramsPane.setCollapsible(false);

        // Map View
        mapContent = new MapContent();
        mapPane = new JMapPane(mapContent);
        mapPane.setRenderer(new StreamingRenderer());

        SwingNode swingNode = new SwingNode();
        createSwingContent(swingNode);

        Tab mapTab = new Tab("Map", swingNode);
        mapTab.setClosable(false);

        // Statistics View
        setupStatsTable();
        Tab statsTab = new Tab("Statistics", statsTable);
        statsTab.setClosable(false);

        TabPane mapAndStatsTabs = new TabPane();
        mapAndStatsTabs.getTabs().addAll(mapTab, statsTab);
        VBox.setVgrow(mapAndStatsTabs, Priority.ALWAYS);

        // Log View
        TitledPane logPane = new TitledPane("Log", logArea);
        logArea.setEditable(false);
        logPane.setCollapsible(true);

        // Run Button
        Button runButton = new Button("Run Analysis");
        runButton.setOnAction(e -> runAnalysis());

        mainLayout.getChildren().addAll(fileSelectionGrid, paramsPane, mapAndStatsTabs, logPane, runButton);

        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void createSwingContent(final SwingNode swingNode) {
        SwingUtilities.invokeLater(() -> {
            swingNode.setContent(mapPane);
        });
    }

    private GridPane createParametersGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);

        grid.add(new Label("EPSG Code:"), 0, 0);
        grid.add(new TextField("2039"), 1, 0);

        CheckBox filterRampsCheckbox = new CheckBox("Filter Ramps");
        filterRampsCheckbox.setSelected(true);
        grid.add(filterRampsCheckbox, 0, 1);

        grid.add(new Label("Ramp DATA1 Values:"), 0, 2);
        grid.add(new TextField("13, 14, 15"), 1, 2);

        grid.add(new Label("Centrality Road Types:"), 0, 3);
        grid.add(new TextField("1, 2, 3, 4, 5, 6"), 1, 3);

        CheckBox combineTwoSidedCheckbox = new CheckBox("Combine Two-Sided Links");
        combineTwoSidedCheckbox.setSelected(true);
        grid.add(combineTwoSidedCheckbox, 0, 4);

        grid.add(new Label("Group RMSE Values (group:rmse):"), 0, 5);
        grid.add(new TextArea("Group1:0.15\nGroup2:0.20\nGroup3:0.25\nGroup4:0.30\nGroup5:0.30\nGroup6:0.40\nOther:0.0"), 1, 5);

        return grid;
    }

    private void browseForInputFile(Stage owner) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Input Shapefile");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shapefiles", "*.shp"));
        File selectedFile = fileChooser.showOpenDialog(owner);
        if (selectedFile != null) {
            inputFileField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void browseForOutputDirectory(Stage owner) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");
        File selectedDirectory = directoryChooser.showDialog(owner);
        if (selectedDirectory != null) {
            outputDirField.setText(selectedDirectory.getAbsolutePath());
        }
    }

    private void runAnalysis() {
        logArea.clear();
        logArea.appendText("Starting analysis...\n");

        // Get parameters from UI
        String inputFile = inputFileField.getText();
        String outputDir = outputDirField.getText();
        GridPane paramsGrid = (GridPane) ((TitledPane) mainLayout.getChildren().get(1)).getContent();
        String epsgCode = ((TextField) paramsGrid.getChildren().get(1)).getText();
        boolean filterRamps = ((CheckBox) paramsGrid.getChildren().get(2)).isSelected();
        String rampData1ValuesStr = ((TextField) paramsGrid.getChildren().get(4)).getText();
        String centralityRoadTypesStr = ((TextField) paramsGrid.getChildren().get(6)).getText();
        boolean combineTwoSided = ((CheckBox) paramsGrid.getChildren().get(7)).isSelected();
        String groupRmseValuesStr = ((TextArea) paramsGrid.getChildren().get(9)).getText();

        // Validate inputs
        if (inputFile.isEmpty() || outputDir.isEmpty()) {
            logArea.appendText("Error: Input file and output directory must be specified.\n");
            return;
        }

        Set<Integer> rampData1Values = new HashSet<>();
        try {
            for (String val : rampData1ValuesStr.split(",")) {
                rampData1Values.add(Integer.parseInt(val.trim()));
            }
        } catch (NumberFormatException e) {
            logArea.appendText("Error: Invalid Ramp DATA1 values.\n");
            return;
        }

        Set<String> centralityRoadTypes = new HashSet<>();
        for (String val : centralityRoadTypesStr.split(",")) {
            centralityRoadTypes.add(val.trim());
        }

        Map<String, Double> groupRmseMap = new HashMap<>();
        try {
            for (String line : groupRmseValuesStr.split("\n")) {
                String[] parts = line.split(":");
                groupRmseMap.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
            }
        } catch (Exception e) {
            logArea.appendText("Error: Invalid Group RMSE values.\n");
            return;
        }

        // Create logic instance
        LinkDistributorLogic logic = new LinkDistributorLogic(
                inputFile,
                outputDir,
                epsgCode,
                filterRamps,
                rampData1Values,
                centralityRoadTypes,
                combineTwoSided,
                groupRmseMap,
                true, // debugMode
                100 // debugPrintLimit
        );

        // Redirect output
        TextAreaOutputStream taos = new TextAreaOutputStream(logArea);
        PrintStream ps = new PrintStream(taos, true);
        logic.setPrintStream(ps);

        // Generate the timestamped output folder path
        String runDateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fullOutputFolder = outputDir + File.separator + runDateTime;

        // Ensure output folder exists for the log file
        File outputDirFile = new File(fullOutputFolder);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs(); // Create directories if they don't exist
        }

        File logFile = new File(fullOutputFolder, "application.log");

        // Run in background
        new Thread(() -> {
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                // Create a TeeOutputStream to write to both TextArea and file
                OutputStream teeOutputStream = new LinkDistributorLogic.TeeOutputStream(
                    new TextAreaOutputStream(logArea), // Existing stream for UI log
                    fos // New stream for file log
                );
                PrintStream combinedPs = new PrintStream(teeOutputStream, true); // true for auto-flush

                // Set the combined PrintStream for the logic
                logic.setPrintStream(combinedPs);

                logic.run(); // This is where the main logic runs and prints

                Platform.runLater(() -> {
                    logArea.appendText("Analysis finished successfully.\n");
                    displayResults(outputDir); // Pass original outputDir to find latest subfolder
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    logArea.appendText("An error occurred during analysis:\n");
                    e.printStackTrace(new PrintStream(new TextAreaOutputStream(logArea))); // Print to logArea
                });
            }
        }).start();
    }

    private void displayResults(String outputDir) {
        if (debugMode) {
            logArea.appendText("displayResults called with outputDir: " + outputDir + "\n");
        }
        try {
            // Find the latest output directory
            File dir = new File(outputDir);
            File[] subDirs = dir.listFiles(File::isDirectory);
            if (subDirs == null || subDirs.length == 0) {
                if (debugMode) {
                    logArea.appendText("No subdirectories found in outputDir: " + outputDir + "\n");
                }
                return;
            }
            Arrays.sort(subDirs, Comparator.comparingLong(File::lastModified).reversed());
            File latestDir = subDirs[0];
            if (debugMode) {
                logArea.appendText("Latest output directory: " + latestDir.getAbsolutePath() + "\n");
            }

            // Find the output shapefile
            File[] shpFiles = latestDir.listFiles((d, name) -> name.endsWith("_shapefile.shp"));
            if (shpFiles != null && shpFiles.length > 0) {
                File shapefile = shpFiles[0];
                if (debugMode) {
                    logArea.appendText("Found shapefile: " + shapefile.getAbsolutePath() + "\n");
                }
                FileDataStore store = FileDataStoreFinder.getDataStore(shapefile);
                SimpleFeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0]);

                Style style = createTypeBasedStyle(featureSource);
                FeatureLayer layer = new FeatureLayer(featureSource, style);

                SwingUtilities.invokeLater(() -> {
                    SwingUtilities.invokeLater(() -> {
                    mapContent.layers().clear();
                    mapContent.addLayer(layer);
                    mapPane.repaint();
                    mapPane.setDisplayArea(mapContent.getMaxBounds());
                    if (debugMode) {
                        logArea.appendText("Map updated with shapefile.\n");
                    }
                });
                });
            } else {
                if (debugMode) {
                    logArea.appendText("No shapefile found in latest output directory.\n");
                }
            }

            // Find and parse the summary CSV
            File[] csvFiles = latestDir.listFiles((d, name) -> name.endsWith("summary.csv"));
            if (csvFiles != null && csvFiles.length > 0) {
                File summaryCsvFile = csvFiles[0];
                if (debugMode) {
                    logArea.appendText("Found summary CSV: " + summaryCsvFile.getAbsolutePath() + "\n");
                }
                List<GroupStats> stats = parseSummaryCsv(summaryCsvFile);
                if (debugMode) {
                    logArea.appendText("Parsed " + stats.size() + " stats entries from summary CSV.\n");
                }
                Platform.runLater(() -> {
                    statsTable.setItems(FXCollections.observableArrayList(stats));
                    if (debugMode) {
                        logArea.appendText("Stats table updated.\n");
                    }
                });
            } else {
                if (debugMode) {
                    logArea.appendText("No summary.csv found in latest output directory.\n");
                }
            }
        } catch (Exception e) {
            logArea.appendText("Error displaying results: " + e.getMessage() + "\n");
            e.printStackTrace(new PrintStream(new TextAreaOutputStream(logArea)));
        }
    }

    private void setupStatsTable() {
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

        statsTable.getColumns().addAll(groupCol, nCol, rmseCol, wCol, ngCol, avgCenCol, maxCenCol, minCenCol);
    }

    private List<GroupStats> parseSummaryCsv(File csvFile) throws IOException {
        if (debugMode) {
            logArea.appendText("parseSummaryCsv called for file: " + csvFile.getAbsolutePath() + "\n");
        }
        List<GroupStats> statsList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            // Skip header lines until the actual data header is found
            while ((line = br.readLine()) != null) {
                if (debugMode) {
                    logArea.appendText("Reading line: " + line + "\n");
                }
                if (line.startsWith("Group,N_g")) {
                    if (debugMode) {
                        logArea.appendText("Found data header: " + line + "\n");
                    }
                    break; // Found the header, exit loop
                }
            }
            if (line == null) { // No data header found
                if (debugMode) {
                    logArea.appendText("No data header found in summary CSV.\n");
                }
                return statsList;
            }

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == 8) {
                    try {
                        statsList.add(new GroupStats(
                            values[0],
                            Long.parseLong(values[1]),
                            Double.parseDouble(values[2]),
                            Double.parseDouble(values[3]),
                            Integer.parseInt(values[4]),
                            Double.parseDouble(values[5]),
                            Double.parseDouble(values[6]),
                            Double.parseDouble(values[7])
                        ));
                        if (debugMode) {
                            logArea.appendText("Parsed stats: " + values[0] + ", " + values[1] + ", ...\n");
                        }
                    } catch (NumberFormatException e) {
                        if (debugMode) {
                            logArea.appendText("Error parsing number in line: " + line + ": " + e.getMessage() + "\n");
                        }
                    }
                } else {
                    if (debugMode) {
                        logArea.appendText("Skipping malformed line in summary CSV (expected 8 values, got " + values.length + "): " + line + "\n");
                    }
                }
            }
        }
        return statsList;
    }

    public static class GroupStats {
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
        public int getNg() { return n_g; } // Corrected getter name
        public double getAvgCentrality() { return avgCentrality; }
        public double getMaxCentrality() { return maxCentrality; }
        public double getMinCentrality() { return minCentrality; }
    }

    private Style createTypeBasedStyle(SimpleFeatureSource featureSource) {
        StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
        FilterFactory2 filterFactory = CommonFactoryFinder.getFilterFactory2();

        // Define colors for different link types
        Map<String, Color> typeColors = new HashMap<>();
        typeColors.put("1", Color.BLUE);
        typeColors.put("2", Color.GREEN);
        typeColors.put("3", Color.RED);
        typeColors.put("4", Color.ORANGE);
        typeColors.put("5", Color.MAGENTA);
        typeColors.put("6", Color.CYAN);
        typeColors.put("Other", Color.GRAY); // For types not explicitly listed

        List<Rule> rules = new ArrayList<>();
        for (Map.Entry<String, Color> entry : typeColors.entrySet()) {
            String type = entry.getKey();
            Color color = entry.getValue();

            // Create a filter for the current type
            Filter filter = filterFactory.equal(filterFactory.property("TYPE"), filterFactory.literal(type));

            // Create a line symbolizer with the specified color
            LineSymbolizer lineSymbolizer = styleFactory.createLineSymbolizer();
            lineSymbolizer.setStroke(styleFactory.createStroke(filterFactory.literal(color), filterFactory.literal(2))); // 2 pixels wide

            // Create a rule and add the symbolizer
            Rule rule = styleFactory.createRule();
            rule.setFilter(filter);
            rule.symbolizers().add(lineSymbolizer);
            rules.add(rule);
        }

        // Create a default rule for any other features not caught by the above rules
        LineSymbolizer defaultLineSymbolizer = styleFactory.createLineSymbolizer();
        defaultLineSymbolizer.setStroke(styleFactory.createStroke(filterFactory.literal(Color.BLACK), filterFactory.literal(1)));
        Rule defaultRule = styleFactory.createRule();
        defaultRule.symbolizers().add(defaultLineSymbolizer);
        rules.add(defaultRule);

        FeatureTypeStyle featureTypeStyle = styleFactory.createFeatureTypeStyle(rules.toArray(new Rule[0]));
        Style style = styleFactory.createStyle();
        style.featureTypeStyles().add(featureTypeStyle);

        return style;
    }

    public static void main(String[] args) {
        launch(args);
    }
}