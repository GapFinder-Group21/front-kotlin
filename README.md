# GAP FINDER — Android (Kotlin + Jetpack Compose)

App Android nativa de GAP FINDER. Tiene la misma interfaz que la versión iOS: mismos colores, tipografías (Montserrat, Magra y Cambay), íconos y flujos.

## Requisitos

- Android Studio (Ladybug o más reciente) con JDK 17
- Android SDK 36
- Dispositivo o emulador con Android 8.0 (API 26) o superior

## Cómo correrla

1. Abre la carpeta `front-kotlin` en Android Studio y deja que sincronice Gradle.
2. Cambia la IP del backend en `app/src/main/java/co/gapfinder/mobile/foundation/ServerEnv.kt`.
3. Ejecuta la configuración `app`.

## Estructura

```
co.gapfinder.mobile
├── foundation/   red (HttpGateway), sesión cifrada (SessionVault), chat activo (LiveChatHub), JSON y fechas
├── domain/       modelos (Member, FreeWindow, Pairing, Hangout, ...) y enums
├── data/         repositorios REST y GeoProbe (GPS / permisos)
├── ui/theme/     Palette y Typo
├── ui/kit/       componentes reutilizables (PillButton, FormField, DockBar, ...)
├── ui/nav/       Destination, StackNavigator, AppStage, Toaster
└── screens/      onboarding, home (pestañas), pairing (match + chat), hangouts (open tables)
```

La navegación usa una pila propia (`StackNavigator`). Las pantallas de abajo en la pila siguen vivas, así que conservan su estado y sus timers mientras estás en otra pantalla.
