// ======================================================================
// DTO PUBLICOS DE LA API
//
// Espejo de api/src/main/java/dev/pepe1603/api/dto/publicapi: la fuente de
// verdad del contrato es docs/API.md, y si este archivo y la API divergen,
// manda la API. Los campos opcionales del backend (CV sin subir, enlaces
// sin configurar) llegan como null, no como ausencia: el tipo lo refleja
// para que la UI tenga que comprobarlo antes de pintar un enlace roto.
// ======================================================================

export interface Skill {
  name: string
  category: string
  level: string | null
}

export interface Experience {
  title: string
  company: string
  period: string
  type: string
  description: string
}

export interface ProfilePublicDTO {
  fullName: string
  headline: string
  bio: string
  location: string
  githubUrl: string | null
  linkedinUrl: string | null
  emailPublic: string | null
  websiteUrl: string | null
  cvUrlEs: string | null
  cvUrlEn: string | null
  avatarUrl: string | null
  skills: Skill[]
  experiences: Experience[]
}

/** Body de POST /contact. `website` es el honeypot: debe llegar vacío. */
export interface ContactRequest {
  name: string
  email: string
  subject: string
  body: string
  website?: string
}

export interface GalleryImage {
  url: string
  alt: string | null
  caption: string | null
}

export interface ProjectSummaryDTO {
  slug: string
  title: string
  subtitle: string
  summary: string
  stack: string[]
  thumbnailUrl: string | null
  /** LocalDate del backend: 'yyyy-MM-dd' en JSON. */
  periodStart: string | null
  periodEnd: string | null
  featured: boolean
}

export interface ProjectDetailDTO {
  slug: string
  title: string
  subtitle: string
  summary: string
  descriptionMd: string
  stack: string[]
  thumbnailUrl: string | null
  gallery: GalleryImage[]
  repoUrl: string | null
  demoUrl: string | null
  periodStart: string | null
  periodEnd: string | null
  featured: boolean
}

export interface CertificatePublicDTO {
  title: string
  issuer: string
  /** 'certificate' | 'course' en minúsculas; otro valor → 400 (docs/API.md). */
  kind: string
  issueDate: string | null
  expiryDate: string | null
  credentialUrl: string | null
  imageUrl: string | null
  featured: boolean
}
