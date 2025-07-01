# Project Overview
This project is a Java application designed for **transportation network analysis and sampling**. It processes spatial link data from shapefiles, performs network analysis, and selects a representative sample of links based on various criteria.

**Core Functionalities:**
- **Shapefile Processing:** Reads and writes spatial data from/to ESRI Shapefiles.
- **Network Analysis:** Calculates edge betweenness centrality using graph theory algorithms.
- **Link Sampling:** Selects a statistically representative sample of links based on user-defined criteria and RMSE values.
- **Output Generation:** Produces output shapefiles (for selected links and centrality visualization) and CSV summary reports.
- **Graphical User Interface (GUI):** Provides a JavaFX-based UI for configuring analysis parameters, running the process, viewing real-time logs, and visualizing results (map and statistics).

# Technologies Used
- **Java:** Core programming language.
- **Apache Maven:** Project build automation tool.
- **GeoTools:** Open-source Java library for geospatial data, used for reading/writing shapefiles and map visualization.
- **JGraphT:** Open-source Java graph library, used for constructing the network graph and calculating centrality.
- **JavaFX:** GUI toolkit for building the desktop application.
- **Swing:** Used within JavaFX via `SwingNode` for embedding GeoTools' `JMapPane` for map display.

# Setup Instructions
To set up and run this project, you will need:
- **Java Development Kit (JDK) 8 or higher:** Ensure `JAVA_HOME` is set correctly.
- **Apache Maven:** Ensure Maven is installed and configured in your system's PATH.

**To build the project:**
```bash
mvn clean install
```
This command will compile the source code, run tests, and package the application into a JAR file in the `target/` directory.

**To run the application (GUI):**
After building, you can typically run the JavaFX application using the `java -jar` command or directly from your IDE.
```bash
mvn javafx:run
```
This command uses the JavaFX Maven Plugin to run the application, ensuring all JavaFX dependencies are correctly handled.

After running, verify that links of types not intended for centrality calculation (e.g., type 9 and 13) are no longer present in the output shapefile or results.csv, and that the statistics table in the GUI is populated correctly, and the map is styled and zoomed correctly.

# Common Commands
- **Build Project:** `mvn clean install`
- **Clean Project:** `mvn clean` (removes the `target` directory)
- **Run Tests:** `mvn test`

# Project Conventions
- **Language:** Java
- **Build Tool:** Maven
- **IDE:** Eclipse (as per user preference)
- **Code Structure:** Follows standard Maven project layout.
- **Logging:** Output is redirected to a `TextArea` in the UI for real-time feedback.

# Important Notes for Gemini
- **Spatial Data Handling:** Be mindful of coordinate reference systems (CRS) when working with GeoTools. The application uses an EPSG code for CRS definition.
- **Graph Algorithms:** Changes to centrality calculations should be carefully validated, as they are central to the sampling logic.
- **UI/UX:** The UI is built with JavaFX, with a Swing component embedded for mapping. Be aware of the interoperability when making UI changes.
- **Error Handling:** Ensure robust error handling, especially for file I/O and geospatial operations.
- **Performance:** For large shapefiles, consider the performance implications of data loading and graph processing.
- **Compatibility:** Prioritize maintaining compatibility with Java 8 if possible, or clarify if a higher version is required for new features.
- **Debugging:** The `LinkDistributorLogic` class has a `debugMode` and `debugPrintLimit` which can be useful for tracing execution.

# Output Files
The application generates several output files in a timestamped subdirectory within the specified output directory (e.g., `output/20250627_103045/`).

- **`output_shapefile.shp` (and associated files: `.shx`, `.dbf`, `.prj`):** This shapefile contains the final selected sample of links.
- **`results.csv`:** A CSV file detailing the attributes of the selected links, including ID, type, group, centrality, RMSE, DATA1, two-sided status, combined ID, and length.
- **`summary.csv`:** A summary CSV file providing metadata about the run (date/time, duration, EPSG code) and detailed statistics per group, including total links, RMSE, weight, sample size, and average/max/min centrality.
- **`centrality_shapefile.shp` (and associated files):** A shapefile containing all links with their calculated centrality scores, useful for visualizing the centrality distribution across the network.
- **`representative_shapefile.shp` (and associated files):** (Generated only if "Combine Two-Sided Links" is enabled in the UI). This shapefile contains representative links for two-sided links, where one link represents both directions with an aggregated centrality score.
- **`parameters.txt`:** A text file listing all the input parameters used for the specific run, useful for reproducibility.