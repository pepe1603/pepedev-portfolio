# Nuxt UI Template

Plantilla de Nuxt 4 con Nuxt UI, Tailwind CSS v4 y tema claro/oscuro basado en tokens semánticos.

## Quick Start

```bash
pnpm install
pnpm dev
```

| Comando | Descripción |
| --- | --- |
| `pnpm dev` | Servidor de desarrollo en `localhost:3000` |
| `pnpm build` | Build de producción |
| `pnpm preview` | Previsualiza el build |
| `pnpm lint` | ESLint |
| `pnpm typecheck` | vue-tsc |

## Cómo está construido

El proyecto usa **una sola capa semántica**: los tokens `--ui-*` de Nuxt UI. No hay una capa de colores propia en paralelo, y ningún componente contiene valores hex.

```
app/assets/css/main.css   →  define los valores de los tokens
app/app.config.ts         →  define el color de marca
app/components/           →  consume tokens, nunca colores literales
```

Por eso el tema claro/oscuro no necesita JavaScript ni lógica condicional: cambiar de tema solo cambia qué valores están activos en `:root` o en `.dark`.

## Cómo cambiar los colores

### 1. Color de marca

En `app/app.config.ts`:

```ts
colors: {
  primary: 'red', // ← cambia este valor
  secondary: 'zinc',
  neutral: 'zinc'
}
```

Acepta cualquier color de Tailwind: `red`, `blue`, `emerald`, `amber`, `violet`, `cyan`… El tema claro toma el shade 500 y el oscuro el 400 de forma automática.

Si necesitas un color fuera de la paleta de Tailwind, decláralo primero en `main.css`:

```css
@theme static {
  --color-marca-500: #8b5cf6;
  --color-marca-400: #a78bfa;
}
```

y luego usa `primary: 'marca'`.

### 2. Neutros, superficies y bordes

En `app/assets/css/main.css`, dentro de los bloques `:root` (claro) y `.dark` (oscuro):

```css
:root {
  --ui-bg: #ffffff;         /* fondo de la página */
  --ui-bg-muted: #fafafa;   /* secciones alternas */
  --ui-bg-elevated: #f4f4f5;/* tarjetas */
  --ui-bg-accented: #e4e4e7;/* hover, superficies presionadas */

  --ui-text: #3f3f46;             /* texto principal */
  --ui-text-toned: #52525b;       /* énfasis */
  --ui-text-muted: #71717a;       /* texto secundario */
  --ui-text-dimmed: #a1a1aa;      /* metadatos */

  --ui-border: #e4e4e7;
}
```

Al cambiar la paleta de neutros, cambia los valores en **ambos** bloques para conservar el contraste.

### 3. Tipografía

En `nuxt.config.ts` declara la familia, y actualiza la variable en `main.css`:

```ts
fonts: {
  families: [
    { name: 'Inter', weights: [400, 600, 700, 900] }
  ]
}
```

```css
@theme {
  --font-sans: 'Inter', sans-serif;
}
```

Ambas líneas son necesarias. Si falta la del CSS, `@nuxt/fonts` no detecta que la familia se usa y no la descarga.

## Clases de color disponibles

Definidas por Nuxt UI a partir de los tokens. No hace falta memorizar hex, estas clases ya se adaptan al tema:

| Clase | Uso |
| --- | --- |
| `bg-default` / `text-default` | Fondo y texto de la página |
| `bg-muted` / `text-muted` | Superficie y texto secundario |
| `bg-elevated` / `text-elevated` | Tarjetas y paneles |
| `bg-accented` / `text-accented` | Hover y superficies activas |
| `text-highlighted` | Texto de máxima jerarquía |
| `text-toned` | Énfasis dentro de una frase |
| `text-dimmed` | Metadatos, notas al pie |
| `text-primary` | Enlaces, CTAs, color de marca |
| `border-default` | Bordes estándar |
| `text-gradient` | Texto con gradiente de marca (utilidad propia) |

## Componentes propios

Tres componentes añadidos. Cualquier otro (`UCard`, `UButton`, `UBadge`,
`UModal`, `UTable`…) ya viene incluido en Nuxt UI y no necesita uno propio.

### Dos capas de animación

Hay dos formas de animar aquí, y conviene no mezclarlas:

| | `RevealOnScroll` | `MotionSection` |
| --- | --- | --- |
| Cómo | `transition` CSS | `@vueuse/motion` |
| Necesita | nada | el módulo `@vueuse/motion/nuxt` |
| Estado | dos clases de Tailwind | valores físicos con velocidad |
| Para qué | entrar y salir de pantalla | valores que siguen moviéndose |

El reveal de scroll podría hacerse con `@vueuse/motion`, y sería un error: para
una entrada de 700 ms no aporta nada y cuesta mantenerlo sincronizado con el
render del servidor. La librería entra solo donde el valor tiene que continuar
moviéndose después de cambiar de objetivo, que es el único caso en que una
transición CSS se queda corta.

La regla que separa las dos capas es la del enunciado de la sección, y es la
misma que se aplica a `motion-v`: un módulo de animación no se mete debajo de
`UCard`, `UButton` o cualquier otro componente de Nuxt UI. Va en un `<div>`
propio, porque los componentes de la librería llevan su propio estado interno y
su propia transición; pelearse con ellos desde fuera produce saltos.

### `GradientTitle`

Título con el degradado de marca. Existe porque Nuxt UI no cubre texto con
gradiente.

```vue
<GradientTitle as="h1" size="xl" align="center">
  Sistema de diseño
</GradientTitle>
```

| Prop | Valores | Default |
| --- | --- | --- |
| `as` | `h1`–`h6`, `span` | `h1` |
| `size` | `sm`, `md`, `lg`, `xl` | `lg` |
| `align` | `left`, `center` | `center` |

El degradado es `--ui-color-primary-500` → `--ui-color-primary-400`, los dos
shades que Nuxt UI resuelve como `--ui-primary` según el tema, así que sigue
al color de marca sin configuración. La utilidad `text-gradient` que lo aplica
está definida en `main.css`.

Dentro de un slot que ya trae su propio elemento —el `<h1>` de `UPageHero`, por
ejemplo— usa `as="span"`: un `<h1>` dentro de un `<h1>` es HTML inválido y
duplica el rol de encabezado.

### `RevealOnScroll`

Revela un bloque al entrar en pantalla. VueUse decide *cuándo* y Tailwind
decide *cómo*; ninguna de las dos capas conoce a la otra.

```vue
<RevealOnScroll animation="fade-up" :delay="100">
  <UPageSection title="Sección" />
</RevealOnScroll>
```

| Prop | Valores | Default |
| --- | --- | --- |
| `animation` | `fade`, `fade-up`, `fade-down`, `from-left`, `from-right`, `zoom-in`, `zoom-out`, `blur` | `fade-up` |
| `easing` | `out`, `in-out`, `soft`, `back` | `soft` |
| `sequence` | `together`, `lead`, `staged` | `staged` |
| `duration` | milisegundos | `700` |
| `delay` | milisegundos | `0` |
| `once` | booleano | `false` |

Con `once: false` el bloque se oculta al salir de la pantalla y vuelve a
animarse cada vez que regresa. `sequence` encadena las propiedades dentro de una
misma entrada —primero la opacidad, después el desplazamiento— y la salida se
resuelve más rápido y sin encadenar. La coreografía está en
`app/utils/reveal.ts`.

Respeta `prefers-reduced-motion`: cada animación declara su propio reset de
`motion-reduce`, sin tocar la lógica.

Un aviso que no sale de la API: `isVisible` empieza en `false`, así que el
servidor siempre escribe el bloque en su estado oculto, aunque esté por encima
del fold. Sin JavaScript ese contenido no aparece nunca. En el `HeroSection` se
compensa con `once` y una duración corta, y la sección *La primera pantalla*
del sitio explica el trade-off entero.

## Imágenes

`@nuxt/image` con **ipx**, el proveedor por defecto. El src es una URL absoluta:
las imágenes no se sirven desde este proyecto, ipx las descarga, las recorta y
sirve la variante que pide el navegador.

```vue
<script setup lang="ts">
const foto = {
  src: 'https://ejemplo.com/foto.jpg',
  alt: 'Lo que muestra'
}
</script>

<template>
  <NuxtImg
    :src="foto.src"
    :alt="foto.alt"
    sizes="100vw sm:50vw lg:33vw"
    loading="lazy"
  />
</template>
```

| Componente | Para qué |
| --- | --- |
| `NuxtImg` | Una sola fuente, con `srcset` responsive |
| `NuxtPicture` | Varios formatos (`avif`, `webp`) con fallback automático |
| `UAvatar` y el resto de Nuxt UI | Ya usan `NuxtImg` por debajo, basta con pasar `src` |

`sizes` describe el ancho que la imagen ocupa **en cada breakpoint**, no el de
la ventana. Es lo que permite a ipx generar candidatos que encajan con el
layout: `lg:33vw` genera el ancho de una de tres columnas, no un tercio de
pantalla completo.

### El host tiene que estar en `image.domains`

En `nuxt.config.ts`:

```ts
image: {
  domains: ['4kwallpapers.com', 'picsum.photos', 'fastly.picsum.photos']
}
```

Sin esa lista la imagen **se ve igual y no da ningún error**: @nuxt/image
comprueba el host, no lo encuentra, y devuelve la URL original sin pasar por
ipx. Te queda sin `srcset`, sin avif y sin placeholder, sin avisar.

Dos detalles que no son obvios:

- Va el **host**, no la URL: `4kwallpapers.com`, nunca `https://4kwallpapers.com/...`.
- Si el host redirige, declara también el destino. `picsum.photos` responde 302
  a `fastly.picsum.photos` e ipx valida el host **después** del redirect, así que
  con uno solo los avatares devuelven `IPX_FORBIDDEN_HOST`.

ipx descarga en el servidor, así que tu despliegue necesita salida a internet y
los hosts que bloquean por hotlink o por User-Agent no sirven.

### Dos cosas más que rompen sin aviso

**`placeholder` como string se lee como URL.** Hay que pasarle un número o un
array:

```vue
<NuxtImg :src="foto.src" :placeholder="[32, 32, 20]" />
```

Con `placeholder="32"` el atributo `src` acaba siendo literalmente `32`, que
devuelve un 404. Y el array se queda en `[width, height, quality]`: una cuarta
posición emite `b_<valor>`, que ipx lee como color de fondo y espera un color,
así que la imagen responde 400.

**Nada de `prerender`.** El handler que sirve `/_ipx` viaja en el server de
Nitro. Si prerenderizas las rutas, nitro emite output estático, no queda
runtime y todas las variantes devuelven 404. Para seguir desplegando estático
hay que cambiar a un proveedor cloud en `nuxt.config.ts`.

## Flujo de trabajo

`main` solo recibe releases, `develop` es el punto de integración y las ramas de
trabajo se quedan en local. Ver [CONTRIBUTING.md](./CONTRIBUTING.md).

## Personalización por proyecto

- `app/app.vue` — nombre del proyecto en el header y footer, enlaces sociales
- `app/app.config.ts` — color de marca
- `app/assets/css/main.css` — neutros, superficies, tipografía
- `app/utils/reveal.ts` — estados y coreografía de las animaciones por `transition`
- `nuxt.config.ts` — dominios remotos permitidos por `@nuxt/image`
- `app/pages/index.vue` — esta página es documentación del sistema; elimínala al iniciar un proyecto
- Imágenes sociales (ogImage): coloca la tuya en `public/` y descomenta la línea en `app/app.vue`

## Licencia

[MIT](./LICENSE) — usala libremente en proyectos personales y comerciales. Si
modificas la plantilla, no tenés que publicar los cambios.

## Prompt para agentes

Copia el bloque de abajo y pásaselo tal cual a Claude, Cursor, Copilot o
cualquier otro agente que vaya a trabajar en esta plantilla. Está escrito para
un agente con acceso al repo: describe el stack, las reglas que no se negocian y
las trampas que ya están pagadas.

````markdown
# Contexto

Plantilla base de Nuxt 4 + Nuxt UI v4. Tailwind v4 vía `@nuxt/ui`, tokens
semánticos en `app/assets/css/main.css`, y una página `/` que hace de
documentación viva del sistema de diseño.

## Stack

- **Nuxt UI v4** como librería de componentes. Antes de añadir un componente a
  mano, comprueba si Nuxt UI ya lo tiene.
- **`vue3-carousel`** para carruseles (`AppCarousel`, `GalleryCarousel`).
  Sustituyó a `UCarousel`; no lo vuelvas a usar.
- **`@vueuse/motion`** solo donde un valor tiene que seguir moviéndose.
- **`@nuxt/image`** con `NuxtImg`. Todo host remoto va en `image.domains`.
- **Gestor de paquetes: `pnpm`.** Nunca `npm install`, nunca crees un
  `package-lock.json`.

## Reglas que no se negocian

1. **Tokens semánticos, nunca shades crudos.** Escribe `text-highlighted`,
   `bg-elevated`, `ring-primary`. Jamás `text-neutral-700` ni
   `var(--ui-color-primary-500)`: los shades son valores fijos, iguales en
   light y en dark, y no se adaptan. Lo que cambia con el tema es el alias
   (`--ui-primary` es 500 en light y 400 en dark).

2. **`RevealOnScroll` para entradas de scroll, `@vueuse/motion` para lo demás.**
   Una entrada de 700 ms no necesita un módulo de animación. Y `v-motion` nunca
   va *sobre* un componente de Nuxt UI: va en un `<div>` propio.

3. **Respeta `prefers-reduced-motion` en toda animación.** Las componentes
   propias lo consultan con `useMediaQuery`.

4. **Nada de librerías nuevas** sin justificarlo antes. La plantilla está
   completa a propósito.

5. **Un `UContainer` por página.** `UHeader` y `UFooter` ya traen el suyo, y
   `UPageSection` envuelve en otro: meterlo dentro es doble centrado.

6. **Comentarios explicando el *porqué*, en español y en la tongue del
   proyecto.** Cuando algo parezca absurdo, la respuesta suele estar escrita
   justo encima. Si una explicación queda desfasada, corrígela: está peor un
   motivo falso que ningún motivo.

## Trampas ya pagadas

- **Las clases de Tailwind construidas con interpolación se purgan.**
  `:ui="{ root: \`[--duration:${duration}s]\` }"` no existe para Tailwind: la
  animación sale con el valor por defecto del tema, sin error ni aviso. Las
  custom properties van en `:style`.
- **`v-model` en `vue3-carousel`, no `v-model:currentSlide`.** La librería no
  tiene ese prop ni ese evento; solo emite `update:modelValue`. Con el nombre
  equivocado nada falla, simplemente no se mueve.
- **Los `.d.ts` de `vue3-carousel` solo declaran los props**, no el `expose`.
  `slideTo`, `next` y `prev` existen en runtime pero TS2339. Tipa el ref:
  `useTemplateRef<CarouselApi>('main')`.
- **Nada de `filter` ni de ancestros transformados sobre elementos `fixed` o
  sobre los `position: absolute` de los carruseles.** Un `filter` crea bloque
  contenedor y las flechas se posicionan mal. Por eso `blur` no se combina.
- **Los degradados de `UMarquee` se salen de su caja.** La sección necesita
  `overflow-hidden` o aparece scroll horizontal en toda la página.
- **Las APIs copiadas de `swiper` o de Embla no existen aquí.** Antes de usar
  un prop o un evento de un ejemplo de internet, verifícalo en
  `node_modules/<lib>/dist/*.d.ts`.

## Antes de terminar

```bash
npx eslint .
```

No corras `build`, `typecheck`, el dev server ni hagas render. La verificación
de esta plantilla es estática.

## Si algo no cuadra

Antes de "arreglarlo", comprueba si es un bug real o un `.d.ts` o un ejemplo
de internet que miente. Pasa con frecuencia, y el arreglo correcto suele ser
documentarlo, no tocar el código.

## Git

Rama de trabajo desde `develop`, nunca en `develop` directamente. Se integra
con `git merge --no-ff` a `develop`. `main` solo recibe releases, con tag.
````

## Documentación

- [Nuxt UI](https://ui.nuxt.com)
- [Tailwind CSS v4](https://tailwindcss.com)
- [Nuxt Fonts](https://nuxt.com/modules/fonts)
- [Nuxt Image](https://nuxt.com/modules/image)
- [VueUse Motion](https://motion.vueuse.js.org) — solo para la sección *Lo que
  CSS no puede hacer*; el reveal de scroll no lo usa