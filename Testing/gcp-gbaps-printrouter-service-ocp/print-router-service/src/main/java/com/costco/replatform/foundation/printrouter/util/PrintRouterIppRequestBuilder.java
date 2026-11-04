package com.costco.replatform.foundation.printrouter.util;

import java.io.ByteArrayOutputStream;

import java.io.DataOutputStream;

import java.io.IOException;

import java.nio.charset.StandardCharsets;

public class PrintRouterIppRequestBuilder {

    /**

     * Generates a valid, structurally compliant IPP/1.1 Get-Printer-Attributes request byte array.

     *

     * @param requestId An arbitrary unique transaction integer ID (e.g., 1).

     * @param printerUri The target network URI of the printer (e.g., "ipp://192.168.1.50/ipp/print").

     * @return A fully populated, valid IPP request byte array.

     * @throws IOException If byte stream writing fails.

     */

    public static byte[] createGetPrinterAttributesRequest(int requestId, String printerUri) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        DataOutputStream out = new DataOutputStream(baos);

        // --- 1. IPP HEADER ---

        out.writeByte(0x01); // Major Version: 1

        out.writeByte(0x01); // Minor Version: 1

        out.writeShort(0x000B); // Operation ID: Get-Printer-Attributes (0x000B)

        out.writeInt(requestId); // Request ID

        // --- 2. OPERATION ATTRIBUTES START ---

        out.writeByte(0x01); // Begin Operation Attributes Group Tag

        // Attribute 1: attributes-charset (Value Type: 0x47 = Charset)

        writeAttribute(out, (byte) 0x47, "attributes-charset", "utf-8");

        // Attribute 2: attributes-natural-language (Value Type: 0x48 = Natural Language)

        writeAttribute(out, (byte) 0x48, "attributes-natural-language", "en-us");

        // Attribute 3: printer-uri (Value Type: 0x45 = URI)

        writeAttribute(out, (byte) 0x45, "printer-uri", printerUri);

        // --- 3. TERMINATION ---

        out.writeByte(0x03); // End-of-attributes Delimiter Tag 🛑

        out.flush();

        return baos.toByteArray();

    }

    /**

     * Helper method to write an individual IPP attribute matching the binary specification layout.

     */

    private static void writeAttribute(DataOutputStream out, byte valueTag, String name, String value) throws IOException {

        out.writeByte(valueTag);

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);

        out.writeShort(nameBytes.length);

        out.write(nameBytes);

        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        out.writeShort(valueBytes.length);

        out.write(valueBytes);

    }

}


