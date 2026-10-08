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
const config = useRuntimeConfig()

const { data: profile, error } = await useAsyncData(
  'landing-profile',
  () => api.getPublic<ProfilePublicDTO>('/public/profile'),
  { watch: [lang] }
)

useSeoMeta({
  title: () => profile.value
    ? `${profile.value.fullName} — ${profile.value.headline}`
    : 'pepedev — Portfolio',
  description: () => profile.value?.headline ?? 'Proyectos, certificados y contacto.',
  // El avatar real del perfil como imagen social; si no hay, la página
  // queda con el og:url global sin imagen, que es preferible a una
  // imagen inventada.
  ogImage: () => profile.value?.avatarUrl ?? undefined
})

// ======================================================================
// JSON-LD Person
//
// schema.org/Person con los datos del perfil para que los buscadores
// asocien el sitio a una persona. Dos detalles:
//
//   1. `</` en cualquier posición cierra un <script> desde dentro; el
//      reemplazo por `<\/` es el truco estándar para que un bio con
//      esos caracteres no rompa el bloque.
//   2. Los campos opcionales van como undefined y JSON.stringify los
//      omite: un sameAs vacío o un image null es peor que no existir.
// ======================================================================
const personJsonLd = computed(() => {
  const p = profile.value
  if (!p) return ''

  const sameAs = [p.githubUrl, p.linkedinUrl, p.websiteUrl]
    .filter((url): url is string => Boolean(url))

  return JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Person',
    'name': p.fullName,
    'jobTitle': p.headline,
    'description': p.bio,
    'url': config.public.siteUrl,
    'image': p.avatarUrl ?? undefined,
    'email': p.emailPublic ?? undefined,
    'sameAs': sameAs.length ? sameAs : undefined
  }).replaceAll('</', '<\\/')
})

useHead({
  script: [
    {
      type: 'application/ld+json',
      innerHTML: () => personJsonLd.value
    }
  ]
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
