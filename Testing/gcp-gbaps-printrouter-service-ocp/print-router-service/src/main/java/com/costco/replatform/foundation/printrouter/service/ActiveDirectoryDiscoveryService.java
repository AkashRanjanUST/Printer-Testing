package com.costco.replatform.foundation.printrouter.service;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.PartialResultException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.costco.replatform.foundation.printrouter.model.ActiveDirectoryDiscoveryResponse;
import com.costco.replatform.foundation.printrouter.model.ApiResponse;
import com.costco.replatform.foundation.printrouter.model.PrinterMetadata;

@Service
public class ActiveDirectoryDiscoveryService {

	private static final Logger log = LoggerFactory.getLogger(ActiveDirectoryDiscoveryService.class);

	private static final String FILTER = "(objectCategory=printQueue)";
	private static final int PAGE_SIZE = 1000;

	@Value("${jndi.ldap.host}")
	private String dcHost;

	@Value("${jndi.ldap.port}")
	private String dcPort;

	@Value("${jndi.ldap.protocol}")
	private String dcProtocol;

	@Value("${jndi.ldap.bind.user}")
	private String bindUser;

	@Value("${jndi.ldap.bind.password}")
	private String bindPassword;

	@Value("${jndi.ldap.base.dn}")
	private String domainDn;

	/**
	 * Method to get all Printers
	 * 
	 * @return
	 */
	public ApiResponse<ActiveDirectoryDiscoveryResponse> discoverPrintersByLocation(String location) {

		ActiveDirectoryDiscoveryResponse response = new ActiveDirectoryDiscoveryResponse();
		List<PrinterMetadata> printers = null;
		LdapContext ctx = null;
		try {
			ctx = connect();
			log.info("Searching print queues in Active Directory. Base DN={}", domainDn);
			printers = searchPrintQueues(ctx, domainDn,location);
			log.info("Successfully retrieved {} printer queues from Active Directory", printers.size());
		} catch (NamingException ex) {
			log.error("LDAP query failed while retrieving print queues", ex);
		} catch (Exception ex) {
			log.error("Unexpected error while retrieving print queues", ex);
		} finally {
			if (ctx != null) {
				try {
					ctx.close();
				} catch (NamingException e) {
					log.warn("Failed to close LDAP context: " + e.getMessage());
				}
			}
		}
		response.setPrinterList(printers);
		log.info("Printers::"+printers);
		return ApiResponse.ok(response);
	}

	/**
	 * Method to get Connect.
	 * 
	 * @return
	 * @throws NamingException
	 */
	private LdapContext connect() throws NamingException {

		StringBuilder ldapUrlBuilder = new StringBuilder(dcProtocol);
		ldapUrlBuilder.append(dcHost);
		ldapUrlBuilder.append(dcPort);
		String ldapUrl = ldapUrlBuilder.toString();

		Hashtable<String, Object> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, ldapUrl);
		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, bindUser);
		env.put(Context.SECURITY_CREDENTIALS, bindPassword);
		env.put("com.sun.jndi.ldap.connect.timeout", "5000");
		env.put(Context.REFERRAL, "ignore");
		log.info("Connecting to LDAP server {}", ldapUrl);
		return new InitialLdapContext(env, null);
	}

	/**
	 * Method to search Print Queue.
	 * 
	 * @param ctx
	 * @param baseDn
	 * @return
	 * @throws Exception
	 */
	private List<PrinterMetadata> searchPrintQueues(LdapContext ctx, String baseDn) throws Exception {

		List<PrinterMetadata> results = new ArrayList<>();
		SearchControls controls = new SearchControls();
		controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
		controls.setReturningAttributes(
				new String[] { "cn", "printsharename", "shortservername", "location", "drivername" });

		byte[] cookie = null;
		do {
			ctx.setRequestControls(new Control[] { new PagedResultsControl(PAGE_SIZE, cookie, Control.CRITICAL) });
			NamingEnumeration<SearchResult> enumeration = ctx.search(baseDn, FILTER, controls);

			try {

				while (enumeration.hasMore()) {
					Attributes attrs = enumeration.next().getAttributes();
					PrinterMetadata info = new PrinterMetadata();
					info.setPrinterName(getFirstValue(attrs, "cn"));
					info.setShareName(getFirstValue(attrs, "printsharename"));
					info.setServer(getFirstValue(attrs, "shortservername"));
					info.setLocation(getFirstValue(attrs, "location"));
					info.setModel(getFirstValue(attrs, "drivername"));
					results.add(info);
				}

			} catch (PartialResultException ex) {
				log.warn("Skipped LDAP referral: {}", ex.getMessage());

			} finally {
				enumeration.close();
			}

			cookie = null;
			Control[] responseControls = ctx.getResponseControls();
			if (responseControls != null) {
				for (Control control : responseControls) {
					if (control instanceof PagedResultsResponseControl) {
						cookie = ((PagedResultsResponseControl) control).getCookie();
					}
				}
			}

		} while (cookie != null && cookie.length > 0);

		return results;
	}
	
	
	private  List<PrinterMetadata> searchPrintQueues(
	        LdapContext ctx,
	        String baseDn,
	        String location) throws Exception {

	    List<PrinterMetadata> results = new ArrayList<>();

	    SearchControls controls = new SearchControls();
	    controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	    controls.setReturningAttributes(new String[]{
	            "cn", "printsharename", "shortservername", "location", "drivername"
	    });

	    // Build LDAP filter
	    String filter;
	    if (location == null || location.trim().isEmpty()) {
	        filter = "(objectCategory=printQueue)";
	    } else {
	        filter = "(&(objectCategory=printQueue)(location=*"
	                + escapeLDAPSearchFilter(location.trim())
	                + "*))";
	    }

	    byte[] cookie = null;
	    do {
	        ctx.setRequestControls(new Control[]{
	                new PagedResultsControl(PAGE_SIZE, cookie, Control.CRITICAL)
	        });

	        NamingEnumeration<SearchResult> enumeration =
	                ctx.search(baseDn, filter, controls);

	        try {
	            while (enumeration.hasMore()) {
	                Attributes attrs = enumeration.next().getAttributes();

	                PrinterMetadata info = new PrinterMetadata();
	                info.setPrinterName(getFirstValue(attrs, "cn"));
					info.setShareName(getFirstValue(attrs, "printsharename"));
					info.setServer(getFirstValue(attrs, "shortservername"));
					info.setLocation(getFirstValue(attrs, "location"));
					info.setModel(getFirstValue(attrs, "drivername"));

	                results.add(info);
	            }
	        } catch (PartialResultException e) {
	        	log.warn("Skipped referral: " + e.getMessage());
	        } finally {
	            enumeration.close();
	        }

	        cookie = null;
	        Control[] responseControls = ctx.getResponseControls();
	        if (responseControls != null) {
	            for (Control c : responseControls) {
	                if (c instanceof PagedResultsResponseControl) {
	                    cookie = ((PagedResultsResponseControl) c).getCookie();
	                }
	            }
	        }
	    } while (cookie != null && cookie.length > 0);

	    return results;
	}

	/**
	 * Method to get First Value.
	 * 
	 * @param attrs
	 * @param attributeName
	 * @return
	 */
	private String getFirstValue(Attributes attrs, String attributeName) {

		try {
			Attribute attribute = attrs.get(attributeName);
			return attribute == null ? "" : String.valueOf(attribute.get());
		} catch (NamingException ex) {
			log.debug("Failed to read LDAP attribute {}", attributeName, ex);
			return "";
		}
	}
	
	/**
	 * Method to do escape on filter.
	 * @param filter
	 * @return
	 */
	private static String escapeLDAPSearchFilter(String filter) {
	    return filter
	            .replace("\\", "\\5c")
	            .replace("*", "\\2a")
	            .replace("(", "\\28")
	            .replace(")", "\\29")
	            .replace("\0", "\\00");
	}

	
}
