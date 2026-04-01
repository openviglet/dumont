package com.viglet.dumont.connector.plugin.assets;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.viglet.dumont.connector.commons.plugin.DumConnectorPlugin;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

@Primary
@Component("assets")
public class DumAssetsPlugin implements DumConnectorPlugin {
    private final DumAssetsSourceRepository repository;
    private final DumAssetsPluginProcess process;

    public DumAssetsPlugin(DumAssetsSourceRepository repository, DumAssetsPluginProcess process) {
        this.repository = repository;
        this.process = process;
    }

    @Override
    public void crawl() { repository.findAll().forEach(process::start); }

    @Override
    public String getProviderName() { return "ASSETS"; }

    @Override
    public void indexAll(String source) {
        repository.findByName(source).ifPresent(process::start);
    }

    @Override
    public void indexById(String source, List<String> contentId) {
        throw new UnsupportedOperationException("Assets plugin does not support indexing by ID");
    }

    @Override
    public List<String> discoverContentIds(String source) {
        throw new UnsupportedOperationException("Assets plugin does not support content ID discovery");
    }
}
