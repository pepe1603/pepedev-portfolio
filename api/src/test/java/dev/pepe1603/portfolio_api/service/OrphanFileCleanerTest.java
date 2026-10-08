package dev.pepe1603.portfolio_api.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class OrphanFileCleanerTest {

    private static final String URL = "http://localhost:8080/files/123e4567-e89b-12d3-a456-426614174000.png";
    private static final String NAME = "123e4567-e89b-12d3-a456-426614174000.png";

    private final StorageService storageService = org.mockito.Mockito.mock(StorageService.class);
    private final StorageReferenceChecker referenceChecker = org.mockito.Mockito.mock(StorageReferenceChecker.class);
    private final OrphanFileCleaner cleaner = new OrphanFileCleaner(storageService, referenceChecker);

    @Test
    void borraCuandoElFicheroNoEstaReferenciadoDelTodo() {
        given(referenceChecker.isReferenced(NAME)).willReturn(false);

        cleaner.cleanupIfUnreferenced(URL);

        verify(storageService).deleteByUrl(URL);
    }

    @Test
    void noBorraCuandoSigueReferenciado() {
        given(referenceChecker.isReferenced(NAME)).willReturn(true);

        cleaner.cleanupIfUnreferenced(URL);

        verify(storageService, never()).deleteByUrl(anyString());
    }

    @Test
    void ignoraUrlsQueNoPertenecenAlStorage() {
        cleaner.cleanupIfUnreferenced("https://cdn.example.com/logo.svg");

        verify(storageService, never()).deleteByUrl(anyString());
    }

    @Test
    void ignoraNull() {
        cleaner.cleanupIfUnreferenced(null);

        verify(storageService, never()).deleteByUrl(isNull());
    }
}