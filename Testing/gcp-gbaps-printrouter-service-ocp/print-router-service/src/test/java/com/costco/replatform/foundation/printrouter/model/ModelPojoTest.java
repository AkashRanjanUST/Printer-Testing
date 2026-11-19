package com.costco.replatform.foundation.printrouter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic getter/setter round-trip test for plain model POJOs.
 * Records and classes with real logic (ApiResponse, PrinterIppResponse)
 * have their own dedicated tests.
 */
class ModelPojoTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {
            ActiveDirectoryDiscoveryResponse.class,
            ApiRequest.class,
            BolPrintData.class,
            HandlingInstructPrintData.class,
            ManifestLabelPrintData.class,
            PrinterMetadata.class,
            PrintMetadata.class,
            PrintRouteDelegateResponse.class,
            PrintRouteRequest.class,
            PrintRouteResponse.class,
            PrintServerDiscoveryResponse.class,
            RtLabelPrintData.class,
            ShippingDocPrintData.class,
            ShippingLabelPrintData.class
    })
    @DisplayName("Every property round-trips through its setter and getter")
    void allProperties_roundTrip(Class<?> pojoClass) throws Exception {
        Object pojo = pojoClass.getDeclaredConstructor().newInstance();

        PropertyDescriptor[] properties =
                Introspector.getBeanInfo(pojoClass, Object.class).getPropertyDescriptors();

        for (PropertyDescriptor property : properties) {
            Method setter = property.getWriteMethod();
            Method getter = findGetter(pojoClass, property);
            if (setter == null || getter == null) {
                continue; // read-only or write-only
            }

            Object value = sampleValue(setter.getParameterTypes()[0], property.getName());
            setter.invoke(pojo, value);

            assertThat(getter.invoke(pojo))
                    .as("%s.%s", pojoClass.getSimpleName(), property.getName())
                    .isEqualTo(value);
        }

        assertThat(pojo.toString()).isNotNull();
    }

    /** Standard getter, or an "isXxx()" getter for a Boolean wrapper field. */
    private static Method findGetter(Class<?> type, PropertyDescriptor property) {
        if (property.getReadMethod() != null) {
            return property.getReadMethod();
        }
        String name = property.getName();
        String isGetter = "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return type.getMethod(isGetter);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** A non-default sample value for the given property type. */
    private static Object sampleValue(Class<?> type, String propertyName) throws Exception {
        if (type == String.class) return "test-" + propertyName;
        if (type == Integer.class || type == int.class) return 42;
        if (type == Long.class || type == long.class) return 42L;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == Double.class || type == double.class) return 4.2d;
        if (type == BigDecimal.class) return BigDecimal.TEN;
        if (type == LocalDate.class) return LocalDate.of(2026, 1, 15);
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 1, 15, 10, 30);
        if (type == OffsetDateTime.class) return OffsetDateTime.parse("2026-01-15T10:30:00Z");
        if (type == ZonedDateTime.class) return ZonedDateTime.parse("2026-01-15T10:30:00Z");
        if (type == Instant.class) return Instant.parse("2026-01-15T10:30:00Z");
        if (type == Date.class) return new Date(0L);
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (List.class.isAssignableFrom(type)) return List.of("item-" + propertyName);
        if (Map.class.isAssignableFrom(type)) return Map.of("key", "value");
        if (type == Object.class) return "generic-" + propertyName; // e.g. ApiRequest<T>.request
        return type.getDeclaredConstructor().newInstance();        // nested POJO, e.g. PrintMetadata
    }
}