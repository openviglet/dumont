package com.viglet.dumont.connector.plugin.assets.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.dumont.connector.plugin.assets.DumAssetsPluginProcess;
import com.viglet.dumont.connector.plugin.assets.export.DumAssetsExchangeProcess;
import com.viglet.dumont.connector.plugin.assets.persistence.model.DumAssetsSource;
import com.viglet.dumont.connector.plugin.assets.persistence.repository.DumAssetsSourceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@CrossOrigin(origins = "*") @RestController
@RequestMapping("/api/v2/assets/source")
@Tag(name = "Assets Source", description = "Assets Source")
public class DumAssetsSourceApi {
    private final DumAssetsSourceRepository repository;
    private final DumAssetsPluginProcess pluginProcess;
    private final DumAssetsExchangeProcess exchangeProcess;

    public DumAssetsSourceApi(DumAssetsSourceRepository repository,
            DumAssetsPluginProcess pluginProcess, DumAssetsExchangeProcess exchangeProcess) {
        this.repository = repository;
        this.pluginProcess = pluginProcess;
        this.exchangeProcess = exchangeProcess;
    }

    @GetMapping @Operation(summary = "List all Assets Sources")
    public List<DumAssetsSource> list() { return repository.findAll(); }

    @GetMapping("/structure") @Operation(summary = "Assets Source structure")
    public DumAssetsSource structure() {
        DumAssetsSource s = new DumAssetsSource();
        s.setLocale("en_US"); s.setContentType("Static File"); s.setChunk(100);
        s.setFileSizeField("fileSize"); s.setFileExtensionField("fileExtension"); s.setEncoding("UTF-8");
        return s;
    }

    @GetMapping("/{id}") @Operation(summary = "Show an Assets Source")
    public DumAssetsSource get(@PathVariable String id) {
        return repository.findById(id).orElse(new DumAssetsSource());
    }

    @Transactional @PutMapping("/{id}") @Operation(summary = "Update an Assets Source")
    public ResponseEntity<DumAssetsSource> update(@PathVariable String id, @RequestBody DumAssetsSource source) {
        if (source.getId() != null && !id.equals(source.getId())) return ResponseEntity.badRequest().build();
        return repository.findById(id).map(e -> {
            e.setName(source.getName()); e.setDescription(source.getDescription());
            e.setSourceDir(source.getSourceDir()); e.setPrefixFromReplace(source.getPrefixFromReplace());
            e.setPrefixToReplace(source.getPrefixToReplace()); e.setSite(source.getSite());
            e.setLocale(source.getLocale()); e.setContentType(source.getContentType());
            e.setChunk(source.getChunk()); e.setTypeInId(source.isTypeInId());
            e.setFileSizeField(source.getFileSizeField()); e.setFileExtensionField(source.getFileExtensionField());
            e.setEncoding(source.getEncoding()); e.setShowOutput(source.isShowOutput());
            e.getTurSNSites().clear();
            if (source.getTurSNSites() != null) e.getTurSNSites().addAll(source.getTurSNSites());
            return ResponseEntity.ok(repository.save(e));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Transactional @DeleteMapping("/{id}") @Operation(summary = "Delete an Assets Source")
    public boolean delete(@PathVariable String id) {
        return repository.findById(id).map(s -> { repository.delete(s); return true; }).orElse(false);
    }

    @PostMapping @Operation(summary = "Create an Assets Source")
    public DumAssetsSource create(@RequestBody DumAssetsSource source) { return repository.save(source); }

    @GetMapping("/{id}/indexAll") @Operation(summary = "Index all files from an Assets Source")
    public ResponseEntity<Object> indexAll(@PathVariable String id) {
        pluginProcess.indexAllByIdAsync(id); return ResponseEntity.ok().build();
    }

    @GetMapping("/export") @Operation(summary = "Export all Assets Sources")
    public StreamingResponseBody export(HttpServletResponse response) { return exchangeProcess.exportObject(response); }

    @PostMapping("/import") @Operation(summary = "Import Assets Sources from ZIP")
    public ResponseEntity<Object> importSources(@RequestParam("file") MultipartFile file) {
        exchangeProcess.importFromMultipartFile(file); return ResponseEntity.ok().build();
    }
}
