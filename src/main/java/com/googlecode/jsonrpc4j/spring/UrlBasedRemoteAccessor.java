package com.googlecode.jsonrpc4j.spring;

/**
 * Replacement for org.springframework.remoting.support.UrlBasedRemoteAccessor
 * which was removed in Spring 6+.
 * <p>
 * This base class provides the serviceUrl and serviceInterface properties
 * that were previously inherited from Spring's remoting support.
 */
public abstract class UrlBasedRemoteAccessor {

    private String serviceUrl;
    private Class<?> serviceInterface;

    /**
     * Set the URL of the service to access.
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * Return the URL of the service to access.
     */
    public String getServiceUrl() {
        return this.serviceUrl;
    }

    /**
     * Set the interface of the service to access.
     */
    public void setServiceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    /**
     * Return the interface of the service to access.
     */
    public Class<?> getServiceInterface() {
        return this.serviceInterface;
    }

    /**
     * Called after properties have been set.
     * Subclasses should override this to perform validation.
     */
    public void afterPropertiesSet() {
        if (getServiceUrl() == null) {
            throw new IllegalArgumentException("Property 'serviceUrl' is required");
        }
    }
}
