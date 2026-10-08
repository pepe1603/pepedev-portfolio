<script setup lang="ts">
// ======================================================================
// LANDING (raíz)
//
// Concentra la presentación del portfolio: hero, perfil, skills,
// experiencia y contacto. A propósito no hay una ruta por sección: el
// portfolio público son cuatro rutas (/, /proyectos, /proyectos/[slug],
// /certificados) y lo demás vive aquí dentro.
//
// Los datos salen de GET /public/profile con el idioma activo. La key de
// useAsyncData no cambia al cambiar de idioma, así que lang va en `watch`:
// sin eso, pulsar EN en la cabecera no re-lanzaría la petición y el
// contenido seguiría en el idioma anterior.
//
// Si la API no responde no se monta NINGUNA sección: una landing con el
// nombre vacío y huecos por todos lados es peor que un aviso claro.
// ======================================================================
const api = useApi()
const lang = useLang()

const { data: profile, error } = await useAsyncData(
  'landing-profile',
  () => api.getPublic<ProfilePublicDTO>('/public/profile'),
  { watch: [lang] }
)

useSeoMeta({
  title: () => profile.value
    ? `${profile.value.fullName} — ${profile.value.headline}`
    : 'pepedev — Portfolio',
  description: () => profile.value?.headline ?? 'Proyectos, certificados y contacto.'
})
</script>

<template>
  <UPage>
    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      icon="i-lucide-cloud-off"
      title="No se pudo cargar el perfil"
      description="La API no está respondiendo. Inténtalo de nuevo en unos segundos."
      class="my-8"
    />

    <template v-else-if="profile">
      <RevealOnScroll animation="fade-up">
        <HeroSection :profile="profile" />
      </RevealOnScroll>

      <RevealOnScroll animation="fade-up">
        <ProfileSection :profile="profile" />
      </RevealOnScroll>

      <RevealOnScroll animation="fade-up">
        <SkillsSection :skills="profile.skills" />
      </RevealOnScroll>

      <RevealOnScroll animation="fade-up">
        <ExperienceSection :experiences="profile.experiences" />
      </RevealOnScroll>

      <RevealOnScroll animation="fade-up">
        <ContactSection />
      </RevealOnScroll>
    </template>
  </UPage>
</template>
