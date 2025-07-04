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
    private TableView<NetworkConfig> networkConfigTable;
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
    private Label totalCostLabel;
    private Label totalLinksLabel;
    private TabPane mainTabPane;

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
        ScrollPane scrollPane = new ScrollPane(rootPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        Scene scene = new Scene(scrollPane); // Create scene with global scroll

        // Set initial theme to light
        setTheme(false, scene);

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(100);
        primaryStage.setMinHeight(100);
        primaryStage.setMaximized(true); // Start maximized for better use of space
        primaryStage.show();
    }

    // --- UI Builder Methods ---

    private Node createHeaderPane(Stage stage) {
        // Main container for the header area
        BorderPane headerPane = new BorderPane();
        headerPane.setPadding(new Insets(0, 0, 15, 0));

        // --- Input fields grid (left/center part of header) ---
        VBox inputSection = new VBox(10);

        // Network Configuration Table
        networkConfigTable = new TableView<>();
        networkConfigTable.setEditable(true);
        networkConfigTable.setPrefHeight(200); // Make the table more compact

        TableColumn<NetworkConfig, String> netCol = new TableColumn<>("Net");
        netCol.setCellValueFactory(new PropertyValueFactory<>("network"));
        netCol.setPrefWidth(40);
        netCol.setResizable(false);

        TableColumn<NetworkConfig, String> shapefileCol = new TableColumn<>("Shapefile");
        shapefileCol.setCellValueFactory(new PropertyValueFactory<>("shapefilePath"));
        shapefileCol.setPrefWidth(200);
        shapefileCol.setCellFactory(col -> new TableCell<NetworkConfig, String>() {
            private final Button browseButton = new Button("Browse");
            private final TextField textField = new TextField();

            {
                browseButton.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
                browseButton.setOnAction(event -> {
                    NetworkConfig config = (NetworkConfig) getTableRow().getItem();
                    if (config != null) {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Select Shapefile for " + config.getNetwork());
                        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shapefiles", "*.shp"));
                        File selectedFile = fileChooser.showOpenDialog(stage);
                        if (selectedFile != null) {
                            config.setShapefilePath(selectedFile.getAbsolutePath());
                        }
                    }
                });
                textField.textProperty().addListener((obs, oldText, newText) -> {
                    NetworkConfig config = (NetworkConfig) getTableRow().getItem();
                    if (config != null) {
                        config.setShapefilePath(newText);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    textField.setText(item);
                    HBox hbox = new HBox(5, textField, browseButton);
                    HBox.setHgrow(textField, Priority.ALWAYS);
                    setGraphic(hbox);
                }
            }
        });

        TableColumn<NetworkConfig, Double> priceCol = new TableColumn<>("Price (₪)");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        priceCol.setCellFactory(col -> new EditingCellDouble());
        priceCol.setPrefWidth(60);

        TableColumn<NetworkConfig, Double> budgetCol = new TableColumn<>("Budget (₪)");
        budgetCol.setCellValueFactory(new PropertyValueFactory<>("budget"));
        budgetCol.setCellFactory(col -> new EditingCellDouble());
        budgetCol.setPrefWidth(80);

        TableColumn<NetworkConfig, Integer> quotaCol = new TableColumn<>("Quota");
        quotaCol.setCellValueFactory(new PropertyValueFactory<>("quota"));
        quotaCol.setCellFactory(col -> new EditingCellInteger());
        quotaCol.setPrefWidth(50);

        TableColumn<NetworkConfig, Void> settingsCol = new TableColumn<>("⚙︎Settings");
        settingsCol.setCellFactory(col -> new TableCell<NetworkConfig, Void>() {
            private final Button settingsButton = new Button("⚙︎");
            {
                settingsButton.setOnAction(event -> {
                    NetworkConfig config = (NetworkConfig) getTableRow().getItem();
                    if (config != null) {
                        // TODO: Implement per-network settings dialog (UI-2)
                        System.out.println("Settings for " + config.getNetwork());
                        // Select the Parameters tab
                        if (mainTabPane != null) {
                            for (Tab tab : mainTabPane.getTabs()) {
                                if (tab.getText().equals("Parameters")) { // Assuming a tab named "Parameters" exists
                                    mainTabPane.getSelectionModel().select(tab);
                                    break;
                                }
                            }
                        }
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                }
                else {
                    setGraphic(settingsButton);
                }
            }
        });
        settingsCol.setPrefWidth(60);
        settingsCol.setResizable(false);

        TableColumn<NetworkConfig, String> descriptionCol = new TableColumn<>("Description");
        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setPrefWidth(120);
        descriptionCol.setResizable(false);

        networkConfigTable.getColumns().addAll(netCol, shapefileCol, priceCol, budgetCol, quotaCol, settingsCol, descriptionCol);
        VBox.setVgrow(networkConfigTable, Priority.ALWAYS);

        // Populate with initial data
        networkConfigTable.getItems().addAll(
                new NetworkConfig("NAT", "", 2000.0, 200000.0, 120, "National network"),
                new NetworkConfig("TA", "", 1800.0, 180000.0, 100, "Tel-Aviv metropolitan area"),
                new NetworkConfig("JLM", "", 1600.0, 160000.0, 90, "Jerusalem metropolitan area"),
                new NetworkConfig("HFA", "", 1400.0, 140000.0, 80, "Haifa metropolitan area"),
                new NetworkConfig("BSH", "", 1200.0, 120000.0, 70, "Beer-Sheva metropolitan area"),
                new NetworkConfig("NCB", "", null, null, null, "National Count-Basket (no price/budget)"),
                new NetworkConfig("BUF", "", null, null, null, "Survey Buffer (no price/budget)")
        );

        // Add listeners to update calculations dynamically
        networkConfigTable.getItems().forEach(config -> {
            config.priceProperty().addListener((obs, oldVal, newVal) -> updateCalculations());
            config.quotaProperty().addListener((obs, oldVal, newVal) -> updateCalculations());
        });
        

        // Output Directory Field
        GridPane outputGrid = new GridPane();
        outputGrid.setHgap(10);
        outputGrid.setVgap(10);

        outputDirField = new TextField("E:\\git\\catalog\\counts\\counts\\output\\tlvm\\edge\\gui_test_27_06");
        Button browseOutputButton = new Button("Browse");
        browseOutputButton.setGraphic(new FontIcon(FontAwesomeSolid.FOLDER_OPEN));
        browseOutputButton.setOnAction(e -> browseForOutputDirectory(stage));

        outputGrid.add(new Label("Output Base Folder:"), 0, 0);
        outputGrid.add(outputDirField, 1, 0);
        outputGrid.add(browseOutputButton, 2, 0);
        GridPane.setHgrow(outputDirField, Priority.ALWAYS);

        inputSection.getChildren().addAll(networkConfigTable, outputGrid);
        VBox.setVgrow(inputSection, Priority.ALWAYS);
        HBox.setHgrow(inputSection, Priority.ALWAYS);
        headerPane.setCenter(inputSection);

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
        mainTabPane = mainTabs; // Assign to field
        SplitPane.setResizableWithParent(mainTabs, true);

        // --- Bottom part: Log View ---
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);

        // The ScrollPane wraps the logArea to handle overflow
        ScrollPane logScrollPane = new ScrollPane(logArea);
        logScrollPane.setFitToWidth(true);
        logScrollPane.setPrefHeight(150); // Give it a preferred height
        SplitPane.setResizableWithParent(logScrollPane, true);

        // --- SplitPane to combine them ---
        SplitPane centerSplitPane = new SplitPane();
        centerSplitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        centerSplitPane.getItems().addAll(mainTabs, logScrollPane);
        centerSplitPane.setDividerPositions(0.6); // Give 60% of space to the map/stats initially, 40% to logs

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

        totalCostLabel = new Label("Total Estimated Cost: ₪0.00");
        totalLinksLabel = new Label("Total Estimated Links: 0");

        HBox runButtonContainer = new HBox(10, runProgressIndicator, runButton);
        runButtonContainer.setAlignment(Pos.CENTER);
        HBox.setHgrow(runButton, Priority.ALWAYS);

        VBox footerContent = new VBox(5, totalCostLabel, totalLinksLabel, runButtonContainer);
        footerContent.setAlignment(Pos.CENTER_RIGHT);
        footerContent.setPadding(new Insets(15, 0, 0, 0));

        return footerContent;
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

        String outputDir = outputDirField.getText();
        if (outputDir.isEmpty()) {
            logArea.appendText("ERROR: Output directory must be specified.\n");
            return;
        }

        // Validate NAT shapefile presence (mandatory for UI-1 acceptance test)
        String natShapefile = null;
        for (NetworkConfig config : networkConfigTable.getItems()) {
            if (config.getNetwork().equals("NAT")) {
                natShapefile = config.getShapefilePath();
                break;
            }
        }

        if (natShapefile == null || natShapefile.isEmpty()) {
            logArea.appendText("ERROR: NAT network shapefile must be specified.\n");
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

        // Temporarily commented out for UI-1 verification
        // LinkDistributorLogic logic = new LinkDistributorLogic(
        //         inputFile, outputDir, epsgField.getText(),
        //         filterRampsCheckbox.isSelected(), rampData1Values,
        //         centralityRoadTypes, combineTwoSidedCheckbox.isSelected(),
        //         groupRmseMap, debugMode, 100
        // );

        Task<Void> analysisTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Analysis in progress...");
                String runDateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fullOutputFolder = outputDir + File.separator + runDateTime;
                new File(fullOutputFolder).mkdirs();
                File logFile = new File(fullOutputFolder, "application.log");

                try (FileOutputStream fos = new FileOutputStream(logFile)) {
                    // OutputStream teeStream = new LinkDistributorLogic.TeeOutputStream(new TextAreaOutputStream(logArea), fos);
                    // logic.setPrintStream(new PrintStream(teeStream, true));
                    // logic.run();
                    logArea.appendText("Analysis logic temporarily skipped for UI verification.\n");
                }
                updateMessage("Analysis finished. Loading results...");
                // Platform.runLater(() -> displayResults(outputDir)); // Temporarily commented out
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

    private void updateCalculations() {
        double totalCost = 0.0;
        int totalLinks = 0;

        for (NetworkConfig config : networkConfigTable.getItems()) {
            // Only consider networks with a price for cost calculation
            if (config.getPrice() > 0) {
                totalCost += config.getPrice() * config.getQuota();
            }
            totalLinks += config.getQuota();
        }

        totalCostLabel.setText(String.format("Total Estimated Cost: ₪%.2f", totalCost));
        totalLinksLabel.setText(String.format("Total Estimated Links: %d", totalLinks));
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

    // --- Custom Cell Factories for Editable TableView Columns ---
    static class EditingCellDouble extends TableCell<NetworkConfig, Double> {
        private TextField textField;

        public EditingCellDouble() {
        }

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem() != null ? String.valueOf(getItem()) : "");
            setGraphic(null);
        }

        @Override
        public void updateItem(Double item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
            textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    try {
                        commitEdit(Double.parseDouble(textField.getText()));
                    } catch (NumberFormatException e) {
                        cancelEdit();
                    }
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : String.valueOf(getItem());
        }
    }

    static class EditingCellInteger extends TableCell<NetworkConfig, Integer> {
        private TextField textField;

        public EditingCellInteger() {
        }

        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem() != null ? String.valueOf(getItem()) : "");
            setGraphic(null);
        }

        @Override
        public void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
            textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    try {
                        commitEdit(Integer.parseInt(textField.getText()));
                    } catch (NumberFormatException e) {
                        cancelEdit();
                    }
                }
            });
        }

        private String getString() {
            return getItem() == null ? "" : String.valueOf(getItem());
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