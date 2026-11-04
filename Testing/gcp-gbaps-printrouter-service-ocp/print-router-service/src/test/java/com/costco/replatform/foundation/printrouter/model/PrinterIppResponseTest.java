package com.costco.replatform.foundation.printrouter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterIppResponseTest {

    @Nested
    @DisplayName("Record")
    class RecordTests {

        @Test
        @DisplayName("Canonical constructor exposes every component through its accessor")
        void accessors_returnConstructorValues() {
            PrinterIppResponse.ResponseData data = new PrinterIppResponse.ResponseData();

            PrinterIppResponse response =
                    new PrinterIppResponse(200, 0x0000, 7, "Printed", true, data);

            assertThat(response.httpStatus()).isEqualTo(200);
            assertThat(response.ippStatus()).isEqualTo(0x0000);
            assertThat(response.requestId()).isEqualTo(7);
            assertThat(response.message()).isEqualTo("Printed");
            assertThat(response.success()).isTrue();
            assertThat(response.data()).isSameAs(data);
        }

        @Test
        @DisplayName("ippStatus and data may be null (e.g. when the HTTP call itself failed)")
        void nullableComponents_areAllowed() {
            PrinterIppResponse response =
                    new PrinterIppResponse(502, null, 0, "Bad gateway", false, null);

            assertThat(response.ippStatus()).isNull();
            assertThat(response.data()).isNull();
            assertThat(response.success()).isFalse();
        }

        @Test
        @DisplayName("Two records with the same values are equal (record value semantics)")
        void equalValues_areEqual() {
            PrinterIppResponse first =
                    new PrinterIppResponse(404, 0x0406, 3, "Not found", false, null);
            PrinterIppResponse second =
                    new PrinterIppResponse(404, 0x0406, 3, "Not found", false, null);

            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
            assertThat(first.toString()).contains("Not found");
        }
    }

    @Nested
    @DisplayName("ResponseData")
    class ResponseDataTests {

        @Test
        @DisplayName("A new ResponseData has Java default values")
        void defaults() {
            PrinterIppResponse.ResponseData data = new PrinterIppResponse.ResponseData();

            assertThat(data.isOk()).isFalse();
            assertThat(data.getTransId()).isNull();
            assertThat(data.getStatus()).isZero();
            assertThat(data.getMessage()).isNull();
        }

        @Test
        @DisplayName("Setters and getters round-trip every field")
        void settersAndGetters_roundTrip() {
            PrinterIppResponse.ResponseData data = new PrinterIppResponse.ResponseData();

            data.setOk(true);
            data.setTransId("TX-123");
            data.setStatus(201);
            data.setMessage("Created");

            assertThat(data.isOk()).isTrue();
            assertThat(data.getTransId()).isEqualTo("TX-123");
            assertThat(data.getStatus()).isEqualTo(201);
            assertThat(data.getMessage()).isEqualTo("Created");
        }

        @Test
        @DisplayName("setStatus(null) on a new object leaves status at 0")
        void setStatusNull_onNewObject_keepsZero() {
            PrinterIppResponse.ResponseData data = new PrinterIppResponse.ResponseData();

            data.setStatus(null);

            assertThat(data.getStatus()).isZero();
        }

        @Test
        @DisplayName("setStatus(null) after a value was set keeps the previous value")
        void setStatusNull_afterValue_keepsPreviousValue() {
            PrinterIppResponse.ResponseData data = new PrinterIppResponse.ResponseData();
            data.setStatus(400);

            data.setStatus(null);

            assertThat(data.getStatus()).isEqualTo(400);
        }
    }
}