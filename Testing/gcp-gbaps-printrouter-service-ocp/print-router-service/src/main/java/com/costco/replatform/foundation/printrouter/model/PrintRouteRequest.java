package com.costco.replatform.foundation.printrouter.model;


public class PrintRouteRequest {

    private PrintMetadata printMetadata;

    private ShippingLabelPrintData shippingLabelPrintData;

    private ManifestLabelPrintData manifestLabelPrintData;

    private RtLabelPrintData rtLabelPrintData;

    private BolPrintData bolPrintData;

    private HandlingInstructPrintData handlingInstructPrintData;

    private ShippingDocPrintData shippingDocPrintData;

    public PrintRouteRequest() {
    }

    public PrintMetadata getPrintMetadata() {
        return printMetadata;
    }

    public void setPrintMetadata(PrintMetadata printMetadata) {
        this.printMetadata = printMetadata;
    }

    public ShippingLabelPrintData getShippingLabelPrintData() {
        return shippingLabelPrintData;
    }

    public void setShippingLabelPrintData(
            ShippingLabelPrintData shippingLabelPrintData) {
        this.shippingLabelPrintData = shippingLabelPrintData;
    }

    public ManifestLabelPrintData getManifestLabelPrintData() {
        return manifestLabelPrintData;
    }

    public void setManifestLabelPrintData(
            ManifestLabelPrintData manifestLabelPrintData) {
        this.manifestLabelPrintData = manifestLabelPrintData;
    }

    public RtLabelPrintData getRtLabelPrintData() {
        return rtLabelPrintData;
    }

    public void setRtLabelPrintData(
            RtLabelPrintData rtLabelPrintData) {
        this.rtLabelPrintData = rtLabelPrintData;
    }

    public BolPrintData getBolPrintData() {
        return bolPrintData;
    }

    public void setBolPrintData(BolPrintData bolPrintData) {
        this.bolPrintData = bolPrintData;
    }

    public HandlingInstructPrintData getHandlingInstructPrintData() {
        return handlingInstructPrintData;
    }

    public void setHandlingInstructPrintData(
            HandlingInstructPrintData handlingInstructPrintData) {
        this.handlingInstructPrintData = handlingInstructPrintData;
    }

    public ShippingDocPrintData getShippingDocPrintData() {
        return shippingDocPrintData;
    }

    public void setShippingDocPrintData(
            ShippingDocPrintData shippingDocPrintData) {
        this.shippingDocPrintData = shippingDocPrintData;
    }
}
