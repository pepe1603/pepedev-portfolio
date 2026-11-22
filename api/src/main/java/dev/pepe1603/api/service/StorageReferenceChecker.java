package dev.pepe1603.api.service;

import dev.pepe1603.api.entity.Certificate;
import dev.pepe1603.api.entity.Profile;
import dev.pepe1603.api.entity.Project;
import dev.pepe1603.api.enums.StorageUse;
import dev.pepe1603.api.repository.CertificateRepository;
import dev.pepe1603.api.repository.ProfileRepository;
import dev.pepe1603.api.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Dice si un fichero del bucket lo usa alguien, y dónde.
 *
 * <p>{@link #isReferenced(String)} es la pregunta de sí/no que usa el borrado de huérfanos.
 * {@link #usages(String)} es la misma comprobación pero devolviendo el detalle, que es lo que
 * necesita el borrado manual del panel para explicar por qué no deja borrar un fichero. Las
 * dos comparten implementación a propósito: si algún día se añade un sitio nuevo que
 * referencia ficheros, se toca un solo método y no hay forma de que se desincronicen.
 */
@Component
public class StorageReferenceChecker {

    private final ProfileRepository profileRepository;
    private final ProjectRepository projectRepository;
    private final CertificateRepository certificateRepository;

    public StorageReferenceChecker(ProfileRepository profileRepository, ProjectRepository projectRepository,
                                   CertificateRepository certificateRepository) {
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.certificateRepository = certificateRepository;
    }

    /** ¿Lo usa alguien? Atajo de {@code !usages(fileName).isEmpty()}. */
    public boolean isReferenced(String fileName) {
        return !usages(fileName).isEmpty();
    }

    /**
     * Infiere el uso de un fichero a partir de quién lo referencia. Sirve para
     * los ficheros sin fila en {@code storage_files} (anteriores a la tabla, o
     * subidos por otra vía) y para que el panel los etiquete igual. Si lo usa más
     * de un sitio se devuelve el primero en este orden: avatar, CV, miniatura,
     * galería, imagen de certificado; no hay caso hoy en que un mismo fichero se
     * use en dos sitios distintos. {@code null} si no lo referencia nadie.
     */
    public StorageUse inferUse(String fileName) {
        if (fileName == null) {
            return null;
        }
        StorageUse profileUse = inferProfileUse(fileName);
        if (profileUse != null) {
            return profileUse;
        }
        return inferProjectOrCertificateUse(fileName);
    }

    private StorageUse inferProfileUse(String fileName) {
        return profileRepository.findById((short) 1)
                .map(profile -> {
                    if (matches(profile.getAvatarUrl(), fileName)) {
                        return StorageUse.AVATAR;
                    }
                    if (matches(profile.getCvUrlEs(), fileName) || matches(profile.getCvUrlEn(), fileName)) {
                        return StorageUse.CV;
                    }
                    return null;
                })
                .orElse(null);
    }

    private StorageUse inferProjectOrCertificateUse(String fileName) {
        for (Project project : projectRepository.findAll()) {
            if (matches(project.getThumbnailUrl(), fileName)) {
                return StorageUse.THUMBNAIL;
            }
            if (project.getGallery().stream().anyMatch(image -> matches(image.url(), fileName))) {
                return StorageUse.GALLERY;
            }
        }
        for (Certificate certificate : certificateRepository.findAll()) {
            if (matches(certificate.getImageUrl(), fileName)) {
                return StorageUse.IMAGE;
            }
        }
        return null;
    }

    /**
     * Dónde se usa el fichero, en lenguaje de persona ("avatar del perfil", "miniatura del
     * proyecto vault-qr"). Vacío si nadie lo usa. Nunca null.
     */
    public List<String> usages(String fileName) {
        if (fileName == null) {
            return List.of();
        }
        List<String> usages = new ArrayList<>();
        collectProfileUsages(fileName, usages);
        collectProjectUsages(fileName, usages);
        collectCertificateUsages(fileName, usages);
        return usages;
    }

    private void collectProfileUsages(String fileName, List<String> usages) {
        profileRepository.findById((short) 1).ifPresent(profile -> {
            addIfMatches(profile.getAvatarUrl(), fileName, "avatar del perfil", usages);
            addIfMatches(profile.getCvUrlEs(), fileName, "CV en español del perfil", usages);
            addIfMatches(profile.getCvUrlEn(), fileName, "CV en inglés del perfil", usages);
        });
    }

    private void collectProjectUsages(String fileName, List<String> usages) {
        for (Project project : projectRepository.findAll()) {
            String name = label(project.getTitle());
            if (matches(project.getThumbnailUrl(), fileName)) {
                usages.add("miniatura del proyecto " + name);
            }
            if (project.getGallery().stream().anyMatch(image -> matches(image.url(), fileName))) {
                usages.add("galería del proyecto " + name);
            }
        }
    }

    private void collectCertificateUsages(String fileName, List<String> usages) {
        for (Certificate certificate : certificateRepository.findAll()) {
            if (matches(certificate.getImageUrl(), fileName)) {
                usages.add("imagen del certificado " + label(certificate.getTitle()));
            }
        }
    }

    private static void addIfMatches(String url, String fileName, String description, List<String> usages) {
        if (matches(url, fileName)) {
            usages.add(description);
        }
    }

    private static boolean matches(String url, String fileName) {
        return fileName.equals(StorageService.baseName(url));
    }

    /**
     * Nombre legible de un recurso bilingüe: el título en español, si no en inglés, y si no un
     * hueco con el identificador... que aquí no tenemos. Como el título es obligatorio en la
     * entidad, la última rama es defensiva.
     */
    private static String label(Map<String, String> title) {
        if (title == null || title.isEmpty()) {
            return "(sin título)";
        }
        String es = title.get("es");
        if (es != null && !es.isBlank()) {
            return es;
        }
        String en = title.get("en");
        if (en != null && !en.isBlank()) {
            return en;
        }
        return "(sin título)";
    }
}