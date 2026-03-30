# RestGuard

**Stress management meets smart scheduling.** RestGuard is an Android app that combines health signals from Health Connect with calendar analysis to predict stress, surface actionable recommendations, and help you protect your energy before burnout hits.

---

## About

RestGuard monitors your physiological state (sleep, heart rate, HRV) and your meeting schedule to calculate a real-time stress score. It learns from your patterns over time and offers context-aware suggestions: reschedule a low-priority meeting, take a breathing break, or block recovery time. Everything is explained — you always know *why* a recommendation was made and *what* it will change.

Health data never leaves your device. Only meeting metadata (titles, not descriptions) is optionally sent to an AI service for importance classification, and you can disable that entirely.

---

## Features

- **Real-time stress gauge** — Animated 0-100 score with 4-7-8 breathing visualization
- **Multi-signal scoring** — Fuses physiological data (45%), calendar pressure (35%), and learned patterns (20%)
- **Calendar pressure analysis** — Detects meeting density, back-to-back blocks, early/late meetings, and context switching
- **Smart recommendations** — Reschedule, cancel, or add recovery time based on stress level and meeting importance
- **Meeting importance assessment** — Hybrid LLM + rule-based classification (works offline too)
- **Historical learning** — Tracks which meeting types raise your stress over time using exponential moving averages
- **Activity suggestions** — 4-7-8 breathing, short walks, screen breaks, power naps — matched to your current stress level
- **Subjective check-ins** — Log your mood and energy to calibrate the scoring algorithm
- **Multi-account calendar support** — Select which Google Calendar accounts to include
- **Always-on notification** — Persistent status with quick actions, escalates priority when stress is extreme
- **Privacy-first** — All health data processed on-device, full data export and deletion in settings

---

## How Stress Scoring Works

```
stress = 0.45 x physiological + 0.35 x calendar_pressure + 0.20 x historical_pattern
```

| Component | Sources | Range |
|---|---|---|
| Physiological | Sleep duration & quality, HRV (RMSSD), resting HR, activity minutes | 0-100 |
| Calendar Pressure | Meeting count, total duration, back-to-back blocks, timing | 0-100 |
| Historical Pattern | Learned stress deltas from past meetings of similar type | 0-100 |

**Stress levels:**
| Level | Score | Response |
|---|---|---|
| Low | 0-30 | Monitor only, warn if future overload detected |
| Moderate | 31-55 | Suggest stress-relief activities |
| High | 56-79 | Recommend rescheduling non-essential meetings |
| Extreme | 80-100 | Same-day intervention with top 3 actionable items |

Weights are personalized over time using feedback from your subjective check-ins.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3, dark theme)
- **Architecture:** Clean Architecture with Hilt DI
- **Database:** Room
- **Health:** Health Connect SDK
- **Calendar:** Android CalendarProvider (Instances API)
- **Background:** WorkManager + Foreground Service
- **AI:** Claude API (optional, for meeting importance classification)
- **Build:** Gradle KTS, KSP, R8/ProGuard minification
- **CI/CD:** GitHub Actions (tag-triggered releases with signed APKs)

---

## Project Structure

```
app/src/main/java/com/restguard/
├── data/
│   ├── local/          # Room database, entities, DAOs
│   ├── preferences/    # DataStore user preferences
│   ├── remote/         # Claude API client
│   └── repository/     # Repository implementations (real + fake)
├── di/                 # Hilt dependency injection module
├── domain/
│   ├── model/          # Domain models (25+ data classes)
│   ├── repository/     # Repository interfaces
│   └── service/        # Business logic (scoring, recommendations, learning)
├── notification/       # Foreground service & notification management
└── ui/
    ├── dashboard/      # Main stress gauge & metrics screen
    ├── history/        # Historical stress patterns
    ├── onboarding/     # Permission setup flow
    ├── settings/       # Preferences & calendar selector
    ├── common/         # Shared composables & permission handling
    └── theme/          # Colors, typography, dark theme
```

---

## Permissions

| Permission | Purpose |
|---|---|
| Health Connect (heart rate, HRV, sleep, steps, exercise, respiratory) | Physiological stress scoring |
| Calendar (read/write) | Meeting analysis and rescheduling |
| Contacts (read) | Attendee lookup for meeting context |
| Notifications | Persistent stress status and alerts |
| Internet | Optional AI-based meeting classification |

---

## Building

```bash
# Debug build (uses real data sources)
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease
```

Create `local.properties` with your Claude API key for AI features:
```properties
CLAUDE_API_KEY=your_key_here
```

To use fake data sources for development, set `USE_FAKES=true` in `app/build.gradle.kts`.

---

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.
