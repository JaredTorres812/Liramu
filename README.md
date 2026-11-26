<p align="center">
  <img src="assets/liramu_logo.jpg" alt="Liramu Logo" width="148" style="border-radius: 32px;" />
</p>

<h1 align="center">Liramu (Beta)</h1>

<p align="center">
  <strong>Cliente moderno de Apple Music para Android diseñado con Material 3 Expressive</strong><br/>
  <em>A modern Material 3 Expressive Apple Music client for Android (Phone, Tablet & Android Auto)</em>
</p>

---

> [!IMPORTANT]
> **Aviso importante / Disclaimer**
> - **Liramu es un proyecto independiente y NO está afiliado, asociado, patrocinado ni relacionado de ninguna manera con Apple Inc. ni con Apple Music.**
> - **Requiere una suscripción activa de Apple Music** para iniciar sesión, acceder a tu biblioteca y reproducir música o videos completos.
> - **Estado Beta:** El proyecto se encuentra actualmente en **fase Beta**. Puede presentar detalles por pulir; ¡esperamos que les agrade el proyecto y cualquier sugerencia o reporte es bienvenido!

---

## Características principales

- **Diseño Material 3 Expressive & Material You:** Interfaz fluida y 100% opaca con colores dinámicos extraídos de cada portada, icono adaptativo con soporte para iconos temáticos de Android y diseño adaptado para **teléfonos y tablets**.
- **Inicio de sesión en un solo paso:** Conexión directa con tu cuenta de Apple Music sin pasos intermedios.
- **Reproducción avanzada:**
  - Soporte nativo para **Dolby Atmos**, **Crossfade**, **AutoMix** y continuidad **gapless** en álbumes.
  - **Ecualizador de bandas + Bass Boost** integrado.
  - **Temporizador de apagado (Sleep Timer)** integrado en el menú del reproductor.
  - **Continuidad de reproducción** al cerrar y volver a abrir la app.
  - **Videos musicales completos**, Picture-in-Picture (PiP), pantalla completa sin cortes y reproducción continua en cola.
- **Letras sincronizadas + Traducción:**
  - Letras sincronizadas palabra por palabra estilo Sing a 60 fps (con soporte de dúos, coros e interludios instrumentales).
  - Botón de **traducción integrada** (TTML nativo + traducción automática al idioma de la app).
- **Biblioteca y Sincronización:**
  - Apertura instantánea con caché ligero y actualización automática.
  - **Fijados (Pins)** arriba de la biblioteca sincronizados con tu cuenta.
  - Sincronización automática de **Artistas favoritos (★)** y **Escuchado recientemente**.
- **Apple Music Replay (Material 3 Expressive):** Resúmenes anuales y mensuales rediseñados con tarjetas Bento, podio #1 y géneros principales.
- **Android Auto:** Soporte integrado mediante `MediaBrowserService` con portada HD, controles del volante y cola de reproducción.
- **Bilingüe:** Interfaz completa en **Español** e **English**.

---

## Cómo compilar

1. Clona este repositorio:
   ```bash
   git clone https://github.com/Jeztorres/Liramu.git
   cd Liramu
   ```
2. Copia `apple_music.properties.example` a `apple_music.properties` y configura tus credenciales de Apple Music (si deseas usar el SDK nativo) o déjalo con tu storefront por defecto (`mx`, `us`, etc.):
   ```bash
   cp apple_music.properties.example apple_music.properties
   ```
   > `apple_music.properties` y cualquier archivo `.p8` están bloqueados en `.gitignore` para proteger tus llaves.
3. Compila con **JDK 21**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Créditos y Comunidad

Proyecto desarrollado con Kotlin, Jetpack Compose y Material 3 Expressive.  
**¡Esperamos que les agrade Liramu!** Si encuentras un error o tienes una idea de mejora, abre un *Issue* o *Pull Request*.
