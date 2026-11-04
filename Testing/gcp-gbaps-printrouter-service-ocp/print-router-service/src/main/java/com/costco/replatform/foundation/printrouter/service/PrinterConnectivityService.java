package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.config.PrinterIppProperties;
import com.costco.replatform.foundation.printrouter.exception.ErrorCode;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterIppResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PrinterConnectivityService {

	private static final short PRINT_JOB_OPERATION_ID = 0x0002;

	private static final int OPERATION_ATTRIBUTES_TAG = 0x01;
	private static final int END_OF_ATTRIBUTES_TAG = 0x03;

	private static final int NAME_WITHOUT_LANGUAGE_TAG = 0x42;
	private static final int URI_TAG = 0x45;
	private static final int CHARSET_TAG = 0x47;
	private static final int NATURAL_LANGUAGE_TAG = 0x48;
	private static final int MIME_MEDIA_TYPE_TAG = 0x49;

	private static final int MINIMUM_IPP_RESPONSE_LENGTH = 8;

	private final PrinterIppProperties properties;
	private final HttpClient httpClient;

	public PrinterConnectivityService(PrinterIppProperties properties) {
		this.properties = properties;

		this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	
	/**
	 * Sends the configured plain-text content to the configured IPP queue.
	 * @param itemNo
	 * @param printerName
	 * @param printerJobName
	 * @return
	 */
	public ApiResponse<PrinterIppResponse> sendPrintRequest(int itemNo,String printerName,String printerJobName) {
		//int requestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

		try {
			validateInput();

			byte[] ippRequest = buildIppRequest(itemNo,printerName,printerJobName);

			HttpRequest httpRequest = buildHttpRequest(ippRequest);

			HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

			PrinterIppResponse printerResponse = processResponse(httpResponse, itemNo);

			if (printerResponse.success()) {
				return ApiResponse.ok(printerResponse);
			}

			return ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(), printerResponse.message(),
					getFailureHttpStatus(printerResponse));

		} catch (IllegalArgumentException exception) {
			return ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(), exception.getMessage(),
					HttpStatus.BAD_REQUEST.value());

		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();

			return ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(), "IPP print request was interrupted",
					HttpStatus.INTERNAL_SERVER_ERROR.value());

		} catch (IOException exception) {
			return ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(),
					"IPP communication failed: " + getExceptionMessage(exception), HttpStatus.BAD_GATEWAY.value());

		} catch (Exception exception) {
			return ApiResponse.failures(ErrorCode.INVALID_REQUEST.getCode(),
					"Unexpected error while sending IPP request: " + getExceptionMessage(exception),
					HttpStatus.INTERNAL_SERVER_ERROR.value());
		}
	}

	private HttpRequest buildHttpRequest(byte[] ippRequest) {
		String authorizationHeader = buildBasicAuthorizationHeader();

		return HttpRequest.newBuilder().uri(URI.create(properties.getQueueUrl()))
				.timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
				.header("Content-Type", "application/ipp").header("Accept", "application/ipp")
				.header("Authorization", authorizationHeader).POST(HttpRequest.BodyPublishers.ofByteArray(ippRequest))
				.build();
	}

	private String buildBasicAuthorizationHeader() {
		String credentials = properties.getDomainUsername() + ":" + properties.getPassword();

		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	
	/**
	 * Method to build Request.
	 * @param itemNumber
	 * @param printerName
	 * @param printJobName
	 * @return
	 * @throws IOException
	 */
	private byte[] buildIppRequest(int itemNumber,String printerName,String printJobName) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		try (DataOutputStream output = new DataOutputStream(buffer)) {

			output.writeByte(0x01);
			output.writeByte(0x01);

			output.writeShort(PRINT_JOB_OPERATION_ID);
			output.writeInt(itemNumber);
			

			output.writeByte(OPERATION_ATTRIBUTES_TAG);

			writeStringAttribute(output, CHARSET_TAG, "attributes-charset", "utf-8");

			writeStringAttribute(output, NATURAL_LANGUAGE_TAG, "attributes-natural-language", "en");

			writeStringAttribute(output, URI_TAG, "printer-uri", properties.getQueueUrl()+printerName);

			writeStringAttribute(output, NAME_WITHOUT_LANGUAGE_TAG, "requesting-user-name",
					properties.getRequestingUser());

			writeStringAttribute(output, NAME_WITHOUT_LANGUAGE_TAG, "job-name", properties.getJobName()+printJobName);

			writeStringAttribute(output, MIME_MEDIA_TYPE_TAG, "document-format", "text/plain");

			output.writeByte(END_OF_ATTRIBUTES_TAG);

			output.write(properties.getPrintContent().getBytes(StandardCharsets.UTF_8));

			output.flush();
		}

		return buffer.toByteArray();
	}

	private void writeStringAttribute(DataOutputStream output, int valueTag, String attributeName,
			String attributeValue) throws IOException {
		byte[] nameBytes = attributeName.getBytes(StandardCharsets.US_ASCII);

		byte[] valueBytes = attributeValue.getBytes(StandardCharsets.UTF_8);

		output.writeByte(valueTag);

		output.writeShort(nameBytes.length);
		output.write(nameBytes);

		output.writeShort(valueBytes.length);
		output.write(valueBytes);
	}

	private PrinterIppResponse processResponse(HttpResponse<byte[]> response, int itemNumber) {
		int httpStatus = response.statusCode();
		byte[] responseBody = response.body();

		if (httpStatus == HttpStatus.UNAUTHORIZED.value()) {
			String authenticationScheme = response.headers().firstValue("WWW-Authenticate").orElse("Not provided");

			return buildPrinterResponse(httpStatus, null, itemNumber,
					"HTTP authentication failed. Scheme: " + authenticationScheme, false);
		}

		if (httpStatus == HttpStatus.FORBIDDEN.value()) {
			return buildPrinterResponse(httpStatus, null, itemNumber,
					"User is not authorized to print to this queue", false);
		}

		if (httpStatus == HttpStatus.NOT_FOUND.value()) {
			return buildPrinterResponse(httpStatus, null, itemNumber, "IPP queue URL was not found", false);
		}

		if (httpStatus < 200 || httpStatus >= 300) {
			return buildPrinterResponse(httpStatus, null, itemNumber,
					"IPP HTTP request failed with status " + httpStatus, false);
		}

		if (responseBody == null || responseBody.length < MINIMUM_IPP_RESPONSE_LENGTH) {
			return buildPrinterResponse(httpStatus, null, itemNumber,
					"HTTP request succeeded, but the response " + "is not a valid IPP response", false);
		}

		int ippMajorVersion = responseBody[0] & 0xFF;
		int ippMinorVersion = responseBody[1] & 0xFF;

		int ippStatus = ((responseBody[2] & 0xFF) << 8) | (responseBody[3] & 0xFF);

		int responseRequestId = ((responseBody[4] & 0xFF) << 24) | ((responseBody[5] & 0xFF) << 16)
				| ((responseBody[6] & 0xFF) << 8) | (responseBody[7] & 0xFF);

		if (responseRequestId != itemNumber) {
			return buildPrinterResponse(httpStatus, ippStatus, responseRequestId,
					"IPP response request ID mismatch. Expected: " + itemNumber + ", received: "
							+ responseRequestId,
					false);
		}

		String statusMessage = "IPP version: " + ippMajorVersion + "." + ippMinorVersion + ", status: "
				+ String.format("0x%04X", ippStatus) + " " + getIppStatusDescription(ippStatus);

		if (ippStatus <= 0x00FF) {
			return buildPrinterResponse(httpStatus, ippStatus, responseRequestId,
					"Print job accepted. " + statusMessage, true);
		}

		return buildPrinterResponse(httpStatus, ippStatus, responseRequestId, "Print job rejected. " + statusMessage,
				false);
	}

	private PrinterIppResponse buildPrinterResponse(int httpStatus, Integer ippStatus, int requestId, String message,
			boolean success) {
		return new PrinterIppResponse(httpStatus, ippStatus, requestId, message, success, null);
	}

	private int getFailureHttpStatus(PrinterIppResponse printerResponse) {
		int status = printerResponse.httpStatus();

		if (status >= 400 && status <= 599) {
			return status;
		}

		return HttpStatus.BAD_GATEWAY.value();
	}

	private String getIppStatusDescription(int ippStatus) {
		return switch (ippStatus) {
		case 0x0000 -> "successful-ok";
		case 0x0001 -> "successful-ok-ignored-or-substituted-attributes";
		case 0x0002 -> "successful-ok-conflicting-attributes";
		case 0x0400 -> "client-error-bad-request";
		case 0x0401 -> "client-error-forbidden";
		case 0x0402 -> "client-error-not-authenticated";
		case 0x0403 -> "client-error-not-authorized";
		case 0x0404 -> "client-error-not-possible";
		case 0x0406 -> "client-error-not-found";
		case 0x040A -> "client-error-document-format-not-supported";
		case 0x0500 -> "server-error-internal-error";
		case 0x0501 -> "server-error-operation-not-supported";
		case 0x0502 -> "server-error-service-unavailable";
		case 0x0503 -> "server-error-version-not-supported";
		default -> String.format("unknown-ipp-status-0x%04X", ippStatus);
		};
	}

	private void validateInput() {
		if (isBlank(properties.getQueueUrl())) {
			throw new IllegalArgumentException("Queue URL must not be empty");
		}

		URI queueUri;

		try {
			queueUri = URI.create(properties.getQueueUrl());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Queue URL is invalid", exception);
		}

		String scheme = queueUri.getScheme();

		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException("Queue URL must use HTTP or HTTPS");
		}

		if (isBlank(queueUri.getHost())) {
			throw new IllegalArgumentException("Queue URL must contain a valid host");
		}

		if (isBlank(queueUri.getPath()) || "/".equals(queueUri.getPath())) {
			throw new IllegalArgumentException("Queue URL must contain an IPP queue path");
		}

		if (isBlank(properties.getRequestingUser())) {
			throw new IllegalArgumentException("Requesting user must not be empty");
		}

		if (isBlank(properties.getDomainUsername())) {
			throw new IllegalArgumentException("Authentication username must not be empty");
		}

		if (isBlank(properties.getPassword())) {
			throw new IllegalArgumentException("Authentication password must not be empty");
		}

		if (isBlank(properties.getJobName())) {
			throw new IllegalArgumentException("Job name must not be empty");
		}

		if (isBlank(properties.getPrintContent())) {
			throw new IllegalArgumentException("Print content must not be empty");
		}

		if (properties.getConnectTimeoutSeconds() <= 0) {
			throw new IllegalArgumentException("Connect timeout must be greater than zero");
		}

		if (properties.getRequestTimeoutSeconds() <= 0) {
			throw new IllegalArgumentException("Request timeout must be greater than zero");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String getExceptionMessage(Exception exception) {
		return isBlank(exception.getMessage()) ? exception.getClass().getSimpleName() : exception.getMessage();
	}
}