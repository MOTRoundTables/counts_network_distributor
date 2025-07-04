package com.golan;

import javafx.beans.property.*;

public class NetworkConfig {
    private final StringProperty network;
    private final StringProperty shapefilePath;
    private final DoubleProperty price;
    private final DoubleProperty budget;
    private final IntegerProperty quota;
    private final StringProperty description;

    public NetworkConfig(String network, String shapefilePath, Double price, Double budget, Integer quota, String description) {
        this.network = new SimpleStringProperty(network);
        this.shapefilePath = new SimpleStringProperty(shapefilePath);
        this.price = new SimpleDoubleProperty(price != null ? price : 0.0);
        this.budget = new SimpleDoubleProperty(budget != null ? budget : 0.0);
        this.quota = new SimpleIntegerProperty(quota != null ? quota : 0);
        this.description = new SimpleStringProperty(description);
    }

    public String getNetwork() {
        return network.get();
    }

    public StringProperty networkProperty() {
        return network;
    }

    public void setNetwork(String network) {
        this.network.set(network);
    }

    public String getShapefilePath() {
        return shapefilePath.get();
    }

    public StringProperty shapefilePathProperty() {
        return shapefilePath;
    }

    public void setShapefilePath(String shapefilePath) {
        this.shapefilePath.set(shapefilePath);
    }

    public Double getPrice() {
        return price.get();
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public void setPrice(Double price) {
        this.price.set(price);
    }

    public Double getBudget() {
        return budget.get();
    }

    public DoubleProperty budgetProperty() {
        return budget;
    }

    public void setBudget(Double budget) {
        this.budget.set(budget);
    }

    public Integer getQuota() {
        return quota.get();
    }

    public IntegerProperty quotaProperty() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota.set(quota);
    }
}
