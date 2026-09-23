# Tareas — front Kotlin

El repo ya tiene la base: configuración de Gradle, recursos, `foundation/` (red, sesión, JSON), `ui/theme`, `ui/kit` (componentes) y `ui/nav` (navegación).

Faltan `domain/`, `data/` y `screens/`. Están divididos en 16 commits, 8 para cada una. Rutas relativas a `app/src/main/java/co/gapfinder/mobile/`.

> El proyecto compila solo cuando estén todos los commits, porque `ui/nav` y `foundation/` ya usan clases de `domain/`, `data/` y `screens/`.
> **Orden recomendado:** primero el commit 1 de Sofía (dominio) y el commit 1 de Daniela (repositorios), porque todas las pantallas dependen de ellos.

**Reparto:** Sofía: dominio + onboarding + open tables (≈3.860 líneas) · Daniela: datos + home + match y chat (≈3.910 líneas)

---

## Sofía — `sofiariasz2`

### 1. `agregar modelos y enums del dominio`
- `domain/Kinds.kt`: enums `EnergyLevel`, `Weekday`, `BondState`, `PairingState`, `PairingFocus`, `AlertKind`, `HangoutState`, `Rsvp`, `DraftStage`
- `domain/Records.kt`: modelos `Member`, `Hobby`, `Pastime`, `CampusSpot`, `SpotDwell`, `Lecture`, `FreeWindow`, `Bond`, `Crew`, `Pairing`, `PairingProspect`, `ChatLine`, `Hangout`, `HangoutAttendee`, `Alert`, `LocationPing`, `LoginTicket`, `HangoutDropOff`, `DropOffStat`

### 2. `agregar pantallas de bienvenida, registro e inicio de sesión`
- `screens/onboarding/LandingPage.kt`: `LandingPage`
- `screens/onboarding/SignUpPage.kt`: `SignUpPage`, `FieldCaption`, `OptionPicker`
- `screens/onboarding/SignInPage.kt`: `SignInPage`

### 3. `agregar permiso de ubicación y selección de intereses`
- `screens/onboarding/LocationGatePage.kt`: `LocationGatePage`
- `screens/onboarding/HobbyPickerPage.kt`: `HobbyPickerPage`, `HobbyCloud`, `HobbyChip`

### 4. `agregar configuración de horario y conexión con Google Calendar`
- `screens/onboarding/TimetableSetupPage.kt`: `TimetableSetupPage`, `OptionBadge`
- `screens/onboarding/CalendarLinkPage.kt`: `CalendarLinkPage`, `RetryLink`
- `screens/onboarding/CalendarImportPage.kt`: `CalendarImportPage`, `StageBadge`, `StageTitle`, `StageNote`

### 5. `agregar componentes compartidos de open tables`
- `screens/hangouts/HangoutBits.kt`: `PillAction`, `BlurbCaption`, `BlurbBox`, `PastimeTag`, `asDartString`, `plainText`

### 6. `agregar mapa de open tables`
- `screens/hangouts/HangoutMapTab.kt`: `HangoutMapTab`, `BoardHeader`, `MapBlock`, `LegendDot`, `ListingBlock`, `DiscoverCard`

### 7. `agregar creación de open tables`
- `screens/hangouts/HangoutComposerPage.kt`: `HangoutComposerPage`, `CardSection`, `PickerField`, `BlurbInput`, `FlatTextAction`, `ChoiceSheet`, `SheetRow`

### 8. `agregar detalle de open table y mis open tables`
- `screens/hangouts/HangoutDetailPage.kt`: `HangoutDetailPage`, `SectionCaption`, `HostCard`, `PastimeCard`, `FactTile`, `AttendeeStrip`
- `screens/hangouts/MyHangoutsPage.kt`: `MyHangoutsPage`, `GroupTitle`, `FaultState`, `NothingYet`, `HistoryCard`, `StateBadge`

---

## Daniela

### 1. `agregar repositorios REST y GeoProbe`
- `data/Repositories.kt`: `AccountRepository`, `PastimeRepository`, `SpotRepository`, `LectureRepository`, `BondRepository`, `WindowRepository`, `CalendarBridge`, `CrewRepository`, `HobbyRepository`, `PairingRepository`, `ChatLineRepository`, `AlertRepository`, `DropOffRepository`, `AttendeeRepository`, `HangoutRepository`, `PingRepository`, `MemberRepository`
- `data/GeoProbe.kt`: `GeoProbe` (`bind`, `unbind`, `gpsSwitchedOn`, `isReady`, `askPermission`, `openAppSettings`, `openLocationSettings`, `currentFix`, `gpsChanges`), `GeoAccess`, `GpsSwitch`

### 2. `agregar contenedor home y utilidades de pestañas`
- `screens/home/HomeShell.kt`: `HomeShell`, `onlyWhen`
- `screens/home/HomeTabKit.kt`: `Fetch`, `OnTabWake`, `twelveHourClock`, `dartStyleText`

### 3. `agregar pestañas de horario y perfil`
- `screens/home/TimetableTab.kt`: `TimetableTab`, `weaveDay`, `TodayAgenda`, `LectureCard`, `BreakCard`
- `screens/home/ProfileTab.kt`: `ProfileTab`, `IdentityPanel`, `PhotoRing`, `SettingsList`, `SettingsRow`

### 4. `agregar pestañas de amigos y match`
- `screens/home/FriendsTab.kt`: `FriendsTab`, `RosterTopBar`, `OverlappingFaces`, `GroupCaption`, `FreeBuddyCard`, `PairUpPill`, `AttendingGrid`, `FaceBubble`, `LonelyNotice`, `TroubleNotice`
- `screens/home/PairingTab.kt`: `PairingTab`, `OpenWindowCard`, `NoWindowCard`, `LivePairingBanner`

### 5. `agregar componentes compartidos de match`
- `screens/pairing/PairingBits.kt`: `SpinnerRing`, `BareLoadingPage`, `RingedPhoto`, `QuietAction`, `dropGlow`, `asBody`, `asButtonText`, `dartText`

### 6. `agregar búsqueda y candidato de match`
- `screens/pairing/PairingSearchPage.kt`: `PairingSearchPage`
- `screens/pairing/PairingCandidatePage.kt`: `PairingCandidatePage`, `SectionCaption`, `HobbyChips`, `WindowSummary`, `ProspectCard`

### 7. `agregar espera, invitación y confirmación de match`
- `screens/pairing/PairingPendingPage.kt`: `PairingPendingPage`
- `screens/pairing/PairingInvitePage.kt`: `PairingInvitePage`, `InviteCaption`, `SenderCard`
- `screens/pairing/PairingConfirmedPage.kt`: `PairingConfirmedPage`, `PastimeCard`, `EnergyBadge`, `ChatLaunchButton`

### 8. `agregar chat`
- `screens/pairing/ConversationPage.kt`: `ConversationPage`, `TimerBanner`, `LineBubble`, `Composer`
