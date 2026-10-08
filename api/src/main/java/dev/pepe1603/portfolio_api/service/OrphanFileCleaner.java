package dev.pepe1603.portfolio_api.service;

import org.springframework.stereotype.Component;

@Component
public class OrphanFileCleaner {

    private final StorageService storageService;
    private final StorageReferenceChecker referenceChecker;

    public OrphanFileCleaner(StorageService storageService, StorageReferenceChecker referenceChecker) {
        this.storageService = storageService;
        this.referenceChecker = referenceChecker;
    }

    public void cleanupIfUnreferenced(String url) {
        String fileName = StorageService.baseName(url);
        if (fileName != null && !referenceChecker.isReferenced(fileName)) {
            storageService.deleteByUrl(url);
        }
    }
}