package com.golan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

// GeoTools imports
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureWriter;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.factory.Hints;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

// JGraphT imports
import org.jgrapht.Graph;
import org.jgrapht.alg.scoring.EdgeBetweennessCentrality;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiLineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

/**
 * This class is a refactored version of LinkDistributorEdge13225working.java
 * to be used as a library for the UI.
 *
 * Original Description:
 * This Java application processes spatial link data from a shapefile to perform
 * network analysis, specifically calculating edge betweenness centrality,
 * and then selecting a representative sample of links based on various criteria.
 * It's designed for transportation network analysis and sampling.
 *
 * Key functionalities include:
 * 1. Shapefile Loading:
 *    - Reads link data from an input shapefile, extracting attributes like ID, TYPE, DATA1,
 *      and geometry.
 *    - Handles two-sided links by combining them based on a 'combinedId' attribute.
 *
 * 2. Group Assignment:
 *    - Assigns links to predefined groups based on their 'TYPE' attribute.
 *
 * 3. Centrality Calculation:
 *    - Constructs a graph using link endpoints as nodes.
 *    - Calculates edge betweenness centrality using the JGraphT library.
 *    - Normalizes the centrality scores.
 *
 * 4. Ramp Filtering:
 *    - Optionally filters out ramp links (identified by DATA1 attribute values 13, 14, or 15)
 *      after centrality calculation.
 *
 * 5. Sample Size Determination:
 *    - Computes sample sizes for each group based on RMSE values and group weights.
 *    - Calculates additional statistics (average, max, and min centrality) per group.
 *
 * 6. Link Selection and Sorting:
 *    - Sorts links within each group in descending order of centrality.
 *    - Selects the top links from each group according to the computed sample sizes.
 *
 * 7. Output Generation:
 *    - Writes the selected links to an output shapefile and CSV file.
 *    - Creates a centrality shapefile with detailed link attributes.
 *    - Generates a summary CSV containing run metadata, processing duration, and grouping robust spatial data handling, proper sampling, and clear output for GIS applications.
 *
 * Author: [Your Name or Company]
 * Date: [Current Date]
 */
public class LinkDistributorLogic {

    // Input/output paths and parameters
    private String inputShapeFile;
    private String baseOutputFolder;
    private String epsgCode;
    private boolean filterRamps;
    private Set<Integer> rampData1Values;
    private Set<String> centralityRoadTypes;
    private boolean combineTwoSided;
    private Map<String, Double> groupRmseMap;
    private boolean debugMode;
    private int debugPrintLimit;

    private PrintStream printStream;
    private CoordinateReferenceSystem sourceCRS; // To hold the CRS of the input shapefile

    public LinkDistributorLogic(String inputShapeFile, String baseOutputFolder, String epsgCode, boolean filterRamps, Set<Integer> rampData1Values, Set<String> centralityRoadTypes, boolean combineTwoSided, Map<String, Double> groupRmseMap, boolean debugMode, int debugPrintLimit) {
        this.inputShapeFile = inputShapeFile;
        this.baseOutputFolder = baseOutputFolder;
        this.epsgCode = "EPSG:" + epsgCode;
        this.filterRamps = filterRamps;
        this.rampData1Values = rampData1Values;
        this.centralityRoadTypes = centralityRoadTypes;
        this.combineTwoSided = combineTwoSided;
        this.debugMode = debugMode;
        this.debugPrintLimit = debugPrintLimit;

        // Initialize groupRmseMap with defaults if provided map is null or empty/invalid
        this.groupRmseMap = (groupRmseMap == null || groupRmseMap.isEmpty() || groupRmseMap.values().stream().allMatch(rmse -> rmse <= 0.0)) ?
                createDefaultRmseMap() : new HashMap<>(groupRmseMap);
    }

    private Map<String, Double> createDefaultRmseMap() {
        Map<String, Double> defaultRmseMap = new HashMap<>();
        defaultRmseMap.put("Group1", 0.1);
        defaultRmseMap.put("Group2", 0.1);
        defaultRmseMap.put("Group3", 0.1);
        defaultRmseMap.put("Group4", 0.1);
        defaultRmseMap.put("Group5", 0.1);
        defaultRmseMap.put("Group6", 0.1);
        defaultRmseMap.put("Other", 0.1);
        return defaultRmseMap;
    }

    public void setPrintStream(PrintStream printStream) {
        this.printStream = printStream;
    }

    public void run() {
        if (printStream != null) {
            System.setOut(printStream);
            System.setErr(printStream);
        }

        long startTime = System.currentTimeMillis();
        String runDateTime = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fullOutputFolder = this.baseOutputFolder + File.separator + runDateTime;

        // Ensure output folder exists
        File outputDir = new File(fullOutputFolder);
        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (debugMode) {
                System.out.println("Output directory created: " + outputDir.getAbsolutePath() + " (Success: " + created + ")");
            }
        } else {
            if (debugMode) {
                System.out.println("Output directory already exists: " + outputDir.getAbsolutePath());
            }
        }

        writeParameters(fullOutputFolder, runDateTime);

        System.out.println("=== LinkDistributorEdge Processing Started ====");
        System.out.println();

        // Step 1: Load links from shapefile.
        System.out.println("Step 1: Loading links from shapefile...");
        List<Link> allLinks = loadLinksFromShapefile(inputShapeFile, false);
        if (allLinks.isEmpty()) {
            System.err.println("Error: No links found in shapefile. Aborting.");
            return;
        }
        if (debugMode) {
            System.out.println("Total links loaded: " + allLinks.size());
            int count = 0;
            for (Link link : allLinks) {
                if (count < debugPrintLimit) {
                    System.out.println(" - ID: " + link.id + ", Type: " + link.type + ", Data1: " + link.data1);
                    count++;
                } else {
                    System.out.println(" - ... (" + (allLinks.size() - debugPrintLimit) + " more links)");
                    break;
                }
            }
        }

        // Step 2: Assign groups.
        System.out.println("\nStep 2: Assigning groups to links...");
        assignGroups(allLinks);
        Map<String, Long> groupCounts = allLinks.stream()
                .collect(Collectors.groupingBy(l -> l.group, Collectors.counting()));
        if (debugMode) {
            System.out.println("Links per group:");
            int grpCount = 0;
            for (Map.Entry<String, Long> entry : groupCounts.entrySet()) {
                if (grpCount < debugPrintLimit) {
                    System.out.println(" - " + entry.getKey() + ": " + entry.getValue() + " links");
                    grpCount++;
                } else {
                    System.out.println(" - ... (" + (groupCounts.size() - debugPrintLimit) + " more groups)");
                    break;
                }
            }
        }

        // Step 3: Filter out ramps if enabled.
        List<Link> sampledLinks = new ArrayList<>(allLinks);
        if (filterRamps) {
            System.out.println("\nStep 3: Filtering out ramp links...");
            sampledLinks = allLinks.stream()
                    .filter(link -> !rampData1Values.contains((int) link.data1))
                    .collect(Collectors.toList());
            System.out.println("Links after ramp filtering: " + sampledLinks.size());
        } else {
            System.out.println("\nStep 3: Ramp filtering skipped.");
        }

        // Step 4: Calculate edge betweenness centrality for specified road types.
        System.out.println("\nStep 4: Calculating edge betweenness centrality...");
        List<Link> centralityLinks = sampledLinks.stream()
                .filter(link -> centralityRoadTypes.contains(link.type))
                .collect(Collectors.toList());
        System.out.println("Including only road types " + centralityRoadTypes + " in centrality calculation. Links: " + centralityLinks.size());
        calculateCentrality(centralityLinks);

        // Step 5: Determine sample sizes by group.
        System.out.println("\nStep 5: Determining sample sizes by group...");
        Map<String, GroupSampleInfo> sampleInfoMap = calculateSampleSizes(sampledLinks);
        if (debugMode) {
            int infoCount = 0;
            for (Map.Entry<String, GroupSampleInfo> entry : sampleInfoMap.entrySet()) {
                if (infoCount < debugPrintLimit) {
                    System.out.println(" - " + entry.getKey() + ": n_g = " + entry.getValue().n_g);
                    infoCount++;
                } else {
                    System.out.println(" - ... (" + (sampleInfoMap.size() - debugPrintLimit) + " more sample infos)");
                    break;
                }
            }
        }

        // Step 6: Sort links within groups by descending centrality.
        System.out.println("\nStep 6: Sorting links by centrality...");
        Map<String, List<Link>> sortedLinks = sortLinksByCentrality(sampledLinks);

        // Step 7: Select final sample links based on group sample sizes.
        System.out.println("\nStep 7: Selecting final sample links...");
        Map<String, List<Link>> selectedLinks = selectSampleLinks(sortedLinks, sampleInfoMap);
        System.out.println("Total selected links: " + selectedLinks.values().stream().mapToInt(List::size).sum());

        String shpOutputPath = fullOutputFolder + File.separator + "output_shapefile.shp";
        String csvOutputPath = fullOutputFolder + File.separator + "results.csv";
        String summaryCsvPath = fullOutputFolder + File.separator + "summary.csv";
        String centralityShpPath = fullOutputFolder + File.separator + "centrality_shapefile.shp";

        // Step 8: Write main output (shapefile and CSV).
        System.out.println("\nStep 8: Writing results to shapefile and CSV...");
        writeResults(selectedLinks, shpOutputPath, csvOutputPath);
        if (debugMode) {
            System.out.println("Results written successfully to shapefile and CSV.");
        }

        // Step 8.5: Process two-sided links (if enabled).
        if (combineTwoSided) {
            System.out.println("\nStep 8.5: Processing two-sided link combination...");
            List<Link> allSelectedTwoSidedLinks = selectedLinks.values().stream()
                    .flatMap(Collection::stream)
                    .filter(l -> l.isTwoSided)
                    .collect(Collectors.toList());
            List<Link> representativeLinks = getRepresentativeTwoSidedLinks(allSelectedTwoSidedLinks);
            String representativeShpPath = fullOutputFolder + File.separator + "representative_shapefile.shp";
            writeRepresentativeResults(representativeLinks, representativeShpPath);
            System.out.println("Representative two-sided links written to: " + representativeShpPath);
        } else {
            System.out.println("\nStep 8.5: Two-sided link combination skipped.");
        }

        // Step 9: Write summary CSV.
        System.out.println("\nStep 9: Writing summary CSV...");
        writeSummaryCsv(allLinks.size(), sampledLinks.size(), sampleInfoMap, summaryCsvPath, runDateTime, System.currentTimeMillis() - startTime);
        if (debugMode) {
            System.out.println("Summary CSV written successfully.");
        }

        // Step 10: Write centrality calculation shapefile.
        System.out.println("\nStep 10: Writing centrality calculation shapefile...");
        writeCentralityShapefile(selectedLinks, centralityShpPath);
        if (debugMode) {
            System.out.println("Centrality shapefile written successfully.");
        }

        System.out.println("\n=== LinkDistributorEdge Processing Completed ====");
        System.out.println("Output Shapefile: " + shpOutputPath);
        System.out.println("Output CSV:       " + csvOutputPath);
        System.out.println("Summary CSV:      " + summaryCsvPath);
        System.out.println("Centrality Shapefile: " + centralityShpPath);
    }

    // Write parameters to a file.
    private void writeParameters(String outputFolder, String runDateTime) {
        File paramFile = new File(outputFolder, "parameters.txt");
        try (FileWriter fw = new FileWriter(paramFile)) {
            fw.write("Run Date/Time: " + runDateTime + "\n");
            fw.write("EPSG Code: " + epsgCode + "\n");
            fw.write("Filter Ramps: " + filterRamps + "\n");
            fw.write("Ramp DATA1 Values: " + rampData1Values + "\n");
            fw.write("Centrality Road Types: " + centralityRoadTypes + "\n");
            fw.write("Combine Two-Sided: " + combineTwoSided + "\n");
            fw.write("Group RMSE Map: " + groupRmseMap + "\n");
            fw.write("Debug Mode: " + debugMode + "\n");
            fw.write("Debug Print Limit: " + debugPrintLimit + "\n");
        } catch (IOException e) {
            System.err.println("Error writing parameters: " + e.getMessage());
        }
    }

    private List<Link> loadLinksFromShapefile(String shapefile, boolean includeTypeFiltering) {
        List<Link> links = new ArrayList<>();
        File file = new File(shapefile);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("url", file.toURI().toURL());
            DataStore ds = DataStoreFinder.getDataStore(params);
            if (ds == null) {
                throw new IllegalStateException("Error: Could not load shapefile: " + shapefile);
            }
            String typeName = ds.getTypeNames()[0];
            this.sourceCRS = ds.getSchema(typeName).getCoordinateReferenceSystem(); // Capture the source CRS
            SimpleFeatureCollection fc = ds.getFeatureSource(typeName).getFeatures();
            try (SimpleFeatureIterator it = fc.features()) {
                while (it.hasNext()) {
                    SimpleFeature feat = it.next();
                    Geometry geom = (Geometry) feat.getDefaultGeometry();
                    if (geom == null || geom.getCoordinates().length < 2) {
                        if (debugMode) {
                            System.out.println("Skipping feature with insufficient geometry. Feature ID: " + feat.getID());
                        }
                        continue;
                    }
                    double data1Value = -1;
                    Object data1Attr = feat.getAttribute("DATA1");
                    if (data1Attr != null) {
                        try {
                            data1Value = Double.parseDouble(data1Attr.toString());
                        } catch (NumberFormatException e) {
                            System.err.println("Warning: Invalid DATA1 value '" + data1Attr + "' for feature ID: " + feat.getID());
                        }
                    }

                    String typeStr = null;
                    Object typeAttr = feat.getAttribute("TYPE");
                    if (typeAttr != null) {
                        typeStr = typeAttr.toString();
                    } else {
                        System.err.println("Warning: TYPE attribute is null for feature ID: " + feat.getID());
                        continue;
                    }

                    int numericType = -1;
                    try {
                        numericType = Integer.parseInt(typeStr);
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Invalid TYPE value '" + typeStr + "' for feature ID: " + feat.getID());
                        continue;
                    }
                    if (includeTypeFiltering && (numericType < 1 || numericType > 6)) {
                        if (debugMode) {
                            System.out.println("Skipping feature with TYPE " + numericType + ". Feature ID: " + feat.getID());
                        }
                        continue;
                    }
                    Object idAttr = feat.getAttribute("ID");
                    String fid = (idAttr != null) ? idAttr.toString() : feat.getID();
                    boolean isTwoSided = false;
                    Object isTwo = feat.getAttribute("isTwoSided");
                    if (isTwo != null) {
                        isTwoSided = Boolean.parseBoolean(isTwo.toString());
                    }
                    String combined = fid;
                    Object cIdAttr = feat.getAttribute("combinedId");
                    if (cIdAttr != null) {
                        combined = cIdAttr.toString();
                    }

                    Link link = new Link(fid, typeStr, geom, data1Value, isTwoSided, combined);
                    links.add(link);

                    if (debugMode) {
                        if (links.size() <= debugPrintLimit) {
                            System.out.println("Loaded Link ID: " + link.id + ", Type: " + link.type + ", CombinedID: " + link.combinedId + ", DATA1: " + link.data1);
                        } else if (links.size() == debugPrintLimit + 1) {
                            System.out.println("... (" + (links.size() - debugPrintLimit) + " more links loaded)");
                        }
                    }
                }
            }
            ds.dispose();
        } catch (Exception e) {
            System.err.println("Error loading shapefile: " + e.getMessage());
            e.printStackTrace();
        }
        return links;
    }

    // Assign groups based on link type.
    private static void assignGroups(List<Link> links) {
        for (Link link : links) {
            switch (link.type) {
                case "1": link.group = "Group1"; break;
                case "2": link.group = "Group2"; break;
                case "3": link.group = "Group3"; break;
                case "4": link.group = "Group4"; break;
                case "5": link.group = "Group5"; break;
                case "6": link.group = "Group6"; break;
                default: link.group = "Other"; break;
            }
        }
    }

    private String makeEdgeKey(int u, int v) {
        return u < v ? u + "-" + v : v + "-" + u;
    }

    private void calculateCentrality(List<Link> links) {
        Graph<Integer, DefaultEdge> graph = new SimpleWeightedGraph<>(DefaultEdge.class);
        NodeManager nodeMgr = new NodeManager();
        Map<String, Link> edgeMap = new HashMap<>();

        for (Link link : links) {
            Coordinate[] coords = link.geometry.getCoordinates();
            if (coords.length < 2) {
                if (debugMode) {
                    System.out.println("Skipping Link ID " + link.id + " due to insufficient coordinates.");
                }
                continue;
            }
            Coordinate start = coords[0];
            Coordinate end = coords[coords.length - 1];
            int fromId = nodeMgr.getOrCreateNodeId(start);
            int toId = nodeMgr.getOrCreateNodeId(end);
            if (fromId == toId) {
                System.err.println("Warning: Link ID " + link.id + " forms a self-loop. Skipping.");
                continue;
            }
            link.fromNode = fromId;
            link.toNode = toId;
            graph.addVertex(fromId);
            graph.addVertex(toId);
            DefaultEdge ed = graph.addEdge(fromId, toId);
            if (ed != null) {
                edgeMap.put(makeEdgeKey(fromId, toId), link);
            }
        }

        if (debugMode) {
            System.out.println("Graph created with " + graph.vertexSet().size() + " vertices and " + graph.edgeSet().size() + " edges.");
        }
        try {
            EdgeBetweennessCentrality<Integer, DefaultEdge> ebc = new EdgeBetweennessCentrality<>(graph);
            Map<DefaultEdge, Double> raw = ebc.getScores();
            double maxVal = raw.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            if (debugMode) {
                System.out.println("Computing and normalizing centrality scores...");
            }
            for (Map.Entry<DefaultEdge, Double> e : raw.entrySet()) {
                int src = graph.getEdgeSource(e.getKey());
                int tgt = graph.getEdgeTarget(e.getKey());
                String key = makeEdgeKey(src, tgt);
                Link link = edgeMap.get(key);
                if (link != null) {
                    double norm = (maxVal == 0.0) ? 0.0 : e.getValue() / maxVal;
                    link.centrality = norm;
                    if (debugMode && link.centrality > 0) {
                        System.out.println("Link ID: " + link.id + ", Centrality: " + link.centrality);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error calculating centrality: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, GroupSampleInfo> calculateSampleSizes(List<Link> links) {
        Map<String, Long> groupCounts = links.stream()
                .collect(Collectors.groupingBy(l -> l.group, Collectors.counting()));
        if (debugMode) {
            System.out.println("Group counts for sample size calculation:");
            int count = 0;
            for (Map.Entry<String, Long> entry : groupCounts.entrySet()) {
                if (count < debugPrintLimit) {
                    System.out.println(" - " + entry.getKey() + ": " + entry.getValue() + " links");
                    count++;
                } else {
                    System.out.println(" - ... (" + (groupCounts.size() - debugPrintLimit) + " more groups)");
                    break;
                }
            }
        }

        Map<String, Double> groupWeights = new HashMap<>();
        double totalWeight = 0.0;
        for (Map.Entry<String, Double> entry : groupRmseMap.entrySet()) {
            String group = entry.getKey();
            double rmse = entry.getValue();
            if (rmse <= 0.0) {
                System.err.println("Warning: Invalid RMSE value '" + rmse + "' for group " + group + ". Skipping this group for weight calculation.");
                continue;
            }
            double weight = 1.0 / (rmse * rmse);
            groupWeights.put(group, weight);
            totalWeight += weight;
        }

        Map<String, GroupSampleInfo> sampleInfoMap = new HashMap<>();
        int sampleCount = 0;
        for (String group : groupCounts.keySet()) {
            long N_g = groupCounts.get(group);
            double w_g = groupWeights.getOrDefault(group, 0.0);
            GroupSampleInfo info = new GroupSampleInfo(group, N_g, groupRmseMap.getOrDefault(group, 0.0), w_g, 0);
            if (w_g != 0.0 && totalWeight != 0.0) {
                double n_g_double = (N_g * w_g) / totalWeight;
                info.n_g = (int) Math.round(n_g_double);
                if (debugMode) {
                    System.out.println("Group: " + group + ", N_g: " + N_g + ", w_g: " + String.format("%.5f", w_g) + ", totalWeight: " + String.format("%.5f", totalWeight) + ", n_g_double: " + String.format("%.5f", n_g_double) + ", n_g: " + info.n_g);
                }
            }
            List<Link> groupLinks = links.stream().filter(l -> l.group.equals(group)).collect(Collectors.toList());
            info.avgCentrality = groupLinks.stream().mapToDouble(l -> l.centrality).average().orElse(0.0);
            info.maxCentrality = groupLinks.stream().mapToDouble(l -> l.centrality).max().orElse(0.0);
            info.minCentrality = groupLinks.stream().mapToDouble(l -> l.centrality).min().orElse(0.0);
            info.percentage = (N_g * 100.0) / links.size();
            sampleInfoMap.put(group, info);
            if (debugMode) {
                System.out.println("Group: " + group + ", N_g: " + N_g + ", w_g: " + String.format("%.5f", w_g) + ", n_g: " + info.n_g);
            }
        }
        return sampleInfoMap;
    }

    private Map<String, List<Link>> sortLinksByCentrality(List<Link> links) {
        return links.stream()
                .collect(Collectors.groupingBy(l -> l.group,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> {
                                    list.sort(Comparator.comparingDouble(l -> ((Link) l).centrality).reversed());
                                    return list;
                                })));
    }

    // Select the top n_g links from each group.
    private Map<String, List<Link>> selectSampleLinks(Map<String, List<Link>> sorted, Map<String, GroupSampleInfo> sampleInfoMap) {
        if (debugMode) {
            System.out.println("Selecting top N links from each group based on sample sizes...");
        }
        Map<String, List<Link>> selected = new HashMap<>();
        for (Map.Entry<String, List<Link>> e : sorted.entrySet()) {
            String grp = e.getKey();
            List<Link> groupLinks = e.getValue();
            int sz = sampleInfoMap.getOrDefault(grp, new GroupSampleInfo(grp, 0, 0.0, 0.0, 0)).n_g;
            if (groupLinks.isEmpty() || sz == 0) {
                selected.put(grp, Collections.emptyList());
                if (debugMode) {
                    System.out.println("Group: " + grp + " has no links or sample size 0. Selected 0 links.");
                }
                continue;
            }
            List<Link> topN = groupLinks.stream().limit(sz).collect(Collectors.toList());
            selected.put(grp, topN);
            if (debugMode) {
                System.out.println("Group: " + grp + ", Sample Size: " + sz + ", Selected: " + topN.size() + " links.");
            }
        }
        return selected;
    }

    private void writeResults(Map<String, List<Link>> selLinks, String shpOutputPath, String csvOutputPath) {
        writeToShapefile(selLinks, shpOutputPath, false);
        writeToCsv(selLinks, csvOutputPath);
    }

    private void writeRepresentativeResults(List<Link> links, String shpOutputPath) {
        writeToShapefileFromList(links, shpOutputPath, true);
    }

    private void writeToShapefileFromList(List<Link> links, String shpOutputPath, boolean isRepresentative) {
        if (links.isEmpty()) {
            System.err.println("Warning: No links to write to shapefile: " + shpOutputPath);
            return;
        }
        if (debugMode) {
            System.out.println("Attempting to write " + links.size() + " links to shapefile: " + shpOutputPath);
        }
        File f = new File(shpOutputPath);
        File parentDir = f.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
            if (debugMode) {
                boolean created = parentDir.mkdirs();
                System.out.println("Created directory: " + parentDir.getAbsolutePath() + " (Success: " + created + ")");
            }
        }
        ShapefileDataStoreFactory dsFactory = new ShapefileDataStoreFactory();
        Map<String, Object> params = new HashMap<>();
        try {
            params.put("url", f.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            ShapefileDataStore sds = (ShapefileDataStore) dsFactory.createNewDataStore(params);
            if (sds == null) {
                throw new IllegalStateException("Error: Could not create ShapefileDataStore for: " + shpOutputPath);
            }
            SimpleFeatureType sft = isRepresentative ? createRepresentativeFeatureType() : createFeatureType();
            if (sft == null) {
                throw new NullPointerException("Error: Schema creation returned null!");
            }
            sds.createSchema(sft);
            sds.setCharset(Charset.forName("UTF-8"));
            String[] names = sds.getTypeNames();
            if (names == null || names.length == 0) {
                sds.dispose();
                throw new IOException("Error: No type name created in new shapefile.");
            }
            String typeName = names[0];

            try {
                // --- CRS Transformation Setup ---
                CoordinateReferenceSystem targetCRS = CRS.decode(epsgCode);
                if (debugMode) {
                    System.out.println("Debug: Source CRS: " + (sourceCRS != null ? sourceCRS.toWKT() : "NULL"));
                    System.out.println("Debug: Target CRS: " + (targetCRS != null ? targetCRS.toWKT() : "NULL"));
                }
                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                if (debugMode) {
                    System.out.println("Debug: MathTransform created: " + (transform != null ? transform.toWKT() : "NULL"));
                }

                try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = sds.getFeatureWriterAppend(typeName, null)) {
                    int writtenCount = 0;
                    int totalLinks = links.size();
                    for (Link link : links) {
                        SimpleFeature ft = writer.next();

                        // --- Reproject Geometry ---
                        Geometry transformedGeom = null;
                        if (link.geometry != null && transform != null) {
                            transformedGeom = JTS.transform(link.geometry, transform);
                        } else if (debugMode) {
                            System.out.println("Debug: Skipping geometry transformation for Link ID: " + link.id + " (geometry or transform is null)");
                        }
                        ft.setAttribute("the_geom", transformedGeom);

                        ft.setAttribute("ID", link.id);
                        ft.setAttribute("TYPE", link.type);
                        ft.setAttribute("GROUP", link.group);
                        ft.setAttribute("CENTRALITY", link.centrality);
                        ft.setAttribute("RMSE", link.rmse);
                        if (isRepresentative) {
                            ft.setAttribute("OTHERSIDE", link.otherSideId);
                        }
                        writer.write();
                        if (debugMode) {
                            System.out.println("Debug: Wrote Link ID: " + link.id + " to shapefile.");
                        }
                        if (debugMode && writtenCount < debugPrintLimit) {
                            System.out.println("Written Link ID: " + link.id + " to shapefile.");
                            writtenCount++;
                        } else if (debugMode && writtenCount == debugPrintLimit) {
                            System.out.println("... (" + (totalLinks - debugPrintLimit) + " more links written)");
                            writtenCount++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error writing features: " + e.getMessage());
                e.printStackTrace();
            }

            sds.dispose();
            if (debugMode) {
                System.out.println("Shapefile written successfully to " + shpOutputPath);
            }
        } catch (IOException ex) {
            System.err.println("Error writing shapefile: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // Write summary CSV with metadata and group statistics.
    /**
     * Writes aggregate summary to a CSV file.
     *
     * The summary includes:
     * - Run metadata: date/time, processing duration, and EPSG code.
     * - Total network links and first-stage sampled links.
     * - For each group: total links (N_g), RMSE, weight (w_g), sample size (n_g),
     *   as well as average, maximum, and minimum centrality.
     *
     * @param totalLinks Total number of links in the network before sampling.
     * @param sampledLinks Total number of links sampled in the first-stage.
     * @param sampleInfoMap Map of group names to their respective GroupSampleInfo objects.
     * @param summaryCsvPath Path to the summary CSV file.
     * @param runDateTime The run date/time string.
     * @param durationMillis The processing duration in milliseconds.
     */
    private void writeSummaryCsv(long totalLinks, long sampledLinks, Map<String, GroupSampleInfo> sampleInfoMap, String summaryCsvPath, String runDateTime, long durationMillis) {
        File f = new File(summaryCsvPath);
        File parentDir = f.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileWriter fw = new FileWriter(f)) {
            // Write run metadata
            fw.write("Run Date/Time:," + runDateTime + "\n");
            fw.write("Processing Duration (ms):," + durationMillis + "\n");
            fw.write("EPSG Code:," + epsgCode + "\n\n");

            // Write overall network information
            fw.write("Total Links in Network,First-Stage Sampleed Links\n");
            fw.write(totalLinks + "," + sampledLinks + "\n\n");

            // Write per-group summary statistics
            fw.write("Group,N_g,RMSE,w_g,n_g,AvgCentrality,MaxCentrality,MinCentrality\n");
            List<String> orderedGroups = new ArrayList<>(sampleInfoMap.keySet());
            Collections.sort(orderedGroups); // Sort groups for consistent output

            int groupPrintCount = 0;
            for (String group : orderedGroups) {
                GroupSampleInfo info = sampleInfoMap.get(group);
                if (info != null) {
                    fw.write(String.format("%s,%d,%.2f,%.5f,%d,%.4f,%.4f,%.4f\n",                        info.group, info.N_g, info.rmse, info.w_g, info.n_g,                        info.avgCentrality, info.maxCentrality, info.minCentrality));
                } else {
                    fw.write(String.format("%s,0,%.2f,%.5f,0,0,0,0\n", group, 0.0, 0.0));
                }
                if (debugMode && groupPrintCount < debugPrintLimit) {
                    System.out.println("Summary for Group " + group + ": N_g=" + info.N_g + ", n_g=" + info.n_g);
                    groupPrintCount++;
                } else if (debugMode && groupPrintCount == debugPrintLimit) {
                    System.out.println("... (" + (orderedGroups.size() - debugPrintLimit) + " more group summaries)");
                    groupPrintCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing summary CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Create a feature type for the output shapefile.
    private SimpleFeatureType createFeatureType() {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("LinkSchema");
        try {
            builder.setCRS(CRS.parseWKT("PROJCS[\"Israel_TM_Grid\",GEOGCS[\"GCS_Israel\",DATUM[\"D_Israel\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",219529.584],PARAMETER[\"False_Northing\",626907.39],PARAMETER[\"Central_Meridian\",35.2045169444444],PARAMETER[\"Scale_Factor\",1.0000067],PARAMETER[\"Latitude_Of_Origin\",31.7343936111111],UNIT[\"Meter\",1.0]]"));
        } catch (FactoryException e) {
            System.err.println("Error decoding CRS (" + epsgCode + "): " + e.getMessage() + " -- falling back to WGS84.");
            builder.setCRS(DefaultGeographicCRS.WGS84);
        }
        builder.add("the_geom", MultiLineString.class);
        builder.add("ID", String.class);
        builder.add("TYPE", String.class);
        builder.add("GROUP", String.class);
        builder.add("CENTRALITY", Double.class);
        builder.add("RMSE", Double.class);
        return builder.buildFeatureType();
    }

    // Create a feature type for the representative shapefile (with "OTHERSIDE" field).
    private SimpleFeatureType createRepresentativeFeatureType() {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("RepresentativeLinkSchema");
        try {
            builder.setCRS(CRS.parseWKT("PROJCS[\"Israel_TM_Grid\",GEOGCS[\"GCS_Israel\",DATUM[\"D_Israel\",SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"False_Easting\",219529.584],PARAMETER[\"False_Northing\",626907.39],PARAMETER[\"Central_Meridian\",35.2045169444444],PARAMETER[\"Scale_Factor\",1.0000067],PARAMETER[\"Latitude_Of_Origin\",31.7343936111111],UNIT[\"Meter\",1.0]]"));
        } catch (FactoryException e) {
            System.err.println("Error decoding CRS (" + epsgCode + "): " + e.getMessage() + " -- falling back to WGS84.");
            builder.setCRS(DefaultGeographicCRS.WGS84);
        }
        builder.add("the_geom", MultiLineString.class);
        builder.add("ID", String.class);
        builder.add("TYPE", String.class);
        builder.add("GROUP", String.class);
        builder.add("CENTRALITY", Double.class);
        builder.add("RMSE", Double.class);
        builder.add("OTHERSIDE", String.class); // Added for representative links
        return builder.buildFeatureType();
    }

    private void writeToShapefile(Map<String, List<Link>> selLinks, String shpOutputPath, boolean isRepresentative) {
        int totalLinks = selLinks.values().stream().mapToInt(List::size).sum();
        if (totalLinks == 0) {
            System.err.println("Warning: No links to write to shapefile: " + shpOutputPath);
            return;
        }
        if (debugMode) {
            System.out.println("Attempting to write " + totalLinks + " links to shapefile: " + shpOutputPath);
        }
        File f = new File(shpOutputPath);
        File parentDir = f.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
            if (debugMode) {
                boolean created = parentDir.mkdirs();
                System.out.println("Created directory: " + parentDir.getAbsolutePath() + " (Success: " + created + ")");
            }
        }
        ShapefileDataStoreFactory dsFactory = new ShapefileDataStoreFactory();
        Map<String, Object> params = new HashMap<>();
        try {
            params.put("url", f.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            ShapefileDataStore sds = (ShapefileDataStore) dsFactory.createNewDataStore(params);
            if (sds == null) {
                System.err.println("Debug: ShapefileDataStore is null for: " + shpOutputPath);
                throw new IllegalStateException("Error: Could not create ShapefileDataStore for: " + shpOutputPath);
            }
            if (debugMode) {
                System.out.println("Debug: ShapefileDataStore created successfully for: " + shpOutputPath);
            }
            SimpleFeatureType sft = isRepresentative ? createRepresentativeFeatureType() : createFeatureType();
            if (sft == null) {
                System.err.println("Debug: SimpleFeatureType is null!");
                throw new NullPointerException("Error: Schema creation returned null!");
            }
            if (debugMode) {
                System.out.println("Debug: SimpleFeatureType created. Attempting to create schema.");
            }
            sds.createSchema(sft);
            if (debugMode) {
                System.out.println("Debug: Schema created. Attempting to set charset.");
            }
            sds.setCharset(Charset.forName("UTF-8"));
            if (debugMode) {
                System.out.println("Debug: Charset set. Attempting to get type names.");
            }
            String[] names = sds.getTypeNames();
            if (names == null || names.length == 0) {
                sds.dispose();
                System.err.println("Debug: No type name created in new shapefile.");
                throw new IOException("Error: No type name created in new shapefile.");
            }
            if (debugMode) {
                System.out.println("Debug: Type name obtained: " + names[0]);
            }
            String typeName = names[0];

            try {
                // --- CRS Transformation Setup ---
                CoordinateReferenceSystem targetCRS = CRS.decode(epsgCode);
                if (debugMode) {
                    System.out.println("Debug: Source CRS: " + (sourceCRS != null ? sourceCRS.toWKT() : "NULL"));
                    System.out.println("Debug: Target CRS: " + (targetCRS != null ? targetCRS.toWKT() : "NULL"));
                }
                MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                if (debugMode) {
                    System.out.println("Debug: MathTransform created: " + (transform != null ? transform.toWKT() : "NULL"));
                }

                try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = sds.getFeatureWriterAppend(typeName, null)) {
                    int writtenCount = 0;
                    for (List<Link> groupLinks : selLinks.values()) {
                        for (Link link : groupLinks) {
                            SimpleFeature ft = writer.next();

                            // --- Reproject Geometry ---
                            Geometry transformedGeom = null;
                            if (link.geometry != null && transform != null) {
                                transformedGeom = JTS.transform(link.geometry, transform);
                            } else if (debugMode) {
                                System.out.println("Debug: Skipping geometry transformation for Link ID: " + link.id + " (geometry or transform is null)");
                            }
                            ft.setAttribute("the_geom", transformedGeom);

                            ft.setAttribute("ID", link.id);
                            ft.setAttribute("TYPE", link.type);
                            ft.setAttribute("GROUP", link.group);
                            ft.setAttribute("CENTRALITY", link.centrality);
                            ft.setAttribute("RMSE", link.rmse);
                            if (isRepresentative) {
                                ft.setAttribute("OTHERSIDE", link.otherSideId);
                            }
                            writer.write();
                            if (debugMode) {
                                System.out.println("Debug: Wrote Link ID: " + link.id + " to shapefile.");
                            }
                            if (debugMode && writtenCount < debugPrintLimit) {
                                System.out.println("Written Link ID: " + link.id + " to shapefile.");
                                writtenCount++;
                            } else if (debugMode && writtenCount == debugPrintLimit) {
                                System.out.println("... (" + (totalLinks - debugPrintLimit) + " more links written)");
                                writtenCount++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error writing features: " + e.getMessage());
                e.printStackTrace();
            }

            sds.dispose();
            if (debugMode) {
                System.out.println("Shapefile written successfully to " + shpOutputPath);
            }
        } catch (IOException ex) {
            System.err.println("Error writing shapefile: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void writeCentralityShapefile(Map<String, List<Link>> selLinks, String shpOutputPath) {
        writeToShapefile(selLinks, shpOutputPath, false); // Centrality shapefile doesn't need OTHERSIDE
    }

    private void writeToCsv(Map<String, List<Link>> selLinks, String csvPath) {
        if (debugMode) {
            System.out.println("Attempting to write CSV to: " + csvPath);
        }
        File f = new File(csvPath);
        File par = f.getParentFile();
        if (par != null && !par.exists()) {
            boolean created = par.mkdirs();
            if (debugMode) {
                System.out.println("Created directory for CSV: " + par.getAbsolutePath() + " (Success: " + created + ")");
            }
        }
        try (FileWriter fw = new FileWriter(f)) {
            fw.write("ID,TYPE,GROUP,CENTRALITY,RMSE,DATA1,isTwoSided,COMBINED_ID,LENGTH\n");
            int csvPrintCount = 0;
            int totalSelectedLinks = selLinks.values().stream().mapToInt(List::size).sum();
            for (List<Link> groupLinks : selLinks.values()) {
                for (Link link : groupLinks) {
                    fw.write(String.format("%s,%s,%s,%.4f,%.4f,%.4f,%s,%s,%.4f\n",
                            link.id, link.type, link.group, link.centrality, link.rmse,
                            link.data1, link.isTwoSided, link.combinedId, ((org.locationtech.jts.geom.Geometry)link.geometry).getLength()));
                    if (debugMode && csvPrintCount < debugPrintLimit) {
                        System.out.println("Written CSV Link ID: " + link.id);
                        csvPrintCount++;
                    } else if (debugMode && csvPrintCount == debugPrintLimit) {
                        System.out.println("... (" + (totalSelectedLinks - debugPrintLimit) + " more links written to CSV)");
                        csvPrintCount++;
                    }
                }
            }
            if (debugMode) {
                System.out.println("CSV written successfully to " + csvPath);
            }
        } catch (IOException e) {
            System.err.println("Error writing CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Process two-sided links to get representative links.
    private List<Link> getRepresentativeTwoSidedLinks(List<Link> links) {
        // Group two-sided links by combinedId.
        Map<String, List<Link>> grouped = links.stream()
                .filter(l -> l.isTwoSided)
                .collect(Collectors.groupingBy(l -> l.combinedId));
        List<Link> representatives = new ArrayList<>();
        // For each group of two-sided links:
        for (Map.Entry<String, List<Link>> entry : grouped.entrySet()) {
            List<Link> group = entry.getValue();
            if (group.isEmpty()) {
                continue;
            }
            if (group.size() == 1) {
                representatives.add(group.get(0));
            } else {
                double avg = group.stream().mapToDouble(l -> l.centrality).average().orElse(0.0);
                Link rep = group.get(0); // Take the first one as representative
                rep.centrality = avg;
                String otherIds = group.stream().skip(1).map(l -> l.id).collect(Collectors.joining(";"));
                rep.otherSideId = otherIds; // Store IDs of other side links
                representatives.add(rep);
            }
        }
        return representatives;
    }

    // A simplified version of the original class for brevity
    static class Link {
        String id;
        String type;
        String group;
        double centrality;
        double rmse;
        double data1;
        boolean isTwoSided;
        String combinedId;
        Geometry geometry;
        int fromNode;
        int toNode;
        String otherSideId;

        public Link(String id, String type, Geometry geometry, double data1, boolean isTwoSided, String combinedId) {
            this.id = id;
            this.type = type;
            this.geometry = geometry;
            this.data1 = data1;
            this.isTwoSided = isTwoSided;
            this.combinedId = combinedId;
        }
    }

    static class GroupSampleInfo {
        String group;
        long N_g;
        double rmse;
        double w_g;
        int n_g;
        double avgCentrality;
        double maxCentrality;
        double minCentrality;
        double percentage;

        public GroupSampleInfo(String group, long N_g, double rmse, double w_g, int n_g) {
            this.group = group;
            this.N_g = N_g;
            this.rmse = rmse;
            this.w_g = w_g;
            this.n_g = n_g;
        }
    }

    static class NodeManager {
        private final Map<Coordinate, Integer> coordToId = new HashMap<>();
        private int nextId = 0;
        public int getOrCreateNodeId(Coordinate coord) {
            Coordinate rounded = new Coordinate(Math.round(coord.x * 100) / 100.0, Math.round(coord.y * 100) / 100.0);
            return coordToId.computeIfAbsent(rounded, k -> nextId++);
        }
    }

    // TeeOutputStream class (from original file)
    static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        public TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                out1.close();
            }
            finally {
                out2.close();
            }
        }
    }
}