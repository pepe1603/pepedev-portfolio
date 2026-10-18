// ======================================================================
// TIPOS ADMIN DE LA API
//
// Espejo de api/src/main/java/dev/pepe1603/api/entity (Project y
// AdminProjectController) y de docs/API.md. Si este archivo y la API
// divergen, manda la API.
//
// --------------------------------------------------------------------------
// LA DIFERENCIA CON LOS DTO PÚBLICOS (utils/types.ts)
//
// /public/** devuelve el texto YA RESUELTO al idioma pedido: una sola
// cadena. /admin/** devuelve la ENTIDAD tal cual la guarda la base de
// datos, con los campos bilingües sin resolver (`{"es": "...", "en": "..."}`)
// en columnas JSONB. De ahí LocalizedText, que no existe en el lado
// público.
//
// Los campos nullable del backend llegan como `null`, no como ausencia, y
// el tipo lo refleja para que la UI tenga que comprobarlo antes de pintar.
// ======================================================================

/** Campo bilingüe tal y como lo guarda la base de datos (JSONB {es,en}). */
export interface LocalizedText {
  es?: string
  en?: string
}

/** Estado de un proyecto o certificado. `POST` crea siempre DRAFT. */
export type ProjectStatus = 'DRAFT' | 'PUBLISHED'

/** Registro de la galería. Mismo shape que GalleryImage del lado público. */
export interface AdminGalleryImage {
  url: string
  alt: string | null
  caption: string | null
}

/**
 * Entidad que devuelven GET /admin/projects y GET /admin/projects/{id}.
 * Trae más que el DTO público: id, status, fechas de publicación y
 * auditoría, y los textos bilingües sin resolver.
 */
export interface AdminProject {
  id: string
  slug: string
  title: LocalizedText
  subtitle: LocalizedText | null
  summary: LocalizedText | null
  descriptionMd: LocalizedText | null
  thumbnailUrl: string | null
  gallery: AdminGalleryImage[]
  repoUrl: string | null
  demoUrl: string | null
  stack: string[]
  /** LocalDate del backend: 'yyyy-MM-dd' en JSON. */
  periodStart: string | null
  periodEnd: string | null
  featured: boolean
  sortOrder: number
  status: ProjectStatus
  /** Instant del backend: ISO-8601. null si sigue en DRAFT. */
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Body de POST y PUT /admin/projects (ProjectRequest en la API).
 *
 * OJO con `featured`: es un boolean PRIMITIVO en Java y Spring Boot 4.x
 * mantiene FAIL_ON_NULL_FOR_PRIMITIVES, así que omitirlo no es un error de
 * validación sino de parseo: 400 "Failed to read request". Enviarlo
 * SIEMPRE, aunque sea false.
 *
 * `PUT` es edición completa: lo que no venga se guarda como null (salvo
 * sortOrder→0 y gallery/stack→[]), así que hay que mandar todo.
 */
export interface ProjectRequest {
  slug: string
  title: LocalizedText
  subtitle?: LocalizedText | null
  summary?: LocalizedText | null
  descriptionMd?: LocalizedText | null
  thumbnailUrl?: string | null
  gallery?: AdminGalleryImage[]
  repoUrl?: string | null
  demoUrl?: string | null
  stack?: string[]
  /** LocalDate: 'yyyy-MM-dd'. */
  periodStart?: string | null
  periodEnd?: string | null
  /** Primitivo en la API: enviar siempre, aunque sea false. */
  featured: boolean
  sortOrder?: number
}

/**
 * Usos permitidos de POST /admin/storage. `image` es el genérico (imagen de
 * certificado); `cv` es el único que admite PDF.
 */
export type StorageUse = 'avatar' | 'thumbnail' | 'gallery' | 'image' | 'cv'

/** Respuesta de POST /admin/storage: la URL pública ya montada por la API. */
export interface StorageUploadResult {
  url: string
}
