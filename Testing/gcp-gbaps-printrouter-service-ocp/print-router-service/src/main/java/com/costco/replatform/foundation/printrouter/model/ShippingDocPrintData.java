package com.costco.replatform.foundation.printrouter.model;


public class ShippingDocPrintData {

    private String title;

    private String packageId;

    private String packageType;

    private String vendorName;

    private String date;

    private String documentNumber;

    private String location;

    private String accountNumber;

    private String raNumber;

    private String itemNumber;

    private String itemDescription;

    private String sellUnits;

    private String shippingCharge;

    private String extendedWeight;

    private String itemBarcode;

    private String productStatus;

    private String action;

    /*
     * Bottom-left / footer information
     */
    private String rtv;

    private String manager;

    private String logLine;

    private String caseCount;

    private String boff;

    /*
     * Bottom-right totals
     */
    private String totalWeightCases;

    private String totalSell;

    private String controlTotal;

    private String totalDate;

    /*
     * Top-right
     */
    private String pageNumber;


    public ShippingDocPrintData() {
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }


    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }


    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }


    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }


    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }


    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }


    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }


    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }


    public String getRaNumber() {
        return raNumber;
    }

    public void setRaNumber(String raNumber) {
        this.raNumber = raNumber;
    }


    public String getItemNumber() {
        return itemNumber;
    }

    public void setItemNumber(String itemNumber) {
        this.itemNumber = itemNumber;
    }


    public String getItemDescription() {
        return itemDescription;
    }

    public void setItemDescription(String itemDescription) {
        this.itemDescription = itemDescription;
    }


    public String getSellUnits() {
        return sellUnits;
    }

    public void setSellUnits(String sellUnits) {
        this.sellUnits = sellUnits;
    }


    public String getShippingCharge() {
        return shippingCharge;
    }

    public void setShippingCharge(String shippingCharge) {
        this.shippingCharge = shippingCharge;
    }


    public String getExtendedWeight() {
        return extendedWeight;
    }

    public void setExtendedWeight(String extendedWeight) {
        this.extendedWeight = extendedWeight;
    }


    public String getItemBarcode() {
        return itemBarcode;
    }

    public void setItemBarcode(String itemBarcode) {
        this.itemBarcode = itemBarcode;
    }


    public String getProductStatus() {
        return productStatus;
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
    }


    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }


    public String getRtv() {
        return rtv;
    }

    public void setRtv(String rtv) {
        this.rtv = rtv;
    }


    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }


    public String getLogLine() {
        return logLine;
    }

    public void setLogLine(String logLine) {
        this.logLine = logLine;
    }


    public String getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(String caseCount) {
        this.caseCount = caseCount;
    }


    public String getBoff() {
        return boff;
    }

    public void setBoff(String boff) {
        this.boff = boff;
    }


    public String getTotalWeightCases() {
        return totalWeightCases;
    }

    public void setTotalWeightCases(String totalWeightCases) {
        this.totalWeightCases = totalWeightCases;
    }


    public String getTotalSell() {
        return totalSell;
    }

    public void setTotalSell(String totalSell) {
        this.totalSell = totalSell;
    }


    public String getControlTotal() {
        return controlTotal;
    }

    public void setControlTotal(String controlTotal) {
        this.controlTotal = controlTotal;
    }


    public String getTotalDate() {
        return totalDate;
    }

    public void setTotalDate(String totalDate) {
        this.totalDate = totalDate;
    }


    public String getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(String pageNumber) {
        this.pageNumber = pageNumber;
    }
}
