package com.costco.replatform.foundation.printrouter.model;

public class PrinterMetadata {

	private String printerName;
	private String shareName;
	private String server;
	private String location;
	private String model;

	public String getPrinterName() {
		return printerName;
	}

	public void setPrinterName(String printerName) {
		this.printerName = printerName;
	}

	public String getShareName() {
		return shareName;
	}

	public void setShareName(String shareName) {
		this.shareName = shareName;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}
	
	@Override
	public String toString() {
		return "PrinterMetadata{" +
				"printerName='" + printerName + '\'' +
				", shareName='" + shareName + '\'' +
				", server='" + server + '\'' +
				", location='" + location + '\'' +
				", model='" + model + '\'' +
				'}';
	}
}
