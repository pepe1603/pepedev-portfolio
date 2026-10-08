package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.pepe1603.portfolio_api.entity.Certificate;
import dev.pepe1603.portfolio_api.entity.GalleryImage;
import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.entity.Project;
import dev.pepe1603.portfolio_api.repository.CertificateRepository;
import dev.pepe1603.portfolio_api.repository.ProfileRepository;
import dev.pepe1603.portfolio_api.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageReferenceCheckerTest {

    private static final String NAME = "123e4567-e89b-12d3-a456-426614174000.png";

    private final ProfileRepository profileRepository = org.mockito.Mockito.mock(ProfileRepository.class);
    private final ProjectRepository projectRepository = org.mockito.Mockito.mock(ProjectRepository.class);
    private final CertificateRepository certificateRepository = org.mockito.Mockito.mock(CertificateRepository.class);

    private final StorageReferenceChecker checker =
            new StorageReferenceChecker(profileRepository, projectRepository, certificateRepository);

    private void noReferencesAtAll() {
        given(profileRepository.findById((short) 1)).willReturn(Optional.empty());
        given(projectRepository.findAll()).willReturn(List.of());
        given(certificateRepository.findAll()).willReturn(List.of());
    }

    @Test
    void avatarDelPerfilCuentaComoReferencia() {
        Profile profile = new Profile();
        profile.setAvatarUrl("http://localhost:8080/files/" + NAME);
        given(profileRepository.findById((short) 1)).willReturn(Optional.of(profile));
        given(projectRepository.findAll()).willReturn(List.of());
        given(certificateRepository.findAll()).willReturn(List.of());

        assertThat(checker.isReferenced(NAME)).isTrue();
    }

    @Test
    void cvDelPerfilCuentaComoReferencia() {
        Profile profile = new Profile();
        profile.setCvUrlEn("http://localhost:8080/files/" + NAME);
        given(profileRepository.findById((short) 1)).willReturn(Optional.of(profile));
        given(projectRepository.findAll()).willReturn(List.of());
        given(certificateRepository.findAll()).willReturn(List.of());

        assertThat(checker.isReferenced(NAME)).isTrue();
    }

    @Test
    void imagenDeCertificadoCuentaComoReferencia() {
        Certificate certificate = new Certificate();
        certificate.setImageUrl("http://localhost:8080/files/" + NAME);
        noReferencesAtAll();
        given(certificateRepository.findAll()).willReturn(List.of(certificate));

        assertThat(checker.isReferenced(NAME)).isTrue();
    }

    @Test
    void miniaturaDeProyectoCuentaComoReferencia() {
        Project project = new Project();
        project.setThumbnailUrl("http://localhost:8080/files/" + NAME);
        noReferencesAtAll();
        given(projectRepository.findAll()).willReturn(List.of(project));

        assertThat(checker.isReferenced(NAME)).isTrue();
    }

    @Test
    void imagenDeGaleriaCuentaComoReferencia() {
        Project project = new Project();
        project.setGallery(List.of(new GalleryImage("http://localhost:8080/files/" + NAME, "alt", null)));
        noReferencesAtAll();
        given(projectRepository.findAll()).willReturn(List.of(project));

        assertThat(checker.isReferenced(NAME)).isTrue();
    }

    @Test
    void ficheroSinReferenciasNoEsReferenciado() {
        noReferencesAtAll();

        assertThat(checker.isReferenced(NAME)).isFalse();
    }

    @Test
    void nullNoSeConsideraReferenciado() {
        noReferencesAtAll();

        assertThat(checker.isReferenced(null)).isFalse();
    }
}