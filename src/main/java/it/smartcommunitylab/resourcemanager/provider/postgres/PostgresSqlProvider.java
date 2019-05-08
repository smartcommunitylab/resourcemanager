package it.smartcommunitylab.resourcemanager.provider.postgres;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.smartcommunitylab.resourcemanager.SystemKeys;
import it.smartcommunitylab.resourcemanager.common.ResourceProviderException;
import it.smartcommunitylab.resourcemanager.model.Resource;
import it.smartcommunitylab.resourcemanager.model.ResourceProvider;
import it.smartcommunitylab.resourcemanager.util.SqlUtil;

@Component
public class PostgresSqlProvider extends ResourceProvider {
	private final static Logger _log = LoggerFactory.getLogger(PostgresSqlProvider.class);

	public static final String TYPE = SystemKeys.TYPE_SQL;
	public static final String ID = "postgresSql";

	private int STATUS;

	@Value("${providers.postgressql.enable}")
	private boolean enabled;

	@Value("${providers.postgressql.properties}")
	private List<String> properties;

	// postgres connection
	@Value("${providers.postgressql.host}")
	private String host;

	@Value("${providers.postgressql.port}")
	private int port;

	@Value("${providers.postgressql.ssl}")
	private boolean ssl;

	@Value("${providers.postgressql.username}")
	private String username;

	@Value("${providers.postgressql.password}")
	private String password;

	private PostgresSqlClient _client;

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public Set<String> listProperties() {
		return new HashSet<String>(properties);
	}

	/*
	 * Init method - POST constructor since spring injects properties *after
	 * creation*
	 */
	@PostConstruct
	public void init() {
		_log.info("enabled " + String.valueOf(enabled));
		STATUS = SystemKeys.STATUS_DISABLED;

		if (enabled) {
			_client = new PostgresSqlClient(host, port, ssl, username, password);
			// check postgres availability

			if (_client.ping()) {
				STATUS = SystemKeys.STATUS_READY;
			} else {
				STATUS = SystemKeys.STATUS_ERROR;
			}

		}

		_log.info("init status " + String.valueOf(STATUS));
	}

	@Override
	public int getStatus() {
		return STATUS;
	}

	@Override
	public Resource createResource(String scopeId, String userId, Map<String, Serializable> properties)
			throws ResourceProviderException {
		Resource res = new Resource();
		res.setType(TYPE);
		res.setProvider(ID);
		res.setPropertiesMap(properties);

		try {
			// generate id with limited tries
			String name = generateId(scopeId, userId);
			int retry = 0;
			boolean exists = _client.hasDatabase(name);
			while (exists && retry < 5) {
				name = generateId(scopeId, userId);
				exists = _client.hasDatabase(name);
				retry++;
			}

			if (exists) {
				throw new ResourceProviderException("error creating database");
			}

			_log.info("create database " + name + " with scope " + scopeId + " for user " + userId);

			// create database
			_client.createDatabase(name);

			// create username = dbname
			String username = name;
			String password = RandomStringUtils.randomAlphanumeric(10);

			_log.info("create user " + username + " for database " + name);

			_client.createUser(name, username, password);

			// generate uri
			String endpoint = host + ":" + String.valueOf(port);
			String uri = SqlUtil.encodeURI("postgressql", endpoint, name, username, password);
			res.setUri(uri);

			return res;
		} catch (SQLException sex) {
			_log.error(sex.getMessage());
			throw new ResourceProviderException("sql error");
		}
	}

	@Override
	public void updateResource(Resource resource) throws ResourceProviderException {
		// TODO

	}

	@Override
	public void deleteResource(Resource resource) throws ResourceProviderException {

		_log.info("delete resource " + String.valueOf(resource.getId())
				+ " with scope " + resource.getScopeId()
				+ " for user " + resource.getUserId());

		// extract info from resource
		String database = SqlUtil.getDatabase(resource.getUri());
		String username = SqlUtil.getUsername(resource.getUri());

		try {
			// delete user first
			_log.info("drop user " + username + " for database " + database);
			_client.deleteUser(database, username);

			// delete database
			_log.info("drop database " + database);
			_client.deleteDatabase(database);
		} catch (SQLException sex) {
			_log.error(sex.getMessage());
			throw new ResourceProviderException("sql error");
		}
	}

	@Override
	public void checkResource(Resource resource) throws ResourceProviderException {
		// TODO
	}

	/*
	 * Helpers
	 */
	private String generateId(String scopeId, String userId) {
		// build id from context plus random string
		StringBuilder sb = new StringBuilder();
		// cleanup scope and userId to alphanum - will strip non ascii
		// use only _ as separator otherwise postgres will complain
		sb.append(scopeId.replaceAll("[^A-Za-z0-9]", "")).append("_");
		sb.append(userId.replaceAll("[^A-Za-z0-9]", "")).append("_");

		// random suffix length 5
		sb.append(RandomStringUtils.randomAlphanumeric(5));

		// ensure lowercase
		return sb.toString().toLowerCase();
	}
}
