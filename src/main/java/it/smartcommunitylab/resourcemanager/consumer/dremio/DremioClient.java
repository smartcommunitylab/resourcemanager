package it.smartcommunitylab.resourcemanager.consumer.dremio;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

public class DremioClient {
    private String ENDPOINT;
    private String USERNAME;
    private String PASSWORD;

    private final static String API = "/apiv2/";

    public DremioClient(String endpoint, String username, String password) {
        super();
        ENDPOINT = endpoint;
        USERNAME = username;
        PASSWORD = password;
    }

    public boolean ping() throws DremioException {
        // use listSources as test - will validate login
        try {
            JSONArray sources = listSources("");
            return true;
        } catch (DremioException e) {
            throw e;
        }

    }

    private JSONObject getSource(String name, String token) throws DremioException {
        try {
            RestTemplate template = template();
            HttpHeaders headers = connect(token);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // fetch response as String because it may not match the json schema
            ResponseEntity<String> response = template.exchange(ENDPOINT + API + "source/" + name, HttpMethod.GET,
                    entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                // fetch JSON array inside object
                return new JSONObject(response.getBody());
            } else {
                throw new DremioException("response error code " + response.getStatusCode());
            }

        } catch (RestClientException rex) {
            throw new DremioException("rest error " + rex.getMessage());
        }
    }

    private JSONArray listSources(String token) throws DremioException {
        try {
            RestTemplate template = template();
            HttpHeaders headers = connect(token);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // fetch response as String because it may not match the json schema
            ResponseEntity<String> response = template.exchange(ENDPOINT + API + "sources", HttpMethod.GET,
                    entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                // fetch JSON array inside object
                JSONObject res = new JSONObject(response.getBody());
                return res.getJSONArray("sources");
            } else {
                throw new DremioException("response error code " + response.getStatusCode());
            }

        } catch (RestClientException rex) {
            throw new DremioException("rest error " + rex.getMessage());
        }
    }

    public boolean hasSource(String name) throws DremioException {
        boolean exists = false;

        JSONArray sources = listSources("");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if (name.equals(source.optString("name", ""))) {
                exists = true;
                break;
            }
        }

        return exists;
    }

    public String addSource(
            String type,
            String name,
            String host, int port,
            String database,
            String username, String password)
            throws DremioException {
        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("type", type);

            JSONObject config = getClientConfiguration(type, host, port, database, username, password);
            if (config == null) {
                throw new DremioException("error generating client configuration");
            }

            json.put("config", config);

            RestTemplate template = template();
            HttpHeaders headers = connect("");

            HttpEntity<String> entity = new HttpEntity<>(json.toString(), headers);

            // fetch response as String because it may not match the json schema
            ResponseEntity<String> response = template.exchange(ENDPOINT + API + "source/" + name, HttpMethod.PUT,
                    entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return name;
            } else {
                throw new DremioException("response error code " + response.getStatusCode());
            }
        } catch (JSONException jex) {
            throw new DremioException("json error " + jex.getMessage());
        } catch (RestClientException rex) {
            throw new DremioException("rest error " + rex.getMessage());
        }

    }

    public String updateSource(
            String type,
            String name,
            String host, int port,
            String database,
            String username, String password)
            throws DremioException {
        // NOT working, returns 409 Conflict
        // DISABLED: not really useful since connection details won't ever change
        // in dremio apiv2 same as add
//        return addSource(type, name, host, port, database, username, password);
        return name;

    }

    public void deleteSource(String name) throws DremioException {
        try {
            RestTemplate template = template();
            HttpHeaders headers = connect("");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // fetch response as String because it should be empty
            ResponseEntity<String> response = template.exchange(
                    ENDPOINT + API + "source/" + name, HttpMethod.DELETE, entity,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                // success
            } else {
                throw new DremioException("response error code " + response.getStatusCode());
            }
        } catch (RestClientException rex) {
            throw new DremioException("rest error " + rex.getMessage());
        }
    }

    /*
     * Helpers
     */

    private JSONObject getClientConfiguration(String type,
            String host, int port,
            String database,
            String username, String password) {

        if (type.equals("POSTGRES")) {
            return getPostgresConfiguration(host, port, database, username, password);
        }

        if (type.equals("MYSQL")) {
            // does NOT work in dremio - missing dbName
//            return getMySqlConfiguration(host, port, database, username, password);
        }

        return null;

    }

    private JSONObject getPostgresConfiguration(
            String host, int port,
            String database,
            String username, String password) {
        JSONObject json = new JSONObject();

        json.put("username", username);
        json.put("password", password);
        json.put("hostname", host);
        json.put("port", Integer.toString(port));
        json.put("databaseName", database);

        return json;

    }

    private JSONObject getMySqlConfiguration(
            String host, int port,
            String database,
            String username, String password) {
        JSONObject json = new JSONObject();

        json.put("username", username);
        json.put("password", password);
        json.put("hostname", host);
        json.put("port", Integer.toString(port));
        json.put("databaseName", database);

        return json;

    }

    private RestTemplate template() {
        return new RestTemplate();
    }

    private HttpHeaders connect(String token) throws DremioException, RestClientException {

        if (token.isEmpty()) {
            token = login();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "_dremio" + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        return headers;
    }

    private String login() throws DremioException, RestClientException {
        RestTemplate template = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        JSONObject json = new JSONObject();
        json.put("userName", USERNAME);
        json.put("password", PASSWORD);

        HttpEntity<String> entity = new HttpEntity<>(json.toString(), headers);

        // fetch response as String because it may not match the json schema
        ResponseEntity<String> response = template.exchange(
                ENDPOINT + API + "/login", HttpMethod.POST, entity,
                String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            // fetch JSON and return token if present
            JSONObject user = new JSONObject(response.getBody());
            return user.optString("token", "");
        } else {
            throw new DremioException("login error code " + response.getStatusCode());
        }
    }

}
