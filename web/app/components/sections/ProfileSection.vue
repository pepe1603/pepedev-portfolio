<script setup lang="ts">
// ======================================================================
// PERFIL: avatar, bio, enlaces sociales y descarga del CV
//
// Sección nueva (la plantilla no tenía equivalente); sigue el patrón de
// las sections/ de la plantilla: recibe datos por props y no decide de
// dónde salen. Quien decide eso es pages/index.vue.
//
// --------------------------------------------------------------------------
// POR QUE LAS URLs SALEN DEL DTO Y NO SE MONTAN A MANO
//
// avatarUrl, cvUrlEs y cvUrlEn ya son URLs absolutas que la API construye
// sobre APP_STORAGE_PUBLIC_URL (su propio host, con /files). Montar
// `${apiBase}/files/${nombre}` aqui duplicaría una regla que vive en el
// backend y saltaría el día que el storage cambie de host.
//
// Los campos pueden venir null (perfil sin avatar, CV sin subir): sin
// este check la UI enseñaría un boton roto.
// ======================================================================
import type { ProfilePublicDTO } from '~/utils/types'

const props = defineProps<{
  profile: ProfilePublicDTO
}>()

const lang = useLang()

// El CV sigue el idioma activo de la cabecera. No se llama a
// GET /public/cv/{lang} porque la respuesta es un 302 a esta misma URL:
// el boton ya apunta al destino y el browser sigue el redirect solo.
const cvUrl = computed(() => (lang.value === 'en' ? props.profile.cvUrlEn : props.profile.cvUrlEs))

interface SocialLink {
  label: string
  icon: string
  to: string
  external: boolean
}

// Solo aparecen los enlaces que el profile trae rellenos: un perfil sin
// LinkedIn no enseña un boton de LinkedIn.
const socialLinks = computed<SocialLink[]>(() => {
  const links: SocialLink[] = []

  if (props.profile.githubUrl) {
    links.push({ label: 'GitHub', icon: 'i-simple-icons-github', to: props.profile.githubUrl, external: true })
  }

  if (props.profile.linkedinUrl) {
    links.push({ label: 'LinkedIn', icon: 'i-simple-icons-linkedin', to: props.profile.linkedinUrl, external: true })
  }

  if (props.profile.websiteUrl) {
    links.push({ label: 'Web', icon: 'i-lucide-globe', to: props.profile.websiteUrl, external: true })
  }

  if (props.profile.emailPublic) {
    links.push({ label: 'Email', icon: 'i-lucide-mail', to: `mailto:${props.profile.emailPublic}`, external: false })
  }

  return links
})
</script>

<template>
  <UPageSection title="Sobre mí">
    <div class="flex flex-col items-start gap-8 md:flex-row">
      <!-- NuxtImg pasa por ipx (el host ya esta en image.domains), asi que
           el avatar sale recortado y en avif/webp sin tocar el backend. -->
      <NuxtImg
        v-if="profile.avatarUrl"
        :src="profile.avatarUrl"
        :alt="profile.fullName"
        width="128"
        height="128"
        class="size-32 shrink-0 rounded-full object-cover ring ring-default"
      />

      <UIcon
        v-else
        name="i-lucide-user"
        class="size-32 shrink-0 text-dimmed"
      />

      <div class="flex flex-1 flex-col gap-4">
        <p class="text-muted text-lg">
          {{ profile.bio }}
        </p>

        <div class="flex flex-wrap gap-2">
          <UButton
            v-for="link in socialLinks"
            :key="link.label"
            :label="link.label"
            :icon="link.icon"
            :to="link.to"
            :target="link.external ? '_blank' : undefined"
            color="neutral"
            variant="soft"
            size="sm"
          />
        </div>

        <div>
          <UButton
            v-if="cvUrl"
            :href="cvUrl"
            target="_blank"
            label="Descargar CV"
            icon="i-lucide-file-down"
            size="sm"
          />
        </div>
      </div>
    </div>
  </UPageSection>
</template>
