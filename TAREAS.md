# Tareas — front Kotlin

El front en Kotlin se actualizó para seguir a la versión Flutter (`front`), que ahora consume el backend nuevo: usuarios con carrera, semestre numérico y teléfono; match armado sobre los gaps; open tables con cupo; amigos cercanos, búsqueda de usuarios y recomendaciones.

El trabajo está repartido en 44 commits, 22 para cada una. Rutas relativas a `app/src/main/java/co/gapfinder/mobile/`.

> El proyecto compila cuando estén todos los commits de las dos, porque `ui/nav` y `screens/hangouts` usan clases de `data/`, `screens/home` y `screens/pairing`.
> **Orden para mergear:** primero los 3 PRs de Sofía, en orden, y luego los 3 de Daniela, en orden. Usar **"Create a merge commit"** (no squash) para que se conserven los commits.

**Reparto:** Sofía: base + dominio + onboarding + open tables + navegación (≈3.940 líneas propias) · Daniela: datos + home + match (≈4.045 líneas)

---

## Sofía — `sofiariasz2`

### PR 1 · `sofiafront` — dominio, onboarding y open tables (versión inicial)

1. `agregar modelos y enums del dominio`
2. `agregar pantallas de bienvenida, registro e inicio de sesión`
3. `agregar permiso de ubicación y selección de intereses`
4. `agregar configuración de horario y conexión con Google Calendar`
5. `agregar componentes compartidos de open tables`
6. `agregar mapa de open tables`
7. `agregar creación de open tables`
8. `agregar detalle de open table y mis open tables`

### PR 2 · `sofia-nuevo-backend` — dominio, registro y open tables sobre el backend nuevo

9. `actualizar enums del dominio al backend nuevo`
   - `domain/Kinds.kt`: esfuerzo LOW/MEDIUM/HIGH, modos de match (carrera, intereses, esfuerzo), tipos de notificación, estados de open table y paso PARTICIPANTS
10. `actualizar modelos de usuario, actividad, edificio, gap y amistad`
    - `domain/Records.kt`: `Member` (carrera, semestre, teléfono), `Hobby`, `Pastime`, `CampusSpot`, `FreeWindow`, `Bond`
11. `actualizar modelos de match, open table y notificación, y agregar recomendación`
    - `domain/Records.kt`: `Pairing`, `PairingProspect`, `Hangout`, `HangoutAttendee`, `Alert`, `LocationPing`, `LoginTicket`, `HangoutDropOff`, `Recommendation`
    - `foundation/JsonKit.kt`: `decimalOrNull`
12. `agregar teléfono, semestre numérico y validaciones al registro`
    - `screens/onboarding/SignUpPage.kt`
13. `habilitar la importación de horario con Google Calendar`
    - `screens/onboarding/TimetableSetupPage.kt`
14. `mostrar cupo y estado de participación en el mapa de open tables`
    - `screens/hangouts/HangoutBits.kt`, `screens/hangouts/HangoutMapTab.kt`
15. `agregar cupo máximo al crear open tables`
    - `screens/hangouts/HangoutComposerPage.kt`
16. `mostrar participantes y cupo en el detalle de open table`
    - `screens/hangouts/HangoutDetailPage.kt`
17. `actualizar mis open tables con el listado nuevo`
    - `screens/hangouts/MyHangoutsPage.kt`

### PR 3 · `sofia-navegacion-amigos-cercanos` — sesión, navegación y amigos cercanos

18. `refrescar la sesión cuando el backend responde 403`
    - `foundation/HttpGateway.kt`
19. `reemplazar perfil por recomendaciones en la barra inferior y quitar el chat`
    - `ui/kit/DockBar.kt`, `ui/kit/Glyphs.kt`; se eliminan `ui/kit/ChatFab.kt` y `foundation/LiveChatHub.kt`
20. `actualizar rutas de match y agregar la de detalle de gap`
    - `ui/nav/Destination.kt`, `ui/nav/AppStage.kt`
21. `agregar aviso de amigos cercanos al cambiar de edificio`
    - `ui/kit/NearbyFriendsDialog.kt`, `ui/kit/SpotStatusLine.kt`
22. `actualizar README y reparto de tareas`
    - `README.md`, `TAREAS.md`

---

## Daniela — `dani3laM`

### PR 1 · `danielafront` — repositorios REST y GeoProbe

1. `agregar repositorios de sesión y usuarios`
   - `data/Repositories.kt`: `AccountRepository`, `MemberRepository`
2. `agregar repositorios de intereses, actividades y edificios`
   - `data/Repositories.kt`: `HobbyRepository`, `PastimeRepository`, `SpotRepository`
3. `agregar repositorios de horario, gaps y Google Calendar`
   - `data/Repositories.kt`: `LectureRepository`, `WindowRepository`, `CalendarBridge`
4. `agregar repositorios de amistades, amigos cercanos y grupos`
   - `data/Repositories.kt`: `BondRepository`, `NearbyRepository`, `CrewRepository`
5. `agregar repositorios de match, mensajes y notificaciones`
   - `data/Repositories.kt`: `PairingRepository`, `ChatLineRepository`, `AlertRepository`
6. `agregar repositorios de open tables, participantes y recomendaciones`
   - `data/Repositories.kt`: `HangoutRepository`, `AttendeeRepository`, `DropOffRepository`, `RecommendationRepository`, `PingRepository`
7. `agregar GeoProbe para GPS y permisos de ubicación`
   - `data/GeoProbe.kt`

### PR 2 · `daniela-home` — pestañas del home

8. `agregar utilidades compartidas de las pestañas`
   - `screens/home/HomeTabKit.kt`: `Fetch`, `OnTabWake`, `twelveHourClock`, `dartStyleText`
9. `agregar contenedor home con las cinco pestañas`
   - `screens/home/HomeShell.kt`
10. `agregar pestaña de horario con los gaps de la semana`
    - `screens/home/TimetableTab.kt`
11. `agregar detalle de gap`
    - `screens/home/WindowDetailPage.kt`
12. `agregar pestaña de amigos`
    - `screens/home/FriendsTab.kt`: amigos con GAP ahora y amigos en clase
13. `agregar búsqueda de usuarios y solicitudes de amistad`
    - `screens/home/FriendsTab.kt`: buscador, enviar solicitud, aceptar y rechazar
14. `agregar pestaña de match`
    - `screens/home/PairingTab.kt`
15. `agregar pestaña de recomendaciones`
    - `screens/home/RecommendationsTab.kt`

### PR 3 · `daniela-match` — flujo de match

16. `agregar componentes compartidos de match`
    - `screens/pairing/PairingBits.kt`
17. `agregar búsqueda de match sobre el gap activo`
    - `screens/pairing/PairingSearchPage.kt`
18. `agregar pantalla de candidato de match`
    - `screens/pairing/PairingCandidatePage.kt`
19. `agregar espera de respuesta del match`
    - `screens/pairing/PairingPendingPage.kt`
20. `agregar invitación de match`
    - `screens/pairing/PairingInvitePage.kt`
21. `agregar confirmación de match con teléfono de contacto`
    - `screens/pairing/PairingConfirmedPage.kt`
22. `abrir la invitación cuando llega una solicitud de match`
    - `screens/home/HomeShell.kt`: sondeo de notificaciones cada 10 s
