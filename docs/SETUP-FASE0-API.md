# Guía Fase 0 — Creación del backend (Spring Boot) paso a paso

> La haces tú con Spring Initializr + IntelliJ. Esta guía es tu checklist.

## Paso 1 · Maven o Gradle?

**Maven**. Es el estándar en ofertas de trabajo enterprise, tiene más documentación y
es lo que definimos en `docs/ARCHITECTURE.md`. No uses Gradle en este proyecto.

## Paso 2 · Configuración en start.spring.io

Abre <https://start.spring.io> y configura:

| Campo | Valor |
|---|---|
| Project | **Maven** |
| Language | **Java** |
| Spring Boot | Versión estable por defecto (la que sugiere el sitio, nunca SNAPSHOT) |
| Group | `dev.pepe1603` |
| Artifact | `portfolio-api` |
| Name | `portfolio-api` |
| Package name | `dev.pepe1603.portfolioapi` |
| Packaging | **Jar** |
| Java | **21** |

## Paso 3 · Dependencias (búscalas por nombre en Initializr)

Obligatorias:

- [ ] **Spring Web** (API REST)
- [ ] **Spring Data JPA** (Hibernate)
- [ ] **Flyway Migration** (migraciones de BD)
- [ ] **PostgreSQL Driver**
- [ ] **Spring Security** (auth CRM con JWT)
- [ ] **Spring Data Redis (Access+Driver)** (caché y rate limit)
- [ ] **Java Mail Sender** (formulario de contacto)
- [ ] **Validation** (validación de DTOs)
- [ ] **Spring Boot Actuator** (health checks)

Recomendadas:

- [ ] **Lombok** (menos boilerplate en entidades/DTOs)
- [ ] **Spring Boot DevTools** (reinicio rápido en desarrollo)

Se agregan DESPUÉS a mano en el `pom.xml` (Initializr no las tiene):

```xml
<!-- Swagger UI / OpenAPI -->
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.8.9</version>
</dependency>

<!-- JWT (verifica la última 0.12.x en mvnrepository) -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```

## Paso 4 · Ubicar el proyecto

1. GENERATE → descarga `portfolio-api.zip`.
2. Extrae el contenido en la RAÍZ del monorepo (`pepedev-portfolio/`), de modo que la carpeta
   `portfolio-api/` quede junto a `docs/` (el `pom.xml` debe quedar directamente en
   `portfolio-api/pom.xml`, no en `portfolio-api/portfolio-api/`).
3. En IntelliJ: **Open** → selecciona la carpeta `portfolio-api` → espera a que indexe Maven.

## Paso 5 · Secretos y tokens (IMPORTANTE, hazlo ANTES del primer commit)

Nunca pegues credenciales en código ni en `application.yml`.

1. Crea `.gitignore` en la RAÍZ del repo con al menos:

   ```gitignore
   .env*
   !.env.example
   target/
   node_modules/
   .nuxt/
   .output/
   *.iml
   .idea/
   ```

2. Crea `.env.example` (plantilla pública, sin valores reales) y `.env` (tus valores
   reales, jamás se sube a GitHub):

   ```bash
   SPRING_DATASOURCE_URL=jdbc:postgresql://IP_DE_TERRAMOUNT:5432/portfolio_dev
   SPRING_DATASOURCE_USERNAME=portfolio_app
   SPRING_DATASOURCE_PASSWORD=cambia_esto
   SPRING_DATA_REDIS_HOST=IP_DE_TERRAMOUNT
   SPRING_DATA_REDIS_PORT=6379
   SPRING_DATA_REDIS_PASSWORD=cambia_esto
   APP_JWT_SECRET=generar_con_openssl
   APP_JWT_REFRESH_SECRET=generar_otro_distinto
   APP_CORS_ALLOWED_ORIGINS=http://localhost:3000
   ```

3. Genera los secretos JWT (dos distintos) desde tu terminal:

   ```bash
   openssl rand -base64 64
   openssl rand -base64 64
   ```

4. Para subir a GitHub usa HTTPS con Personal Access Token guardado en el credential
   manager de Git (`git config credential.helper store`) o mejor SSH keys — nunca
   escribas el token dentro de archivos del proyecto.

## Paso 6 · Conexión a Terramount sin exponer la BD

Opción segura recomendada (túnel SSH, la BD ni siquiera necesita puerto abierto):

```bash
ssh -L 5432:localhost:5432 usuario@IP_TERRAMOUNT
# entonces en .env: jdbc:postgresql://localhost:5432/portfolio_dev
```

Alternativa directa: abre 5432/6379 en el firewall de Terramount SOLO a la IP pública
de tu conexión.

Crea la base de datos en Terramount (una vez):

```sql
CREATE DATABASE portfolio_dev;
CREATE USER portfolio_app WITH PASSWORD '...';
GRANT ALL PRIVILEGES ON DATABASE portfolio_dev TO portfolio_app;
```

## Paso 7 · `application.yml` mínimo

En `src/main/resources/application.yml` deja solo esto (los secretos llegan por entorno):

```yaml
spring:
  application:
    name: portfolio-api
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate   # el schema lo maneja Flyway, nunca Hibernate
    open-in-view: false
  flyway:
    enabled: true
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      password: ${SPRING_DATA_REDIS_PASSWORD:}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Nota: con Spring Security activo, `/actuator/health` quedará protegido por defecto;
lo abriremos en el `SecurityConfig` cuando creemos la configuración base.

## Paso 8 · Ejecutar y verificar

Desde IntelliJ: Run Configuration → Environment Variables → pega las de `.env`
(o instala el plugin **EnvFile** y apúntalo a `.env`).

Por terminal equivalente:

```bash
cd portfolio-api
set -a; source .env; set +a
./mvnw spring-boot:run
```

Verificación:

- [ ] Arranca sin errores de conexión (DB + Redis OK)
- [ ] `GET http://localhost:8080/actuator/health` responde (tras ajustar SecurityConfig)
- [ ] Flyway crea/aplica migraciones sin error

## Paso 9 · Git y GitHub

```bash
cd ~/Projects/pepedev-portfolio
git init
git add .
git commit -m "chore: fase 0 - estructura monorepo y api base"
git branch -M main
git remote add origin https://github.com/pepe1603/pepedev-portfolio.git
git push -u origin main
```

Antes del push revisa: `git status` NO debe mostrar `.env`.

## Checklist final de Fase 0 (backend)

- [ ] Proyecto generado con las dependencias del Paso 3
- [ ] Abre y compila en IntelliJ (`mvn clean verify`)
- [ ] `.env` creado, `.gitignore` protegiéndolo, secretos JWT generados
- [ ] Conexión verificada a PostgreSQL y Redis de Terramount
- [ ] Health endpoint funcionando
- [ ] Push inicial a GitHub hecho
