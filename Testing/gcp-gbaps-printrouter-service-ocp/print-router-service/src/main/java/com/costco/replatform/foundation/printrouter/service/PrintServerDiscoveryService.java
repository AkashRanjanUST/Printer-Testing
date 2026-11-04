package com.costco.replatform.foundation.printrouter.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrintServerDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.util.PrintRouterIppRequestBuilder;

/**
 * Service for handling printer discovery and IPP print requests
 * All configuration values are externalized to application properties
 */
@Service
public class PrintServerDiscoveryService {

	// ============================================================
	// Configuration Properties - Injected from application.properties
	// ============================================================

	private static final String SUCCESS = "SUCCESS";

	@Value("${printrouter.target.url:http://printserverhost.np.costco.com}")
	private String targetUrl;

	@Value("${printrouter.printer.path:/printers/HP%20Universal%20Printing%20PCL6/.printer}")
	private String printerPath;

	@Value("${printrouter.printer.name:HP Universal Printing PCL6}")
	private String printerName;

	@Value("${printrouter.auth.username:NP\\username}")
	private String authUsername;

	@Value("${printrouter.auth.password:}")
	private String authPassword;

	@Value("${printrouter.auth.domain:NP}")
	private String authDomain;

	@Value("${printrouter.connection.timeout:5000}")
	private int connectTimeout;

	@Value("${printrouter.connection.read-timeout:5000}")
	private int readTimeout;

	// Logger - declared at class level
	private static final Logger log = LoggerFactory.getLogger(PrintServerDiscoveryService.class);

	/**
	 * Main entry point for sending a print request
	 * 
	 * @return PrintRouteResponse with print request status
	 * @throws Exception if any error occurs during processing
	 */
	public ApiResponse<PrintServerDiscoveryResponse> sendDiscoveryRequest() {
		log.info("========================================");
		log.info("Starting IPP Print Request");
		log.debug("Target URL: {}", targetUrl);
		log.debug("Printer Name: {}", printerName);

		byte[] ippPayload = createSampleIppPayload();
		log.info("IPP Payload Length : {}", ippPayload.length);
		
		PrintServerDiscoveryResponse response=null;
		try {
		response=doPrinterDiscovery();
		}catch(Exception ex) {
			log.warn("Exception while printer discovery phase",ex);
			//refer vendorMasterService and Globalexception handler for impl.
			//throw new PrintRouteDiscoveryException();  
		}
		
		return ApiResponse.ok(response);
	}

	/**
	 * Orchestrates the printer discovery and print request process
	 * 
	 * @return PrintRouteResponse with operation status
	 * @throws Exception if any step fails
	 */
	private PrintServerDiscoveryResponse doPrinterDiscovery() throws Exception {
		log.info("==================================================");
		log.info("[DEBUG] Initiating Windows POST Print Request Task...");
		log.info("==================================================");

		setupAuthenticator();
		String rawTargetUrl = buildTargetUrl();
		String internalIppUri = buildIppUri(rawTargetUrl);
		byte[] payload = createPayload(internalIppUri);

		HttpURLConnection conn = establishConnection(rawTargetUrl);
		sendPayload(conn, payload);
		return readAndParseResponse(conn, rawTargetUrl);
	}

	/**
	 * Sets up NTLM/Basic network authenticator using externalized credentials
	 * 
	 * @throws Exception if authenticator setup fails
	 */
	private void setupAuthenticator() throws Exception {
		log.info("[DEBUG] Registering NTLM/Basic Network Authenticator credentials...");
		log.debug("[DEBUG] Username: {}", authUsername);

		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				log.info("[DEBUG] Authenticator callback triggered for user: {}\\{}", authDomain, authUsername);
				return new PasswordAuthentication(authUsername, authPassword.toCharArray());
			}
		});
	}

	/**
	 * Builds the target printer URL from externalized configuration
	 * 
	 * @return complete target URL string
	 * @throws Exception if URL construction fails
	 */
	private String buildTargetUrl() throws Exception {
		String rawTargetUrl = targetUrl + printerPath;
		log.info("[DEBUG] Target Web Server URL Location: {}", rawTargetUrl);
		new URL(rawTargetUrl); // Validate URL
		return rawTargetUrl;
	}

	/**
	 * Converts HTTP URL to IPP URI format
	 * 
	 * @param rawTargetUrl the HTTP URL
	 * @return IPP formatted URI
	 */
	private String buildIppUri(String rawTargetUrl) {
		String internalIppUri = rawTargetUrl.replace("http://", "ipp://").replace("https://", "ipps://");
		log.info("[DEBUG] Generated internal IPP Attribute URI: {}", internalIppUri);
		return internalIppUri;
	}

	/**
	 * Creates IPP request payload
	 * 
	 * @param internalIppUri the IPP URI for the request
	 * @return binary payload bytes
	 * @throws Exception if payload creation fails
	 */
	private byte[] createPayload(String internalIppUri) throws Exception {
		log.info("[DEBUG] Compiling IPP request binary payload sequence...");
		byte[] data = PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, internalIppUri);
		log.info("[DEBUG] Total compiled IPP payload binary size: {} bytes.", data.length);
		return data;
	}

	/**
	 * Establishes HTTP connection to the printer server with externalized timeouts
	 * 
	 * @param rawTargetUrl the target URL
	 * @return configured HttpURLConnection
	 * @throws Exception if connection fails
	 */
	private HttpURLConnection establishConnection(String rawTargetUrl) throws Exception {
		log.info("[DEBUG] Opening HttpURLConnection network stream...");
		log.debug("[DEBUG] Connection timeout: {} ms", connectTimeout);
		log.debug("[DEBUG] Read timeout: {} ms", readTimeout);

		HttpURLConnection conn = (HttpURLConnection) new URL(rawTargetUrl).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);

		return conn;
	}

	/**
	 * Sends payload to the printer server
	 * 
	 * @param conn    the HTTP connection
	 * @param payload the data to send
	 * @throws Exception if transmission fails
	 */
	private void sendPayload(HttpURLConnection conn, byte[] payload) throws Exception {
		log.info("[DEBUG] Opening Output Stream pipeline to write binary contents...");

		conn.setRequestProperty("Content-Type", "application/ipp");
		conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

		log.info("[DEBUG] Outbound Request Headers Configured:");
		log.info("        -> Content-Type: application/ipp");
		log.info("        -> Content-Length: {}", payload.length);

		try (OutputStream os = conn.getOutputStream()) {
			os.write(payload);
			os.flush();
			log.info("[DEBUG] All {} bytes successfully flushed to network buffers.", payload.length);
		}
	}

	/**
	 * Reads and parses the response from the server
	 * 
	 * @param conn         the HTTP connection
	 * @param rawTargetUrl the request URL
	 * @return PrintRouteResponse with parsed response data
	 * @throws Exception if response reading fails
	 */
	private PrintServerDiscoveryResponse readAndParseResponse(HttpURLConnection conn, String rawTargetUrl) throws Exception {
		log.info("[DEBUG] Awaiting remote web server response signals...");

		int responseCode = conn.getResponseCode();
		String responseMessage = conn.getResponseMessage();

		logResponseHeaders(responseCode, responseMessage, conn);

		InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
		byte[] responseBytes = is != null ? is.readAllBytes() : new byte[0];

		return parseResponse(responseCode, responseBytes, rawTargetUrl);
	}

	/**
	 * Logs response headers and status information
	 * 
	 * @param responseCode    the HTTP response code
	 * @param responseMessage the HTTP response message
	 * @param conn            the HTTP connection with headers
	 * @throws Exception if header extraction fails
	 */
	private void logResponseHeaders(int responseCode, String responseMessage, HttpURLConnection conn) throws Exception {
		log.info("\n==================================================");
		log.info("SERVER RESPONSE HEADERS & STATUS");
		log.info("==================================================");
		log.info("HTTP Status Code:    {}", responseCode);
		log.info("HTTP Status Message: {}", responseMessage);

		Map<String, List<String>> headers = conn.getHeaderFields();
		headers.entrySet().stream().filter(e -> e.getKey() != null)
				.forEach(e -> log.info("   Header: {} = {}", e.getKey(), e.getValue()));
	}

	/**
	 * Parses the response based on HTTP status code
	 * 
	 * @param responseCode  the HTTP response code
	 * @param responseBytes the response body bytes
	 * @param rawTargetUrl  the request URL
	 * @return PrintRouteResponse with parsed status and message
	 */
	private PrintServerDiscoveryResponse parseResponse(int responseCode, byte[] responseBytes, String rawTargetUrl) {
		PrintServerDiscoveryResponse response = new PrintServerDiscoveryResponse();
		response.setHttpStatus(responseCode);
		response.setUrl(rawTargetUrl);

		if (responseCode == 500) {
			return handleServerError(response, responseBytes);
		} else if (responseCode == 200 || responseCode == 201) {
			return handleSuccess(response, responseBytes);
		} else {
			return handlePartialSuccess(response, responseCode);
		}
	}

	/**
	 * Handles 500 server error response
	 * 
	 * @param response      the response object to populate
	 * @param responseBytes the error response body
	 * @return populated PrintRouteResponse
	 */
	private PrintServerDiscoveryResponse handleServerError(PrintServerDiscoveryResponse response, byte[] responseBytes) {
		log.error("\n[CRITICAL ERROR REPORT FROM SERVER]:");
		String textDump = new String(responseBytes, StandardCharsets.UTF_8);
		log.error(textDump);

		response.setStatus("FAILED");
		response.setErrorType("SERVER_ERROR");
		response.setMessage(textDump);
		return response;
	}

	/**
	 * Handles successful (200/201) response
	 * 
	 * @param response      the response object to populate
	 * @param responseBytes the response body
	 * @return populated PrintRouteResponse
	 */
	private PrintServerDiscoveryResponse handleSuccess(PrintServerDiscoveryResponse response, byte[] responseBytes) {
		log.info("\n[SUCCESS / BINARY RESPONSE COLLECTED]:");
		String hexValue = responseBytes.length > 0 ? String.format("0x%02X", responseBytes[0]) : "Empty";
		log.info("First few bytes raw: {}", hexValue);

		response.setStatus(SUCCESS);
		response.setMessage("IPP request transmitted successfully");
		return response;
	}

	/**
	 * Handles partial success (non-error, non-success codes)
	 * 
	 * @param response     the response object to populate
	 * @param responseCode the HTTP status code
	 * @return populated PrintRouteResponse
	 */
	private PrintServerDiscoveryResponse handlePartialSuccess(PrintServerDiscoveryResponse response, int responseCode) {
		response.setStatus("PARTIAL_SUCCESS");
		response.setMessage("Server returned status code: " + responseCode);

		if (responseCode >= 400) {
			log.warn("[WARN] Remote system returned an error state code");
		} else {
			log.info("[INFO] Server request processed with status: {}", responseCode);
		}

		return response;
	}

	/**
	 * Creates a sample IPP payload for testing
	 * 
	 * @return sample payload bytes
	 */
	private byte[] createSampleIppPayload() {
		return new byte[] { 0x02, 0x00, 0x00, 0x02 };
	}
}