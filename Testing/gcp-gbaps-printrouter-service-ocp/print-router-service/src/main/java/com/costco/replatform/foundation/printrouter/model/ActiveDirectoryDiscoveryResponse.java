package com.costco.replatform.foundation.printrouter.model;

import java.util.List;

public class ActiveDirectoryDiscoveryResponse {
	
	private List<PrinterMetadata> printerList;

	public List<PrinterMetadata> getPrinterList() {
		return printerList;
	}

	public void setPrinterList(List<PrinterMetadata> printerList) {
		this.printerList = printerList;
	}



}
