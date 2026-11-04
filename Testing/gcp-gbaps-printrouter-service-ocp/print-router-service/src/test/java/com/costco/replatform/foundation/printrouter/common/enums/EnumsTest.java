package com.costco.replatform.foundation.printrouter.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumsTest {

    @Test
    @DisplayName("DocumentType contains exactly PDF")
    void documentType_values() {
        assertThat(DocumentType.values()).containsExactly(DocumentType.PDF);
        assertThat(DocumentType.valueOf("PDF")).isSameAs(DocumentType.PDF);
    }

    @Test
    @DisplayName("PrinterType contains exactly ZEBRA and LASER, in order")
    void printerType_values() {
        assertThat(PrinterType.values())
                .containsExactly(PrinterType.ZEBRA, PrinterType.LASER);
        assertThat(PrinterType.valueOf("LASER")).isSameAs(PrinterType.LASER);
    }

    @Test
    @DisplayName("PrintType contains all 7 print types, in order")
    void printType_values() {
        assertThat(PrintType.values()).containsExactly(
                PrintType.TABLE,
                PrintType.RTLABEL,
                PrintType.MANIFEST,
                PrintType.SHIPPING_LABEL,
                PrintType.SHIPPING_DOC,
                PrintType.BILL_OF_LANDING,
                PrintType.HANDLING_INSTRUCTION);
        assertThat(PrintType.valueOf("MANIFEST")).isSameAs(PrintType.MANIFEST);
    }

    @Test
    @DisplayName("PrintRequestStatus contains all 5 statuses, in order")
    void printRequestStatus_values() {
        assertThat(PrintRequestStatus.values()).containsExactly(
                PrintRequestStatus.PRINT_REQUESTED,
                PrintRequestStatus.PRINT_REQUEST_FAILED,
                PrintRequestStatus.PRINTER_NOT_FOUND,
                PrintRequestStatus.PRINTER_CONNECTION_ERROR,
                PrintRequestStatus.PRINTER_AUTHENTICATION_FAILED);
        assertThat(PrintRequestStatus.valueOf("PRINT_REQUESTED"))
                .isSameAs(PrintRequestStatus.PRINT_REQUESTED);
    }

    @Test
    @DisplayName("PrintDiscoveryStatus contains all 5 statuses, in order")
    void printDiscoveryStatus_values() {
        assertThat(PrintDiscoveryStatus.values()).containsExactly(
                PrintDiscoveryStatus.SUCCESS,
                PrintDiscoveryStatus.FAILED,
                PrintDiscoveryStatus.NOT_FOUND,
                PrintDiscoveryStatus.CONNECTION_ERROR,
                PrintDiscoveryStatus.AUTHENTICATION_FAILED);
        assertThat(PrintDiscoveryStatus.valueOf("SUCCESS"))
                .isSameAs(PrintDiscoveryStatus.SUCCESS);
    }

    @Test
    @DisplayName("valueOf with an unknown name throws IllegalArgumentException")
    void valueOf_unknownName_throws() {
        assertThatThrownBy(() -> PrintType.valueOf("NOT_A_PRINT_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}