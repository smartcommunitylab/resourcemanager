package it.smartcommunitylab.resourcemanager.consumer.dss;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.smartcommunitylab.resourcemanager.SystemKeys;
import it.smartcommunitylab.resourcemanager.common.ConsumerException;
import it.smartcommunitylab.resourcemanager.model.Consumer;
import it.smartcommunitylab.resourcemanager.model.Registration;
import it.smartcommunitylab.resourcemanager.model.Resource;
import it.smartcommunitylab.resourcemanager.provider.dremio.DremioODBCProvider;
import it.smartcommunitylab.resourcemanager.util.OdbcUtil;

public class DSSODBCConsumer extends Consumer {

    private final static Logger _log = LoggerFactory.getLogger(DSSODBCConsumer.class);
    private static final String VALID_CHARS = "[^a-zA-Z0-9-_]+";

    public static final String TYPE = SystemKeys.TYPE_ODBC;
    public static final String ID = "dssodbc";

    // DSS connection
    private String endpoint;
    private String username;
    private String password;
    private String tenant;

    private int STATUS;

    private Registration registration;

    // filters
    private String spaceId;
    private List<String> tags;

    private DSSRestClient _client;

    public DSSODBCConsumer() {
        endpoint = "";
        username = "";
        password = "";
        tenant = "carbon.super"; // default tenant
        spaceId = "";
        tags = new ArrayList<>();
    }

    public DSSODBCConsumer(Map<String, Serializable> properties) {
        this();
        _properties = properties;
    }

    public DSSODBCConsumer(Registration reg) {
        this();
        registration = reg;
        _properties = reg.getPropertiesMap();
        spaceId = reg.getSpaceId();
        tags = reg.getTags();
    }

    // DSS additional properties
    private Map<String, Serializable> _properties;

    public Map<String, Serializable> getProperties() {
        return _properties;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getUrl() {
        // build access url from endpoint
        return endpoint;
    }

    @Override
    public Registration getRegistration() {
        return registration;
    }

    /*
     * Init method - POST constructor since spring injects properties *after
     * creation*
     */
    @PostConstruct
    public void init() {
        _log.debug("init called");

        STATUS = SystemKeys.STATUS_UNKNOWN;

        if (_properties != null) {
            if (_properties.containsKey("endpoint")
                    && _properties.containsKey("username")
                    && _properties.containsKey("password")) {

                endpoint = _properties.get("endpoint").toString();
                username = _properties.get("username").toString();
                password = _properties.get("password").toString();

            }

            if (_properties.containsKey("tenant")) {
                tenant = _properties.get("tenant").toString();
            }
        }

        if (!endpoint.isEmpty() && !username.isEmpty() && !password.isEmpty()) {
            _client = new DSSRestClient(endpoint, tenant, username, password);

            STATUS = SystemKeys.STATUS_READY;
        }
        _log.debug("init status is " + String.valueOf(STATUS));

    }

    @Override
    public int getStatus() {
        return STATUS;
    }

    @Override
    public void addResource(String spaceId, String userId, Resource resource) throws ConsumerException {
        if (checkSpace(resource.getSpaceId()) && checkTags(resource.getTags())) {
            _log.debug("add resource " + resource.toString());
            try {
                // fetch type from supported
                String type = getType(resource.getProvider());
                if (!type.isEmpty()) {
                    // supported
                    String uri = resource.getUri();
                    String host = OdbcUtil.getHost(uri);
                    int port = OdbcUtil.getPort(uri);
                    String uname = OdbcUtil.getUsername(uri);
                    String passw = OdbcUtil.getPassword(uri);
                    String database = OdbcUtil.getDatabase(uri);
                    String schema = OdbcUtil.getSchema(uri);
                    String table = OdbcUtil.getTable(uri);

                    StringBuilder dest = new StringBuilder();
                    dest.append(database);
                    if (!schema.isEmpty()) {
                        dest.append("/").append(schema);
                    }
                    if (!table.isEmpty()) {
                        dest.append(".\"").append(table).append("\"");
                    }

                    String destination = dest.toString();
                    String name = type.toLowerCase() + "_" + destination.replaceAll(VALID_CHARS, "");

                    name = _client.addSource(type, name, host, port, destination, uname, passw);
                    _log.debug("created source " + name);
                }
            } catch (DSSException e) {
                _log.error("dss error " + e.getMessage());
                throw new ConsumerException(e.getMessage());
            }
        }
    }

    @Override
    public void updateResource(String spaceId, String userId, Resource resource) throws ConsumerException {
        if (checkSpace(resource.getSpaceId())) {
            _log.debug("update resource " + resource.toString());
            try {
                // fetch type from supported
                String type = getType(resource.getProvider());
                if (!type.isEmpty()) {
                    // supported
                    String uri = resource.getUri();
                    String host = OdbcUtil.getHost(uri);
                    int port = OdbcUtil.getPort(uri);
                    String uname = OdbcUtil.getUsername(uri);
                    String passw = OdbcUtil.getPassword(uri);
                    String database = OdbcUtil.getDatabase(uri);
                    String schema = OdbcUtil.getSchema(uri);
                    String table = OdbcUtil.getTable(uri);

                    StringBuilder dest = new StringBuilder();
                    dest.append(database);
                    if (!schema.isEmpty()) {
                        dest.append("/").append(schema);
                    }
                    if (!table.isEmpty()) {
                        dest.append(".\"").append(table).append("\"");
                    }

                    String destination = dest.toString();
                    String name = type.toLowerCase() + "_" + destination.replaceAll(VALID_CHARS, "");

                    if (checkTags(resource.getTags())) {
                        // matches, update or create via client
                        if (_client.hasSource(name)) {
                            // exists, update
                            name = _client.updateSource(type, name, host, port, destination, uname, passw);
                            _log.debug("updated source " + name);
                        } else {
                            // create as new
                            name = _client.addSource(type, name, host, port, destination, uname, passw);
                            _log.debug("created source " + name);
                        }
                    } else {
                        if (_client.hasSource(name)) {
                            // remove previously existing resource
                            _client.deleteSource(name);
                            _log.debug("deleted source " + name);
                        }
                    }
                }
            } catch (DSSException e) {
                _log.error("dss error " + e.getMessage());
                throw new ConsumerException(e.getMessage());
            }
        }
    }

    @Override
    public void deleteResource(String spaceId, String userId, Resource resource) throws ConsumerException {
        if (checkSpace(resource.getSpaceId()) && checkTags(resource.getTags())) {
            _log.debug("delete resource " + resource.toString());
            try {
                // fetch type from supported
                String type = getType(resource.getProvider());
                if (!type.isEmpty()) {
                    // supported
                    String uri = resource.getUri();
                    String database = OdbcUtil.getDatabase(uri);
                    String schema = OdbcUtil.getSchema(uri);
                    String table = OdbcUtil.getTable(uri);

                    StringBuilder dest = new StringBuilder();
                    dest.append(database);
                    if (!schema.isEmpty()) {
                        dest.append("/").append(schema);
                    }
                    if (!table.isEmpty()) {
                        dest.append(".\"").append(table).append("\"");
                    }

                    String destination = dest.toString();
                    String name = type.toLowerCase() + "_" + destination.replaceAll(VALID_CHARS, "");

                    _client.deleteSource(name);
                    _log.debug("deleted if existing source " + name);

                }
            } catch (DSSException e) {
                _log.error("dss error " + e.getMessage());
                throw new ConsumerException(e.getMessage());
            }
        }
    }

    @Override
    public void checkResource(String spaceId, String userId, Resource resource) throws ConsumerException {
        if (checkSpace(resource.getSpaceId()) && checkTags(resource.getTags())) {
            _log.debug("check resource " + resource.toString());
            try {
                // fetch type from supported
                String type = getType(resource.getProvider());
                if (!type.isEmpty()) {
                    // supported
                    String uri = resource.getUri();
                    String database = OdbcUtil.getDatabase(uri);
                    String schema = OdbcUtil.getSchema(uri);
                    String table = OdbcUtil.getTable(uri);

                    StringBuilder dest = new StringBuilder();
                    dest.append(database);
                    if (!schema.isEmpty()) {
                        dest.append("/").append(schema);
                    }
                    if (!table.isEmpty()) {
                        dest.append(".\"").append(table).append("\"");
                    }

                    String destination = dest.toString();
                    String name = type.toLowerCase() + "_" + destination.replaceAll(VALID_CHARS, "");

                    boolean exists = _client.hasSource(name);

                    if (exists) {
                        _log.debug("check ok source " + name);
                    }
                }
            } catch (DSSException e) {
                _log.error("dss error " + e.getMessage());
                throw new ConsumerException(e.getMessage());
            }
        }
    }

    /*
     * Helpers
     */
    public String getType(String provider) {
        String type = "";
        switch (provider) {
        case DremioODBCProvider.ID:
            type = "DREMIO";
            break;
        }
        return type;
    }

    public boolean checkTags(List<String> tags) {
        boolean ret = true;
        if (!this.tags.isEmpty() || !tags.isEmpty()) {
            ret = false;
            // look for at least one match
            for (String t : tags) {
                if (this.tags.contains(t)) {
                    ret = true;
                    break;
                }
            }
        }

        return ret;
    }

    public boolean checkSpace(String space) {
        if (spaceId == null) {
            return false;
        } else if (!this.spaceId.isEmpty()) {
            return spaceId.equals(space);
        } else {
            // if global space
            return true;
        }
    }

}
