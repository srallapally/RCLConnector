package org.example;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationClass;
import org.identityconnectors.framework.spi.ConfigurationProperty;

@ConfigurationClass(skipUnsupported = true)
public class RCLConfiguration  extends AbstractConfiguration {
    private String idmHost;
    private String idmPort;
    private String idmUserName;
    private GuardedString idmPassword;

    private String idmUserFilter;

    @ConfigurationProperty(order = 1, displayMessageKey = "idmhost.key",groupMessageKey = "basic.group", helpMessageKey = "idmhost.help",
            required = true, confidential = false)
    public String getIdmHost(){
        return idmHost;
    }

    public void setIdmHost(String idmHost) {
        this.idmHost = idmHost;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "idmport.key",groupMessageKey = "basic.group", helpMessageKey = "idmport.help",
            required = true, confidential = false)
    public String getIdmPort(){
        return idmPort;
    }

    public void setIdmPort(String idmPort) {
        this.idmPort = idmPort;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "idmuser.key",groupMessageKey = "basic.group", helpMessageKey = "idmuser.help",
            required = true, confidential = false)
    public String getIdmUserName(){
        return idmUserName;
    }

    public void setIdmUserName(String idmUserName) {
        this.idmUserName = idmUserName;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "idmpassword.key",groupMessageKey = "basic.group", helpMessageKey = "idmpassword.help",
            required = true, confidential = true)
    public GuardedString getIdmPassword(){
        return idmPassword;
    }

    public void setIdmPassword(GuardedString idmPassword) {
        this.idmPassword = idmPassword;
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "idmuserfilter.key",groupMessageKey = "basic.group", helpMessageKey = "idmuserfilter.help",
            required = false, confidential = false)
    public String getIdmUserFilter(){
        return idmUserFilter;
    }

    public void setIdmUserFilter(String idmUserFilter) {
        this.idmUserFilter = idmUserFilter;
    }

    public RCLConfiguration () {
    }

    public void validate(){}

    public void release() {}

}
