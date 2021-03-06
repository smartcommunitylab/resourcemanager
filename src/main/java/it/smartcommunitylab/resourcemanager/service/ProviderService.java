package it.smartcommunitylab.resourcemanager.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.smartcommunitylab.resourcemanager.common.NoSuchProviderException;
import it.smartcommunitylab.resourcemanager.model.ResourceProvider;

@Component
public class ProviderService {
    private final static Logger _log = LoggerFactory.getLogger(ProviderService.class);

    @Autowired
    private ProviderLocalService providerService;

    public Map<String, List<ResourceProvider>> list(String spaceId, String userId) {
        // TODO check auth
        //
        // call local service
        return providerService.listProviders();
    }

    public List<ResourceProvider> list(String spaceId, String userId, String type) {
        // TODO check auth
        //
        // call local service
        return providerService.listProviders(type);
    }

    public List<String> listTypes(String spaceId, String userId) {
        // TODO check auth
        //
        // call local service
        return providerService.listTypes();
    }

    public ResourceProvider get(String spaceId, String userId, String id) throws NoSuchProviderException {
        // TODO check auth
        //
        // call local service
        return providerService.getProvider(id);
    }
}
