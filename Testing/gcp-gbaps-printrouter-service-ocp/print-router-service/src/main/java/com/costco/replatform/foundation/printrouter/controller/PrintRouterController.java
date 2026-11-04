package com.costco.replatform.foundation.printrouter.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.costco.replatform.foundation.printrouter.common.enums.PrintRequestStatus;
import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiRequest;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrintMetadata;
import com.costco.replatform.foundation.printrouter.model.PrintRouteRequest;
import com.costco.replatform.foundation.printrouter.model.PrintRouteResponse;
import com.costco.replatform.foundation.printrouter.model.PrintServerDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;
import com.costco.replatform.foundation.printrouter.service.ActiveDirectoryDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrintServerDiscoveryService;
import com.costco.replatform.foundation.printrouter.service.PrinterConnectivityService;

@RestController
@RequestMapping("/${app.url.api.name}/v1/printroute")
public class PrintRouterController {

	private final ActiveDirectoryDiscoveryService activeDirectoryDiscoveryService;

	private final PrinterConnectivityService printService;

	public PrintRouterController(ActiveDirectoryDiscoveryService activeDirectoryDiscoveryService,
			PrinterConnectivityService delegateService) {
		this.activeDirectoryDiscoveryService = activeDirectoryDiscoveryService;
		this.printService = delegateService;
	}

	private static final Logger log = LoggerFactory.getLogger(PrintRouterController.class);

	/**
	 * Controller method to route to right printer added in print server.
	 * 
	 * @param apiRequest containing the PrintRequest payload
	 * @return
	 */
	@PostMapping("/send")
	public ResponseEntity<ApiResponse<PrintRouteResponse>> routePrint(
			@RequestBody ApiRequest<PrintRouteRequest> apiRequest) {

		log.info("OCP_PRINT_ROUTE Starting");
		PrintRouteResponse printRouteResponse = new PrintRouteResponse();
		ApiResponse<PrintRouteResponse> routeApiResponse = ApiResponse.init(printRouteResponse);
		Integer itemNo =0;
		Integer printCopies=1;
		String documentName=null;
		String trackingId=null;
		String locationId=null;

		if (apiRequest != null) {
			String uiRequestId = apiRequest.getRequestId();
			PrintRouteRequest request = apiRequest.getRequest();
			if (request != null) {
				PrintMetadata metaData = request.getPrintMetadata();
				if (metaData != null) {
					printCopies = metaData.getCopies();
					documentName = metaData.getDocumentName();
					String documentType = metaData.getDocumentType().name();
					itemNo = metaData.getItemNo();
					locationId = metaData.getLocationId();
					String printerType = metaData.getPrinterType().name();
					String printType = metaData.getPrintType().name();
					String requestedUser = metaData.getRequestedUser();
					String requestTime = metaData.getRequestTime();
					trackingId = metaData.getTrackingId();
					
					log.info(
							"OCP_PRINT_ROUTE_REQ_DETAILS -> itemNo: {}, printCopies: {}, documentName: {}, trackingId: {}, locationId: {}, "
									+ "documentType: {}, printerType: {}, printType: {}, requestedUser: {}, requestTime: {}, uiRequestId: {}",
							itemNo, printCopies, documentName, trackingId, locationId, documentType, printerType,
							printType, mask(requestedUser), requestTime, uiRequestId);

				}
			}

			// PrintRouteRequest printRequest = apiRequest.getRequest();
			ApiResponse<ActiveDirectoryDiscoveryResponse> discoveryApiResponse = activeDirectoryDiscoveryService
					.discoverPrintersByLocation(locationId);//"01680/LA/WHS/MGROFC"
			log.info("OCP_PRINT_ROUTE_DISCOVERY_RESP:: AD Discovery Response : {}", discoveryApiResponse);
			List<PrinterMetadata> printerList = discoveryApiResponse.getResponse().getPrinterList();
			if (!CollectionUtils.isEmpty(printerList)) {
				for (PrinterMetadata printerMetadata : printerList) {
					if (printerMetadata != null) {
						String printerName=printerMetadata.getPrinterName();
						log.info("OCP_PRINT_ROUTE::Sending IPP request to printer:"+printerName+"...");
						Integer printResponseStatus = null;
						ApiResponse<PrinterIppResponse> printResponse = printService.sendPrintRequest(itemNo,printerName,documentName);
						log.info("OCP_PRINT_ROUTE::Print request successfully submitted to printer.TrackingID:"+trackingId+"...");
						if (printResponse != null && printResponse.getResponse() != null) {
							log.info("OCP_PRINT_ROUTE::Printer Response Received:" + printResponse.getResponse().toString());
							printResponseStatus = printResponse.getResponse().httpStatus();
							log.info("OCP_PRINT_ROUTE::Printer Response HTTP Status: {}", printResponseStatus);

						}
						if (printResponseStatus != null && printResponseStatus == 200) {
							routeApiResponse = ApiResponse.ok(printRouteResponse);
							routeApiResponse.getResponse().setPrintStatus(PrintRequestStatus.PRINT_REQUESTED.name());
							routeApiResponse.getResponse().setPrintStatus(PrintRequestStatus.PRINT_REQUESTED.name());
							log.info("OCP_PRINT_ROUTE_SUCCESS_RESP::RouteApiResponse.getResponse(): {}", routeApiResponse.getResponse());
							log.info("OCP_PRINT_ROUTE_SUCCESS::========== Print Request Routed to Printer Successfully ==========");
							return ResponseEntity.ok(routeApiResponse);

						}
					}
				}
			}

			log.info("OCP_PRINT_ROUTE_FAILED::Connectivity validation failed. Skipping IPP request.");
			return ResponseEntity.badRequest().body(routeApiResponse);

		} else {
			log.info("OCP_PRINT_ROUTE::FAILED::Bad Request:: API request is null.");
			return ResponseEntity.badRequest().body(routeApiResponse);
		}
	}
	
	
	/**
	 * Method to mask sensitive userId
	 * 
	 * @param data
	 * @return
	 */
	private static String mask(String data) {
		if (data == null || data.isBlank()) {
			return data;
		}

		if (data.length() <= 2) {
			return "*".repeat(data.length());
		}

		return data.charAt(0) + "*".repeat(data.length() - 2) + data.charAt(data.length() - 1);
	}
}
