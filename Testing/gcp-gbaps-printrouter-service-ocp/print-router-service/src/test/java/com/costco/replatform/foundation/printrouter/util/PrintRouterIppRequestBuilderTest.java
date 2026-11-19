package com.costco.replatform.foundation.printrouter.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintRouterIppRequestBuilderTest {

    private static final String PRINTER_URI = "ipp://print-server.test.invalid/printers/TestPrinter";

    @Test
    @DisplayName("Header: IPP 1.1, Get-Printer-Attributes (0x000B), request ID")
    void header_isIpp11GetPrinterAttributes() throws Exception {
        byte[] request = PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, PRINTER_URI);
        ByteBuffer buffer = ByteBuffer.wrap(request);

        assertThat(buffer.get()).as("major version").isEqualTo((byte) 0x01);
        assertThat(buffer.get()).as("minor version").isEqualTo((byte) 0x01);
        assertThat(buffer.getShort()).as("operation id").isEqualTo((short) 0x000B);
        assertThat(buffer.getInt()).as("request id").isEqualTo(1);
    }

    @Test
    @DisplayName("Body: operation group, 3 attributes in order, end tag, nothing after")
    void body_containsAttributesInOrderAndEndTag() throws Exception {
        byte[] request = PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, PRINTER_URI);
        ByteBuffer buffer = ByteBuffer.wrap(request);
        buffer.position(8);

        assertThat(buffer.get()).as("operation-attributes group tag").isEqualTo((byte) 0x01);

        assertAttribute(buffer, (byte) 0x47, "attributes-charset", "utf-8");
        assertAttribute(buffer, (byte) 0x48, "attributes-natural-language", "en-us");
        assertAttribute(buffer, (byte) 0x45, "printer-uri", PRINTER_URI);

        assertThat(buffer.get()).as("end-of-attributes tag").isEqualTo((byte) 0x03);
        assertThat(buffer.hasRemaining()).as("no trailing bytes").isFalse();
    }

    @Test
    @DisplayName("Total length matches the IPP layout exactly")
    void totalLength_matchesLayout() throws Exception {
        byte[] request = PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, PRINTER_URI);

        int expected = 8                                         // header
                + 1                                              // group tag
                + attributeSize("attributes-charset", "utf-8")
                + attributeSize("attributes-natural-language", "en-us")
                + attributeSize("printer-uri", PRINTER_URI)
                + 1;                                             // end tag

        assertThat(request).hasSize(expected);
    }

    @Test
    @DisplayName("Request ID is written as 4 big-endian bytes")
    void requestId_isBigEndian() throws Exception {
        byte[] request = PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(0x01020304, PRINTER_URI);

        assertThat(request[4]).isEqualTo((byte) 0x01);
        assertThat(request[5]).isEqualTo((byte) 0x02);
        assertThat(request[6]).isEqualTo((byte) 0x03);
        assertThat(request[7]).isEqualTo((byte) 0x04);
    }

    @Test
    @DisplayName("Boundary: Integer.MAX_VALUE request ID round-trips")
    void requestId_maxValue() throws Exception {
        byte[] request = PrintRouterIppRequestBuilder
                .createGetPrinterAttributesRequest(Integer.MAX_VALUE, PRINTER_URI);

        assertThat(ByteBuffer.wrap(request, 4, 4).getInt()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("A null printer URI currently throws NullPointerException")
    void nullUri_throwsNpe() {
        assertThatThrownBy(() -> PrintRouterIppRequestBuilder.createGetPrinterAttributesRequest(1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Implicit public constructor can be invoked (covers the class declaration line)")
    void implicitConstructor() {
        assertThat(new PrintRouterIppRequestBuilder()).isNotNull();
    }

    // helpers: read IPP attributes back out of the byte array

    /** Reads one attribute (tag, name length, name, value length, value) and checks each part. */
    private static void assertAttribute(ByteBuffer buffer, byte expectedTag,
                                        String expectedName, String expectedValue) {
        assertThat(buffer.get()).as("value tag of %s", expectedName).isEqualTo(expectedTag);

        byte[] name = new byte[buffer.getShort()];
        buffer.get(name);
        assertThat(new String(name, StandardCharsets.US_ASCII)).isEqualTo(expectedName);

        byte[] value = new byte[buffer.getShort()];
        buffer.get(value);
        assertThat(new String(value, StandardCharsets.UTF_8))
                .as("value of %s", expectedName).isEqualTo(expectedValue);
    }

    /** tag(1) + name-length(2) + name + value-length(2) + value */
    private static int attributeSize(String name, String value) {
        return 1 + 2 + name.getBytes(StandardCharsets.US_ASCII).length
                + 2 + value.getBytes(StandardCharsets.UTF_8).length;
    }
}