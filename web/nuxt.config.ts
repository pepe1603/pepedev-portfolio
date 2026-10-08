// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  modules: [
    '@nuxt/eslint',
    '@nuxt/ui',
    '@nuxt/fonts',
    '@nuxt/image',
    '@vueuse/nuxt',
    '@vueuse/motion/nuxt',
    'vue3-carousel-nuxt'
  ],

  // Sin pathPrefix, una seccion en components/sections/HeroSection.vue se
  // importa como <HeroSection /> y no como <SectionsHeroSection />.
  components: [
    { path: '~/components', pathPrefix: false }
  ],

  devtools: {
    enabled: false
  },

  css: ['~/assets/css/main.css'],

  // El cambio de tema se anima con View Transitions (ver ColorModeToggle.vue),
  // asi que las transiciones CSS de Nuxt UI tienen que estar apagadas durante
  // el cambio. En true, @nuxtjs/color-mode inyecta
  // `* { transition: none !important }` al cambiar de tema y lo retira al
  // siguiente frame.
  //
  // En false, que es el default, el fondo sigue su transicion de color
  // mientras la API captura el estado NUEVO: la instantanea sale a medio
  // camino entre los dos temas, y el reveal circular muestra el tema viejo
  // encima del viejo. El tema no cambia hasta que termina la transicion, y
  // de golpe. Por eso va en true, y no por estetica.
  colorMode: {
    disableTransition: true
  },

  ui: {
    experimental: {
      // componentDetection activa el recorte de temas: Nuxt UI escanea que
      // componentes se usan de verdad y solo genera el CSS de esos. Con
      // `true` no haria falta la lista; el array la completa con los que
      // se usan de forma dinamica y el escaner no puede ver.
      //
      // 'Modal' y 'Drawer' van aqui porque AppModal.vue y AppDrawer.vue los
      // envuelven, pero envuelto no cuenta como dinamico: es una referencia
      // estatica y el escaner la ve. Se dejan de forma explicita para que sus
      // temas no dependan de que las secciones que los usan sigan en la pagina.
      componentDetection: ['Modal', 'Drawer']
    }
  },

  // El host de la API nunca se escribe a mano en un componente: sale de aqui
  // y se lee con useRuntimeConfig().public.apiBase (ver composables/useApi.ts).
  // El nombre de la variable que lo sobreescribe es NUXT_PUBLIC_API_BASE: nuxt
  // convierte la ruta del config a SCREAMING_SNAKE_CASE (public.apiBase →
  // PUBLIC_API_BASE) y le pone el prefijo NUXT_.
  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:8080'
    }
  },

  // A proposito no hay routeRules con prerender: prerenderear '/' hace que
  // nitro emita solo output estatico, y en estatico no hay runtime que sirva
  // las variantes de ipx, asi que /_ipx daba 404. Con SSR el handler de ipx
  // viaja al build y las imagenes se optimizan tambien en produccion.

  compatibilityDate: '2026-06-30',

  eslint: {
    config: {
      stylistic: {
        commaDangle: 'never',
        braceStyle: '1tbs'
      }
    }
  },

  fonts: {
    families: [
      { name: 'Inter', weights: [400, 600, 700, 900] }
    ]
  },

  // ipx es el proveedor por defecto y optimiza en local, sin servicios
  // externos: recorta, convierte y sirve la imagen ya ajustada al srcset.
  image: {
    quality: 80,
    format: ['avif', 'webp'],
    // Sin esta lista, una URL absoluta se devuelve tal cual sin pasar por ipx:
    // la imagen se ve igual, pero sin srcset, sin avif y sin placeholder, y
    // ningun aviso en consola. El host tiene que ir aqui, no la URL entera.
    // picsum.photos responde 302 a fastly.picsum.photos e ipx valida el host
    // despues del redirect, asi que van los dos.
    // localhost:8080 va porque las URLs que la API devuelve (avatar, miniatura,
    // galeria) apuntan a su propio host via APP_STORAGE_PUBLIC_URL.
    domains: ['4kwallpapers.com', 'picsum.photos', 'fastly.picsum.photos', 'localhost:8080']
  }
})
