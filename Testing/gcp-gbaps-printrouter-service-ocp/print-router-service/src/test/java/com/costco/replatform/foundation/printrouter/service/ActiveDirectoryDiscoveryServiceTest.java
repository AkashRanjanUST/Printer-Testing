package com.costco.replatform.foundation.printrouter.service;

import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.util.ReflectionTestUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsResponseControl;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveDirectoryDiscoveryServiceTest {

    private static final String BASE_DN = "DC=test,DC=invalid";
    private static final String PLAIN_FILTER = "(objectCategory=printQueue)";

    private ActiveDirectoryDiscoveryService service;
    private final AtomicReference<List<?>> constructorArgs = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        service = new ActiveDirectoryDiscoveryService();
        // The 6 @Value fields have no defaults, so set them as Spring would.
        ReflectionTestUtils.setField(service, "dcHost", "ldap.test.invalid");
        ReflectionTestUtils.setField(service, "dcPort", ":636");
        ReflectionTestUtils.setField(service, "dcProtocol", "ldaps://");
        ReflectionTestUtils.setField(service, "bindUser", "test-bind-user@test.invalid");
        ReflectionTestUtils.setField(service, "bindPassword", "test-bind-password");
        ReflectionTestUtils.setField(service, "domainDn", BASE_DN);
    }

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** A simple NamingEnumeration over fixed results, optionally failing at the end. */
    private static final class FakeEnumeration implements NamingEnumeration<SearchResult> {
        private final Iterator<SearchResult> iterator;
        private final NamingException failAtEnd;
        private boolean closed;

        FakeEnumeration(List<SearchResult> results) {
            this(results, null);
        }

        FakeEnumeration(List<SearchResult> results, NamingException failAtEnd) {
            this.iterator = results.iterator();
            this.failAtEnd = failAtEnd;
        }

        @Override public boolean hasMore() throws NamingException {
            if (iterator.hasNext()) return true;
            if (failAtEnd != null) throw failAtEnd;
            return false;
        }
        @Override public SearchResult next() { return iterator.next(); }
        @Override public boolean hasMoreElements() { return iterator.hasNext(); }
        @Override public SearchResult nextElement() { return iterator.next(); }
        @Override public void close() { closed = true; }

        boolean isClosed() { return closed; }
    }

    /** A search result for one print queue; null values are left out of the attributes. */
    private static SearchResult printer(String cn, String share, String server, String location, String driver) {
        BasicAttributes attrs = new BasicAttributes(true);
        if (cn != null) attrs.put("cn", cn);
        if (share != null) attrs.put("printsharename", share);
        if (server != null) attrs.put("shortservername", server);
        if (location != null) attrs.put("location", location);
        if (driver != null) attrs.put("drivername", driver);
        return new SearchResult("cn=" + cn, null, attrs);
    }

    private static PagedResultsResponseControl pagedControl(byte[] cookie) {
        PagedResultsResponseControl control = mock(PagedResultsResponseControl.class);
        when(control.getCookie()).thenReturn(cookie);
        return control;
    }

    /**
     * Intercepts "new InitialLdapContext(...)". The mock returns one enumeration per search
     * call, and one Control[] per getResponseControls() call (use Arrays.asList to allow nulls).
     */
    private MockedConstruction<InitialLdapContext> mockLdap(List<NamingEnumeration<SearchResult>> pages,
                                                            List<Control[]> responseControlsPerPage) {
        return mockConstruction(InitialLdapContext.class, (ctx, context) -> {
            constructorArgs.set(context.arguments());

            OngoingStubbing<NamingEnumeration<SearchResult>> search =
                    when(ctx.search(anyString(), anyString(), any(SearchControls.class)));
            for (NamingEnumeration<SearchResult> page : pages) {
                search = search.thenReturn(page);
            }

            if (!responseControlsPerPage.isEmpty()) {
                OngoingStubbing<Control[]> controls = when(ctx.getResponseControls());
                for (Control[] pageControls : responseControlsPerPage) {
                    controls = controls.thenReturn(pageControls);
                }
            }
        });
    }

    private String capturedFilter(InitialLdapContext ctx) throws NamingException {
        ArgumentCaptor<String> filter = ArgumentCaptor.forClass(String.class);
        verify(ctx).search(eq(BASE_DN), filter.capture(), any(SearchControls.class));
        return filter.getValue();
    }

    private static List<PrinterMetadata> printers(ApiResponse<ActiveDirectoryDiscoveryResponse> result) {
        return result.getResponse().getPrinterList();
    }

    // =====================================================================
    // 1. Connection settings
    // =====================================================================

    @Test
    @DisplayName("connect() builds the LDAP URL and environment from the @Value fields")
    void connect_usesConfiguredLdapEnvironment() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation("WH-1");

            Hashtable<?, ?> env = (Hashtable<?, ?>) constructorArgs.get().get(0);
            assertThat(env.get(Context.INITIAL_CONTEXT_FACTORY)).isEqualTo("com.sun.jndi.ldap.LdapCtxFactory");
            assertThat(env.get(Context.PROVIDER_URL)).isEqualTo("ldaps://ldap.test.invalid:636");
            assertThat(env.get(Context.SECURITY_AUTHENTICATION)).isEqualTo("simple");
            assertThat(env.get(Context.SECURITY_PRINCIPAL)).isEqualTo("test-bind-user@test.invalid");
            assertThat(env.get(Context.SECURITY_CREDENTIALS)).isEqualTo("test-bind-password");
            assertThat(env.get("com.sun.jndi.ldap.connect.timeout")).isEqualTo("5000");
            assertThat(env.get(Context.REFERRAL)).isEqualTo("ignore");
            assertThat(constructorArgs.get().get(1)).as("no connection controls").isNull();

            verify(mocked.constructed().get(0)).close();
        }
    }

    // =====================================================================
    // 2. LDAP filter building
    // =====================================================================

    @Test
    @DisplayName("Location -> filter restricted to that location; subtree search of the 5 attributes")
    void location_buildsLocationFilter() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation("WH-123");

            InitialLdapContext ctx = mocked.constructed().get(0);
            assertThat(capturedFilter(ctx))
                    .isEqualTo("(&(objectCategory=printQueue)(location=*WH-123*))");

            ArgumentCaptor<SearchControls> controls = ArgumentCaptor.forClass(SearchControls.class);
            verify(ctx).search(anyString(), anyString(), controls.capture());
            assertThat(controls.getValue().getSearchScope()).isEqualTo(SearchControls.SUBTREE_SCOPE);
            assertThat(controls.getValue().getReturningAttributes())
                    .containsExactly("cn", "printsharename", "shortservername", "location", "drivername");
        }
    }

    @Test
    @DisplayName("Location with surrounding spaces is trimmed")
    void location_isTrimmed() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation("  WH-123  ");

            assertThat(capturedFilter(mocked.constructed().get(0)))
                    .isEqualTo("(&(objectCategory=printQueue)(location=*WH-123*))");
        }
    }

    @Test
    @DisplayName("Null location -> plain printQueue filter")
    void nullLocation_usesPlainFilter() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation(null);

            assertThat(capturedFilter(mocked.constructed().get(0))).isEqualTo(PLAIN_FILTER);
        }
    }

    @Test
    @DisplayName("Blank location -> plain printQueue filter")
    void blankLocation_usesPlainFilter() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation("   ");

            assertThat(capturedFilter(mocked.constructed().get(0))).isEqualTo(PLAIN_FILTER);
        }
    }

    @Test
    @DisplayName("LDAP special characters \\ * ( ) NUL are escaped (prevents LDAP injection)")
    void specialCharacters_areEscaped() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            service.discoverPrintersByLocation("a\\b*c(d)e\u0000f");

            assertThat(capturedFilter(mocked.constructed().get(0)))
                    .isEqualTo("(&(objectCategory=printQueue)(location=*a\\5cb\\2ac\\28d\\29e\\00f*))");
        }
    }

    // =====================================================================
    // 3. Mapping search results to PrinterMetadata
    // =====================================================================

    @Test
    @DisplayName("All five LDAP attributes are mapped to PrinterMetadata")
    void attributes_mappedToPrinterMetadata() throws Exception {
        SearchResult laser = printer("Laser1", "LASER1", "PRINTSRV01", "WH-123 Receiving", "HP LaserJet");

        try (MockedConstruction<InitialLdapContext> ignored =
                     mockLdap(List.of(new FakeEnumeration(List.of(laser))), List.of())) {

            ApiResponse<ActiveDirectoryDiscoveryResponse> result = service.discoverPrintersByLocation("WH-123");

            assertThat(result.isSuccess()).isTrue();
            assertThat(printers(result)).hasSize(1);
            PrinterMetadata printer = printers(result).get(0);
            assertThat(printer.getPrinterName()).isEqualTo("Laser1");
            assertThat(printer.getShareName()).isEqualTo("LASER1");
            assertThat(printer.getServer()).isEqualTo("PRINTSRV01");
            assertThat(printer.getLocation()).isEqualTo("WH-123 Receiving");
            assertThat(printer.getModel()).isEqualTo("HP LaserJet");
        }
    }

    @Test
    @DisplayName("Missing attributes become empty strings, not null")
    void missingAttributes_becomeEmptyStrings() throws Exception {
        SearchResult sparse = printer("Zebra1", null, null, null, null);

        try (MockedConstruction<InitialLdapContext> ignored =
                     mockLdap(List.of(new FakeEnumeration(List.of(sparse))), List.of())) {

            PrinterMetadata printer = printers(service.discoverPrintersByLocation("WH-1")).get(0);

            assertThat(printer.getPrinterName()).isEqualTo("Zebra1");
            assertThat(printer.getShareName()).isEmpty();
            assertThat(printer.getServer()).isEmpty();
            assertThat(printer.getLocation()).isEmpty();
            assertThat(printer.getModel()).isEmpty();
        }
    }

    @Test
    @DisplayName("An attribute that throws NamingException on read becomes an empty string")
    void attributeReadFailure_becomesEmptyString() throws Exception {
        Attribute broken = mock(Attribute.class);
        when(broken.get()).thenThrow(new NamingException("cannot read"));
        Attributes attrs = mock(Attributes.class);
        when(attrs.get("drivername")).thenReturn(broken); // all other names return null
        SearchResult result = new SearchResult("cn=broken", null, attrs);

        try (MockedConstruction<InitialLdapContext> ignored =
                     mockLdap(List.of(new FakeEnumeration(List.of(result))), List.of())) {

            PrinterMetadata printer = printers(service.discoverPrintersByLocation("WH-1")).get(0);

            assertThat(printer.getModel()).isEmpty();
            assertThat(printer.getPrinterName()).isEmpty();
        }
    }

    @Test
    @DisplayName("No search results -> empty list (not null)")
    void noResults_returnsEmptyList() throws Exception {
        try (MockedConstruction<InitialLdapContext> ignored =
                     mockLdap(List.of(new FakeEnumeration(List.of())), List.of())) {

            assertThat(printers(service.discoverPrintersByLocation("WH-1"))).isNotNull().isEmpty();
        }
    }

    // =====================================================================
    // 4. Enumeration and paging
    // =====================================================================

    @Test
    @DisplayName("PartialResultException (referral) keeps results read so far; enumeration is closed")
    void partialResult_keepsEarlierResults() throws Exception {
        FakeEnumeration enumeration = new FakeEnumeration(
                List.of(printer("Laser1", "L1", "S1", "WH-1", "HP")),
                new PartialResultException("referral skipped"));

        try (MockedConstruction<InitialLdapContext> ignored = mockLdap(List.of(enumeration), List.of())) {

            List<PrinterMetadata> result = printers(service.discoverPrintersByLocation("WH-1"));

            assertThat(result).extracting(PrinterMetadata::getPrinterName).containsExactly("Laser1");
            assertThat(enumeration.isClosed()).isTrue();
        }
    }

    @Test
    @DisplayName("Two pages: follows the paging cookie, ignores non-paging controls, stops at null controls")
    void multiplePages_followsCookie() throws Exception {
        FakeEnumeration page1 = new FakeEnumeration(List.of(printer("Laser1", "L1", "S1", "WH-1", "HP")));
        FakeEnumeration page2 = new FakeEnumeration(List.of(printer("Zebra1", "Z1", "S1", "WH-1", "Zebra")));
        Control otherControl = mock(Control.class);
        Control[] page1Controls = {otherControl, pagedControl(new byte[]{1, 2, 3})};

        try (MockedConstruction<InitialLdapContext> mocked =
                     mockLdap(List.of(page1, page2), Arrays.asList(page1Controls, null))) {

            List<PrinterMetadata> result = printers(service.discoverPrintersByLocation("WH-1"));

            assertThat(result).extracting(PrinterMetadata::getPrinterName).containsExactly("Laser1", "Zebra1");
            InitialLdapContext ctx = mocked.constructed().get(0);
            verify(ctx, times(2)).setRequestControls(any());
            verify(ctx, times(2)).search(anyString(), anyString(), any(SearchControls.class));
            assertThat(page1.isClosed()).isTrue();
            assertThat(page2.isClosed()).isTrue();
        }
    }

    @Test
    @DisplayName("Paging control with an empty cookie stops paging after one page")
    void emptyCookie_stopsPaging() throws Exception {
        Control[] lastPage = {pagedControl(new byte[0])};

        try (MockedConstruction<InitialLdapContext> mocked = mockLdap(
                List.of(new FakeEnumeration(List.of(printer("Laser1", "L1", "S1", "WH-1", "HP")))),
                Arrays.asList(new Control[][]{lastPage}))) {

            List<PrinterMetadata> result = printers(service.discoverPrintersByLocation("WH-1"));

            assertThat(result).hasSize(1);
            verify(mocked.constructed().get(0), times(1))
                    .search(anyString(), anyString(), any(SearchControls.class));
        }
    }

    // =====================================================================
    // 5. Error handling
    // =====================================================================

    @Test
    @DisplayName("CURRENT BEHAVIOUR: NamingException during search -> ok() with null list; context closed")
    void namingException_returnsOkWithNullList() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked = mockConstruction(InitialLdapContext.class,
                (ctx, context) -> when(ctx.search(anyString(), anyString(), any(SearchControls.class)))
                        .thenThrow(new NamingException("LDAP down")))) {

            ApiResponse<ActiveDirectoryDiscoveryResponse> result = service.discoverPrintersByLocation("WH-1");

            assertThat(result.isSuccess()).isTrue(); // failure is not visible to the caller
            assertThat(printers(result)).isNull();
            verify(mocked.constructed().get(0)).close();
        }
    }

    @Test
    @DisplayName("CURRENT BEHAVIOUR: unexpected RuntimeException -> ok() with null list; context closed")
    void unexpectedException_returnsOkWithNullList() throws Exception {
        try (MockedConstruction<InitialLdapContext> mocked = mockConstruction(InitialLdapContext.class,
                (ctx, context) -> when(ctx.search(anyString(), anyString(), any(SearchControls.class)))
                        .thenThrow(new IllegalStateException("boom")))) {

            ApiResponse<ActiveDirectoryDiscoveryResponse> result = service.discoverPrintersByLocation("WH-1");

            assertThat(result.isSuccess()).isTrue();
            assertThat(printers(result)).isNull();
            verify(mocked.constructed().get(0)).close();
        }
    }

    @Test
    @DisplayName("Connection fails (no context created) -> ok() with null list, nothing to close")
    void connectionFailure_returnsOkWithNullList() {
        try (MockedConstruction<InitialLdapContext> ignored = mockConstruction(InitialLdapContext.class,
                (ctx, context) -> {
                    throw new NamingException("cannot connect");
                })) {

            ApiResponse<ActiveDirectoryDiscoveryResponse> result = service.discoverPrintersByLocation("WH-1");

            assertThat(result.isSuccess()).isTrue();
            assertThat(printers(result)).isNull();
        }
    }

    @Test
    @DisplayName("ctx.close() throwing NamingException is logged and ignored")
    void closeFailure_isIgnored() throws Exception {
        try (MockedConstruction<InitialLdapContext> ignored = mockConstruction(InitialLdapContext.class,
                (ctx, context) -> {
                    when(ctx.search(anyString(), anyString(), any(SearchControls.class)))
                            .thenReturn(new FakeEnumeration(List.of(printer("Laser1", "L1", "S1", "WH-1", "HP"))));
                    doThrow(new NamingException("close failed")).when(ctx).close();
                })) {

            List<PrinterMetadata> result = printers(service.discoverPrintersByLocation("WH-1"));

            assertThat(result).hasSize(1);
        }
    }

    // =====================================================================
    // 6. Unused 2-argument searchPrintQueues overload (called via reflection)
    // =====================================================================

    @SuppressWarnings("unchecked")
    private List<PrinterMetadata> invokeTwoArgSearch(LdapContext ctx) throws Exception {
        Method method = ActiveDirectoryDiscoveryService.class
                .getDeclaredMethod("searchPrintQueues", LdapContext.class, String.class);
        method.setAccessible(true);
        return (List<PrinterMetadata>) method.invoke(service, ctx, BASE_DN);
    }

    @Test
    @DisplayName("2-arg overload: plain filter, two pages, ignores non-paging controls")
    void twoArgOverload_multiplePages() throws Exception {
        // Create every mock FIRST - never inside another when(...).thenReturn(...)
        Control otherControl = mock(Control.class);
        PagedResultsResponseControl nextPage = pagedControl(new byte[]{9});
        Control[] page1Controls = {otherControl, nextPage};

        LdapContext ctx = mock(LdapContext.class);
        when(ctx.search(anyString(), anyString(), any(SearchControls.class)))
                .thenReturn(new FakeEnumeration(List.of(printer("Laser1", "L1", "S1", "WH-1", "HP"))))
                .thenReturn(new FakeEnumeration(List.of(printer("Zebra1", "Z1", "S1", "WH-2", "Zebra"))));
        when(ctx.getResponseControls())
                .thenReturn(page1Controls)
                .thenReturn(null);

        List<PrinterMetadata> result = invokeTwoArgSearch(ctx);

        assertThat(result).extracting(PrinterMetadata::getPrinterName).containsExactly("Laser1", "Zebra1");
        verify(ctx, times(2)).search(eq(BASE_DN), eq(PLAIN_FILTER), any(SearchControls.class));
    }

    @Test
    @DisplayName("2-arg overload: PartialResultException is skipped; empty cookie stops paging")
    void twoArgOverload_partialResultAndEmptyCookie() throws Exception {
        // Create every mock FIRST - never inside another when(...).thenReturn(...)
        PagedResultsResponseControl lastPage = pagedControl(new byte[0]);
        Control[] lastPageControls = {lastPage};
        FakeEnumeration enumeration = new FakeEnumeration(
                List.of(printer("Laser1", "L1", "S1", "WH-1", "HP")),
                new PartialResultException("referral"));

        LdapContext ctx = mock(LdapContext.class);
        when(ctx.search(anyString(), anyString(), any(SearchControls.class))).thenReturn(enumeration);
        when(ctx.getResponseControls()).thenReturn(lastPageControls);

        List<PrinterMetadata> result = invokeTwoArgSearch(ctx);

        assertThat(result).hasSize(1);
        assertThat(enumeration.isClosed()).isTrue();
        verify(ctx, times(1)).search(anyString(), anyString(), any(SearchControls.class));
    }
}