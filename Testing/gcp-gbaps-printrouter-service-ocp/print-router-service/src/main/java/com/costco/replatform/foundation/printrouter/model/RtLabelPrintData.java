package com.costco.replatform.foundation.printrouter.model;



public class RtLabelPrintData {

    private String itemDescription;
    private String itemNumber;

    private String barcode;
    private String barcodeLabel;

    private String barcodeValue;
    private String quantity;

    private String departmentNumber;
    private String vendorNumber;
    private String rtd;

    private String shipLocation;
    private String shipId;

    private String workOrder;

    private String workOrderBarcode;
    private String workOrderBarcodeValue;

    public RtLabelPrintData() {
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }

    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getBarcodeLabel() {
        return barcodeLabel;
    }

    public void setBarcodeLabel(String barcodeLabel) {
        this.barcodeLabel = barcodeLabel;
    }

    public String getBarcodeValue() {
        return barcodeValue;
    }

    public void setBarcodeValue(String barcodeValue) {
        this.barcodeValue = barcodeValue;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getDepartmentNumber() {
        return departmentNumber;
    }

    public void setDepartmentNumber(String departmentNumber) {
        this.departmentNumber = departmentNumber;
    }

    public String getVendorNumber() {
        return vendorNumber;
    }

    public void setVendorNumber(String vendorNumber) {
        this.vendorNumber = vendorNumber;
    }

    public String getRtd() {
        return rtd;
    }

    public void setRtd(String rtd) {
        this.rtd = rtd;
    }

    public String getShipLocation() {
        return shipLocation;
    }

    public void setShipLocation(String shipLocation) {
        this.shipLocation = shipLocation;
    }

    public String getShipId() {
        return shipId;
    }

    public void setShipId(String shipId) {
        this.shipId = shipId;
    }

    public String getWorkOrder() {
        return workOrder;
    }

    public void setWorkOrder(String workOrder) {
        this.workOrder = workOrder;
    }

    public String getWorkOrderBarcode() {
        return workOrderBarcode;
    }

    public void setWorkOrderBarcode(String workOrderBarcode) {
        this.workOrderBarcode = workOrderBarcode;
    }

    public String getWorkOrderBarcodeValue() {
        return workOrderBarcodeValue;
    }

    public void setWorkOrderBarcodeValue(String workOrderBarcodeValue) {
        this.workOrderBarcodeValue = workOrderBarcodeValue;
    }
}

