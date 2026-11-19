package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: the REAL JNDI code in ActiveDirectoryDiscoveryService binds to and
 * searches a REAL LDAP server (UnboundID in-memory) that stands in for Active Directory.
 */
class ActiveDirectoryDiscoveryServiceIT {

    private static final String BASE_DN = "DC=test,DC=invalid";
    private static final String PRINTERS_OU = "OU=Printers," + BASE_DN;
    private static final String BIND_DN = "CN=svc-print," + BASE_DN;
    private static final String BIND_PASSWORD = "test-bind-password";

    private static InMemoryDirectoryServer directory;

    private ActiveDirectoryDiscoveryService service;

    @BeforeAll
    static void startDirectory()    throws Exception {
        InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE_DN);
        config.setSchema(null); // accept AD attribute names (printShareName, driverName, ...) without AD's schema
        config.addAdditionalBindCredentials(BIND_DN, BIND_PASSWORD);
        config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0)); // random free port

        directory = new InMemoryDirectoryServer(config);
        directory.startListening();
    }

    @AfterAll
    static void stopDirectory() {
        directory.shutDown(true);
    }

    @BeforeEach
    void setUp()    throws Exception {
        directory.clear();
        directory.add("dn: " + BASE_DN, "objectClass: top", "objectClass: domain", "dc: test");
        directory.add("dn: " + PRINTERS_OU, "objectClass: top", "objectClass: organizationalUnit", "ou: Printers");

        service = new ActiveDirectoryDiscoveryService();
        ReflectionTestUtils.setField(service, "dcProtocol", "ldap://");
        ReflectionTestUtils.setField(service, "dcHost", "localhost");
        ReflectionTestUtils.setField(service, "dcPort", ":" + directory.getListenPort());
        ReflectionTestUtils.setField(service, "bindUser", BIND_DN);
        ReflectionTestUtils.setField(service, "bindPassword", BIND_PASSWORD);
        ReflectionTestUtils.setField(service, "domainDn", BASE_DN);
    }

    /** Adds one AD-style print queue entry; a null driver leaves driverName out. */
    private static void addPrintQueue(String cn, String location, String driver)    throws Exception {
        List<String> ldif = new ArrayList<>(List.of(
                "dn: CN=" + cn + "," + PRINTERS_OU,
                "objectClass: printQueue",
                "objectCategory: printQueue",
                "cn: " + cn,
                "printShareName: " + cn.toUpperCase(),
                "shortServerName: PRINTSRV01",
                "location: " + location));
        if (driver != null) {
            ldif.add("driverName: " + driver);
        }
        directory.add(ldif.toArray(new String[0]));
    }

    private List<PrinterMetadata> discover(String location) {
        ApiResponse<ActiveDirectoryDiscoveryResponse> result = service.discoverPrintersByLocation(location);
        assertThat(result.isSuccess()).isTrue();
        return result.getResponse().getPrinterList();
    }

    // Search and mapping against a live directory

    @Test
    @DisplayName("Location search returns only that warehouse's queues, with all attributes mapped")
    void locationSearch_returnsMatchingQueuesOnly()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", "HP Universal Printing PCL6");
        addPrintQueue("Zebra1", "01680/LA/WHS/RECEIVING", "ZDesigner ZT410");
        addPrintQueue("Laser9", "00999/WA/WHS/MGROFC", "HP Universal Printing PCL6");

        List<PrinterMetadata> printers = discover("01680");

        assertThat(printers).extracting(PrinterMetadata::getPrinterName)
                .containsExactlyInAnyOrder("Laser1", "Zebra1");
        PrinterMetadata laser = printers.stream()
                .filter(p -> p.getPrinterName().equals("Laser1")).findFirst().orElseThrow();
        assertThat(laser.getShareName()).isEqualTo("LASER1");
        assertThat(laser.getServer()).isEqualTo("PRINTSRV01");
        assertThat(laser.getLocation()).isEqualTo("01680/LA/WHS/MGROFC");
        assertThat(laser.getModel()).isEqualTo("HP Universal Printing PCL6");
    }

    @Test
    @DisplayName("Null location returns every print queue - but never non-printer entries")
    void nullLocation_returnsAllPrintQueuesOnly()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", "HP");
        addPrintQueue("Laser9", "00999/WA/WHS/MGROFC", "HP");
        directory.add("dn: CN=Some User," + BASE_DN, "objectClass: person",
                "objectCategory: person", "cn: Some User", "location: 01680/LA/WHS/MGROFC");

        assertThat(discover(null)).extracting(PrinterMetadata::getPrinterName)
                .containsExactlyInAnyOrder("Laser1", "Laser9");
    }

    @Test
    @DisplayName("Queue without driverName -> model is an empty string")
    void missingAttribute_mapsToEmptyString()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", null);

        assertThat(discover("01680").get(0).getModel()).isEmpty();
    }

    @Test
    @DisplayName("Unknown location -> empty list, not null")
    void unknownLocation_returnsEmptyList()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", "HP");

        assertThat(discover("77777")).isNotNull().isEmpty();
    }

    // Security and scale

    @Test
    @DisplayName("LDAP injection: location '*' is escaped, so it does NOT match every queue")
    void wildcardLocation_isEscaped_notAWildcard()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", "HP");
        addPrintQueue("Laser9", "00999/WA/WHS/MGROFC", "HP");

        // Unescaped, "(location=***)" would match ALL queues. Escaped, it looks for a literal '*'.
        assertThat(discover("*")).isEmpty();
    }

    @Test
    @DisplayName("1,005 queues (> PAGE_SIZE of 1000) -> all returned via the paged-results cookie loop")
    void moreThanOnePage_allResultsReturned()    throws Exception {
        for (int i = 0; i < 1005; i++) {
            addPrintQueue("Q" + i, "05000/TX/WHS/DOCK", "Zebra");
        }

        assertThat(discover("05000")).hasSize(1005);
    }

    // Connection failures (CURRENT BEHAVIOUR: reported as ok() with a null list)

    @Test
    @DisplayName("CURRENT BEHAVIOUR: wrong bind password -> real AuthenticationException -> ok(null)")
    void wrongPassword_returnsNullList()    throws Exception {
        addPrintQueue("Laser1", "01680/LA/WHS/MGROFC", "HP");
        ReflectionTestUtils.setField(service, "bindPassword", "wrong-password");

        assertThat(discover("01680")).isNull();
    }

    @Test
    @DisplayName("CURRENT BEHAVIOUR: directory unreachable -> real CommunicationException -> ok(null)")
    void unreachableDirectory_returnsNullList() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // free port; nothing listens once the socket closes
        }
        ReflectionTestUtils.setField(service, "dcPort", ":" + closedPort);

        assertThat(discover("01680")).isNull();
    }
}