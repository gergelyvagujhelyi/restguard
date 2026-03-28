# RestGuard — Complete Technical Design Document

## 5. Stress Scoring Design

### Formula

```
currentStress = 0.45 × physiological + 0.35 × calendarPressure + 0.20 × historicalPattern
```

Each component produces a 0–100 score.

### Physiological Score (0–100)

| Signal | Weight | Ideal | Stressed |
|--------|--------|-------|----------|
| Sleep duration | 0.35 | ≥8h → 10 | ≤4h → 85+ |
| HRV (RMSSD) | 0.30 | ≥50ms → 10 | ≤20ms → 80+ |
| Resting HR | 0.20 | ≤60bpm → 10 | ≥85bpm → 80 |
| Activity | 0.15 | 30-90min → 15 | <30 or >150 → 35-70 |

Missing signals: weight redistributes among available signals. If all missing, score = 50.

### Calendar Pressure (0–100)

| Component | Max Contribution |
|-----------|-----------------|
| Meeting density (count / 8) | 60 |
| Total duration (mins / 480) | 20 |
| Back-to-back blocks | 15 |
| Early/late meeting timing | 5 |

### Near-Future Prediction

For future days, physiological data is unavailable. Weights shift:
```
predictedStress = 0.55 × calendarPressure + 0.45 × historicalPattern
```

### Thresholds

| Level | Score Range | Behavior |
|-------|------------|----------|
| LOW | 0–30 | No action unless future overload |
| MODERATE | 31–55 | Activity suggestions; reschedule if future HIGH |
| HIGH | 56–79 | Suggest cancel/reschedule non-essential (not today) |
| EXTREME | 80–100 | Same-day intervention; top 3 events; skip ranking |

### Confidence Scoring

Each data source contributes to confidence:
- Sleep: 0.25
- HRV: 0.20
- Resting HR: 0.15
- Activity: 0.10
- Calendar: 0.20
- History: 0.10

Total confidence = sum of available sources. Displayed to user.

---

## 6. Historical Learning Logic

### MVP: Exponential Moving Average (EMA)

```
delta = stress_after_meeting - stress_before_meeting
EMA_new = 0.3 × delta + 0.7 × EMA_old
```

- Sample window: stress samples within 2 hours before/after meeting
- Pattern key: normalized_title | recurrence | attendee_size_bucket
- Minimum 3 samples for confident classification

### Features Tracked Per Meeting Pattern

| Feature | Source |
|---------|--------|
| 1:1 vs group | attendee count |
| Recurring vs one-off | recurrence rule |
| Manager present | title keywords ("1:1") |
| Customer-facing | title/desc keywords |
| Morning vs afternoon | start time |
| Duration | start/end delta |
| Prep-heavy | desc keywords ("review", "demo", "presentation") |
| Online vs in-person | location field |

### Upgrade Path

| Phase | Method |
|-------|--------|
| MVP | EMA with pattern key grouping |
| Phase 3 | Logistic regression on features + EMA |
| Phase 4 | On-device lightweight model (decision tree or small NN) |
| Phase 5 | Personalized embeddings, cross-user anonymized insights |

---

## 7. Meeting Importance Assessment

### LLM Prompt Template

```
You are a meeting importance classifier. Analyze the following meeting and classify
its importance as HIGH, MEDIUM, or LOW.

Respond ONLY with valid JSON matching this exact schema:
{
  "importance": "HIGH" | "MEDIUM" | "LOW",
  "confidence": 0.0-1.0,
  "explanation": "One sentence explaining why."
}

Meeting details:
- Title: {{title}}
- Description: {{description}}
- Organizer: {{organizer}}
- Attendees: {{attendeeCount}} people
- Recurring: {{isRecurring}}
- Duration: {{duration}} minutes
- Time: {{timeOfDay}} on {{dayOfWeek}}
```

### LLM Response Schema (validated)

```json
{
  "importance": "MEDIUM",
  "confidence": 0.82,
  "explanation": "Regular team sync with no external stakeholders; can be rescheduled."
}
```

### Guardrails
1. LLM output is parsed into `MeetingPriorityAssessment` — invalid JSON falls back to rules
2. LLM never sees "cancel this" or "reschedule this" — only classifies
3. Confidence below 0.5 triggers rule-based cross-check
4. User can override any assessment

### Rule-Based Fallback

Scoring keywords:
- HIGH: "1:1", "customer", "interview", "incident", "board", "deadline"
- LOW: "optional", "social", "all-hands", "fyi", "book club", "happy hour"
- MEDIUM: "standup", "sync", "planning", "retro"

Structural signals: attendee count, self-organized, duration, recurrence.

---

## 8. Recommendation Engine — Decision Matrix

```
┌──────────────┬───────────────┬───────────────────────────────────────┐
│ Current      │ Future        │ Action                                │
│ Stress       │ Overload      │                                       │
├──────────────┼───────────────┼───────────────────────────────────────┤
│ LOW          │ LOW           │ No action                             │
│ LOW          │ HIGH          │ Suggest reschedule future meetings     │
│ MODERATE     │ LOW           │ Activity suggestion                   │
│ MODERATE     │ HIGH          │ Reschedule + activity                 │
│ HIGH         │ any           │ Cancel/reschedule non-essential       │
│ EXTREME      │ any           │ URGENT top 3, same-day OK            │
└──────────────┴───────────────┴───────────────────────────────────────┘
```

### Hard Rules
1. **Same-day cancellation FORBIDDEN** unless EXTREME stress
2. EXTREME mode: max 3 event candidates, skip normal ranking, go to priority assessment
3. Never auto-execute: all destructive actions need user confirmation
4. Every recommendation has an explanation

### Prioritization
- Sort by `importanceToSortKey(importance)`: LOW=1, MEDIUM=2, HIGH=3
- Lowest importance = highest recommendation priority
- Tie-breaking: higher `stressReduction` estimate wins

---

## 9. Rescheduling Engine

### Slot Scoring (lower = better)

```
slotScore = dayStress                    (0-100)
          + meetingCount × 8             (0-50)
          + timePreferencePenalty        (0-25)
          + daysOut × 2                  (0-20)
```

### Time Preference
| Hour | Penalty |
|------|---------|
| 10-11 | 0 (ideal morning) |
| 14-15 | 5 (good afternoon) |
| 9, 13 | 10 |
| 16-17 | 15 |
| Other | 25 |

### Constraints
- Working hours: 9-17 (configurable)
- Buffer: 15 min between meetings
- No weekends (configurable)
- Search window: 10 days
- Slot granularity: 30 min
- Max 5 results

---

## 10. Messaging Engine

### Cancellation Email Example

```
Subject: Unable to attend: Sprint Planning

Hi Alice,

I unfortunately need to cancel "Sprint Planning" due to a schedule conflict.
I apologize for the short notice.

I'll reach out soon to find a time that works for everyone.

Thanks for understanding.
```

### Reschedule Email Example

```
Subject: Reschedule request: Design Review

Hi Bob,

Would it be possible to move "Design Review" to Thursday, April 3 at 10:00 AM?
I need to adjust my schedule due to a scheduling adjustment.

If that time doesn't work for you, I'm happy to look at alternatives.

Thanks!
```

### Short SMS Example

```
Hi Alice — could we move "Sprint Planning" to Thu 10am? Let me know if that works.
```

### Contact Picker Fallback
When `event.attendees` is empty:
1. Show contact search UI
2. User picks email or phone number
3. Message is generated for selected contact
4. User approves before sending

---

## 11. Notification Design

### Always-Running Notification

| Stress Level | Icon Color | Priority | Behavior |
|-------------|-----------|----------|----------|
| LOW | Green | LOW | Silent, compact |
| MODERATE | Amber | DEFAULT | Normal |
| HIGH | Orange | HIGH | Expanded, action button |
| EXTREME | Red | HIGH | Heads-up alert |

### Notification Content
- **Title**: "RestGuard" (constant)
- **Text**: Dynamic headline (e.g., "Low stress — looking good")
- **Action 1**: "View Suggestions" → deep-links to recommendation list
- **Action 2**: "Refresh" → triggers stress recalculation

### Updates
- Recalculated every 15 minutes via WorkManager
- Updated immediately on significant stress change (>10 points)
- Notification ID is constant (in-place update, no spam)

---

## 12. UI / UX Design

### Screen Map

```
Onboarding → Dashboard (main)
                ├── Stress Card
                ├── Upcoming Days (predictions)
                ├── Recommendations List
                │     ├── Recommendation Detail
                │     │     ├── Reschedule Options
                │     │     ├── Cancellation Confirm
                │     │     └── Contact Picker
                │     └── Activity Detail
                ├── History / Insights
                └── Settings / Privacy
```

### UX by Stress Level

| Level | Dashboard Tone | Card Color | Urgency |
|-------|---------------|------------|---------|
| LOW | Calm, green accents | Green bg | Minimal suggestions |
| MODERATE | Warm, amber accents | Amber bg | Activity suggestions |
| HIGH | Alerting, orange | Orange bg | Reschedule/cancel cards prominent |
| EXTREME | Urgent, red | Red bg | Full-screen banner, top 3 events only |

### Key UX Principles
- Explanation-first: every suggestion shows "why" before "what"
- Reversibility: reschedules can be undone, cancellations show undo window
- Consent: no action without tap → confirm → send
- Progressive disclosure: don't overwhelm with data

---

## 13. Android Permissions & Platform Integration

### Required Permissions

| Permission | Purpose | Runtime? |
|-----------|---------|----------|
| `health.READ_HEART_RATE` | Resting HR | Yes (Health Connect) |
| `health.READ_HEART_RATE_VARIABILITY` | HRV | Yes |
| `health.READ_SLEEP` | Sleep data | Yes |
| `health.READ_STEPS` | Activity level | Yes |
| `READ_CALENDAR` / `WRITE_CALENDAR` | Calendar access | Yes |
| `READ_CONTACTS` | Contact fallback | Yes |
| `POST_NOTIFICATIONS` | Foreground notification | Yes (API 33+) |
| `FOREGROUND_SERVICE` | Always-on notification | Manifest only |
| `FOREGROUND_SERVICE_HEALTH` | Health monitoring service | Manifest only |
| `INTERNET` | LLM API calls | Manifest only |

### Platform APIs

| Integration | API | Notes |
|------------|-----|-------|
| Health data | Health Connect SDK | Requires Health Connect app installed |
| Calendar | CalendarContract | Works with any calendar app |
| Contacts | ContactsContract | Standard contacts access |
| Background | WorkManager | 15-min minimum interval |
| Notification | NotificationCompat + ForegroundService | Required for always-on |

### Limitations
- Health Connect: not all devices have it pre-installed (API 28-33 need sideload)
- WorkManager: 15-min minimum interval; actual timing is battery-optimized
- Calendar writes: some calendar providers may restrict modifications
- Foreground service: battery optimization may kill it on some OEMs

---

## 14. Safety, Privacy, and Compliance

### Consent
- First-run onboarding explains all data access
- Each permission requested with clear purpose explanation
- Health data processing explained in plain language
- User can revoke any permission at any time

### Data Minimization
- Health data stored locally only; never sent to server
- Only meeting metadata sent to LLM (not full descriptions)
- Calendar data not persisted beyond what's needed for analysis
- No location tracking

### Retention
- Stress samples: 90 days, then auto-deleted
- Meeting impact profiles: kept until user resets
- Audit logs: 30 days
- User feedback: 90 days

### Explainability
- Every recommendation includes human-readable "why"
- Stress score breakdown always visible (body/calendar/pattern)
- LLM assessments show source and confidence
- User can see historical meeting stress patterns

### User Control
- Settings: disable any data source
- Settings: adjust stress thresholds
- Settings: opt out of LLM (use rules only)
- Settings: export or delete all personal data
- Settings: pause monitoring

### Fail-Safe
- If Health Connect unavailable: graceful degradation, calendar-only scoring
- If LLM unavailable: rule-based fallback, no blocking
- If calendar unavailable: health-only mode with activity suggestions
- Never cancel without explicit confirmation
- Network failures: queue actions for retry, don't lose user intent

### Medical Disclaimer
> RestGuard is a wellness assistant designed to help you manage your schedule
> and energy levels. It is **not** a medical device, does not provide medical
> diagnoses, and should not replace professional medical advice. If you are
> experiencing persistent stress, anxiety, or health concerns, please consult
> a healthcare professional.

---

## 15. MVP Plan

### Phase 1: Mocked Prototype (Week 1-2)
- [x] Data models
- [x] Repository interfaces
- [x] Fake implementations (health, calendar, contacts, LLM)
- [x] Stress scoring service with heuristic formula
- [x] Recommendation engine with decision rules
- [x] Meeting importance assessor (rule-based)
- [x] Rescheduling engine
- [x] Messaging engine with templates
- [x] Dashboard screen (Compose)
- [x] Recommendation detail screen
- [x] Reschedule options screen
- [x] Unit tests
- **Mocked**: health data, calendar data, contacts, LLM responses

### Phase 2: Local Persistence + Notifications (Week 3-4)
- Room database for stress samples, predictions, meeting impacts
- DataStore for user preferences
- Foreground service with stress notification
- WorkManager for periodic stress updates
- Notification actions and deep links
- Onboarding flow with permission requests

### Phase 3: Real Integrations (Week 5-8)
- Health Connect SDK integration
- Calendar Provider integration
- Contacts Provider integration
- Real LLM API client (Claude API)
- Email/SMS sending via Android intents
- Subjective check-in prompts
- History/insights screen

### Phase 4: Smarter Personalization (Week 9-12)
- Historical learning with EMA
- Feature-based meeting stress profiling
- Improved LLM prompts with user context
- Wear OS companion (stretch goal)
- On-device Gemini Nano for offline classification (stretch goal)

---

## 16. Project Structure

```
restguard/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/restguard/
│       │   ├── RestGuardApp.kt                   # Hilt Application
│       │   ├── MainActivity.kt                    # NavHost entry point
│       │   ├── di/
│       │   │   └── AppModule.kt                   # Hilt dependency wiring
│       │   ├── domain/
│       │   │   ├── model/
│       │   │   │   └── Models.kt                  # All data classes and enums
│       │   │   ├── repository/
│       │   │   │   └── Repositories.kt            # All repository interfaces
│       │   │   └── service/
│       │   │       ├── StressScoringService.kt     # Stress computation
│       │   │       ├── RecommendationEngine.kt     # Decision engine
│       │   │       ├── MeetingImportanceAssessor.kt # LLM + rules
│       │   │       ├── ActivitySuggester.kt        # Recovery activities
│       │   │       ├── ReschedulingEngine.kt       # Slot finder
│       │   │       ├── MessagingEngine.kt          # Message drafting
│       │   │       └── HistoricalLearningService.kt # EMA learning
│       │   ├── data/
│       │   │   └── repository/impl/
│       │   │       ├── FakeHealthRepository.kt
│       │   │       ├── FakeCalendarRepository.kt
│       │   │       ├── FakeContactRepository.kt
│       │   │       ├── FakeLlmClient.kt
│       │   │       ├── InMemoryStressRepository.kt
│       │   │       └── InMemoryRecommendationRepository.kt
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   │   └── Theme.kt                   # Material 3 theme + stress colors
│       │   │   ├── dashboard/
│       │   │   │   ├── DashboardScreen.kt
│       │   │   │   └── DashboardViewModel.kt
│       │   │   ├── recommendation/
│       │   │   │   ├── RecommendationDetailScreen.kt
│       │   │   │   └── RecommendationDetailViewModel.kt
│       │   │   └── reschedule/
│       │   │       ├── RescheduleScreen.kt
│       │   │       └── RescheduleViewModel.kt
│       │   └── notification/
│       │       └── StressNotificationService.kt    # Notification state model
│       ├── main/res/
│       │   └── values/themes.xml
│       └── test/java/com/restguard/domain/service/
│           ├── StressScoringServiceTest.kt
│           ├── RecommendationEngineTest.kt
│           ├── MeetingImportanceAssessorTest.kt
│           └── ReschedulingEngineTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── DESIGN.md
```

---

## 17. Implementation Plan — Milestones

### Milestone 1: Core Domain (DONE)
| File | Responsibility |
|------|---------------|
| `domain/model/Models.kt` | All 14+ data classes |
| `domain/repository/Repositories.kt` | 7 repository interfaces + LLM client |
| `domain/service/StressScoringService.kt` | Heuristic stress computation |
| `domain/service/RecommendationEngine.kt` | Decision matrix + rules |
| `domain/service/MeetingImportanceAssessor.kt` | LLM + rule-based classification |
| `domain/service/ActivitySuggester.kt` | Recovery activity catalog |
| `domain/service/ReschedulingEngine.kt` | Slot finder with scoring |
| `domain/service/MessagingEngine.kt` | LLM + template message drafting |
| `domain/service/HistoricalLearningService.kt` | EMA learning logic |

### Milestone 2: Mock Data Layer (DONE)
| File | Responsibility |
|------|---------------|
| `data/repository/impl/FakeHealthRepository.kt` | Switchable health scenarios |
| `data/repository/impl/FakeCalendarRepository.kt` | Realistic meeting schedule |
| `data/repository/impl/FakeContactRepository.kt` | Sample contacts |
| `data/repository/impl/FakeLlmClient.kt` | Plausible LLM responses |
| `data/repository/impl/InMemoryStressRepository.kt` | In-memory stress storage |
| `data/repository/impl/InMemoryRecommendationRepository.kt` | In-memory recs |

### Milestone 3: UI Screens (DONE)
| File | Responsibility |
|------|---------------|
| `ui/theme/Theme.kt` | Material 3 + stress colors |
| `ui/dashboard/DashboardScreen.kt` | Main screen with stress card |
| `ui/dashboard/DashboardViewModel.kt` | Dashboard state management |
| `ui/recommendation/RecommendationDetailScreen.kt` | Suggestion detail + actions |
| `ui/recommendation/RecommendationDetailViewModel.kt` | Detail state |
| `ui/reschedule/RescheduleScreen.kt` | Time slot picker |
| `ui/reschedule/RescheduleViewModel.kt` | Reschedule state |

### Milestone 4: App Shell (DONE)
| File | Responsibility |
|------|---------------|
| `RestGuardApp.kt` | Hilt application |
| `MainActivity.kt` | Navigation host |
| `di/AppModule.kt` | Dependency wiring |
| `notification/StressNotificationService.kt` | Notification model |
| `AndroidManifest.xml` | Permissions + components |

### Milestone 5: Tests (DONE)
| File | Responsibility |
|------|---------------|
| `StressScoringServiceTest.kt` | Physiological, calendar, classification |
| `RecommendationEngineTest.kt` | Same-day restriction, extreme mode, scenarios |
| `MeetingImportanceAssessorTest.kt` | Keyword matching, structural signals |
| `ReschedulingEngineTest.kt` | Slot finding, constraints, ranking |

### Milestone 6: Phase 2 — Persistence (TODO)
- Room entities + DAOs
- Room database
- DataStore preferences
- Migration from in-memory to Room

### Milestone 7: Phase 2 — Background Work (TODO)
- StressMonitorWorker (periodic WorkManager)
- StressForegroundService
- Notification channel creation
- Deep link handling

### Milestone 8: Phase 3 — Real Integrations (TODO)
- HealthConnectRepository
- CalendarProviderRepository
- ContactsProviderRepository
- ClaudeApiClient (Retrofit)
- Permission flow UI

---

## 19. Testing Strategy

### Unit Test Coverage

| Area | What to Test | Test File |
|------|-------------|-----------|
| Stress scoring | Each component independently, combined score, thresholds, missing data | StressScoringServiceTest |
| Recommendation rules | Decision matrix, same-day restriction, extreme mode cap, explanation presence | RecommendationEngineTest |
| Meeting importance | Keyword matching, structural signals, confidence, LLM fallback | MeetingImportanceAssessorTest |
| Rescheduling | Slot generation, working hours, buffer, ranking, no same-day | ReschedulingEngineTest |
| Historical learning | EMA updates, feature extraction, pattern key derivation | (TODO) HistoricalLearningServiceTest |
| Messaging | Template generation, LLM draft, missing contact handling | (TODO) MessagingEngineTest |
| Notification | State building, heads-up logic | (TODO) StressNotificationServiceTest |

### Integration Test Strategy (Phase 2+)
- Room DAO tests with in-memory database
- WorkManager tests with TestDriver
- Compose UI tests for critical flows (dashboard load, recommendation tap, reschedule confirm)

### Key Test Invariants
1. Same-day cancellation is NEVER produced unless stress >= EXTREME (80)
2. Extreme mode NEVER returns more than 3 event candidates
3. Every recommendation has a non-empty explanation
4. Stress scores are always 0-100
5. Confidence is always 0.0-1.0
6. Reschedule slots are always within working hours
7. Reschedule slots never overlap with existing events
8. LLM parsing failures always fall back to rule-based

---

## 20. Scenario Walkthroughs

### Scenario 1: Moderate stress, future overload → reschedule

**State**: User slept 5.5h, HRV 28ms, resting HR 72. Current stress: ~55 (MODERATE).
Tomorrow has 7 meetings including 3 back-to-back.

**Flow**:
1. WorkManager fires → StressScoringService computes score 55
2. Prediction for tomorrow: 72/100 (HIGH)
3. RecommendationEngine: MODERATE + future HIGH → suggest reschedule + activity
4. MeetingImportanceAssessor classifies tomorrow's "Book Club" as LOW
5. Recommendation: "Tomorrow is predicted at 72/100. 'Book Club' could be rescheduled"
6. User taps → sees explanation → taps "Find New Time"
7. ReschedulingEngine finds 5 slots, ranked by stress score
8. User picks Thursday 2pm (stress: 25/100, 2 meetings)
9. MessagingEngine drafts reschedule email
10. User reviews, edits slightly, sends via email app

### Scenario 2: High stress, low-importance meeting tomorrow → cancel

**State**: User slept 4h, HRV 18ms. Current stress: ~72 (HIGH).
Tomorrow has "All-Hands Town Hall" (50 attendees, recurring).

**Flow**:
1. Score: 72 → HIGH stress
2. "All-Hands Town Hall" → LOW importance (large, recurring, likely recorded)
3. Recommendation: "Suggest cancelling 'All-Hands Town Hall' — large informational meeting, typically recorded"
4. User taps → sees why + message preview
5. No attendee notification needed for all-hands (or user sends brief decline)
6. User confirms → event deleted from calendar
7. Audit log records the action

### Scenario 3: Extreme stress today → same-day intervention

**State**: User slept 4h, HRV 15ms, resting HR 85. Current stress: 88 (EXTREME).
Today has 6 remaining meetings.

**Flow**:
1. Score: 88 → EXTREME stress
2. RecommendationEngine: EXTREME mode activated
3. Skips normal ranking → direct priority assessment of today's 6 events
4. Top 3 lowest-importance: "Coffee Chat" (LOW), "All-Hands" (LOW), "Sprint Planning" (MEDIUM)
5. Notification: RED, heads-up alert: "Extreme stress — 3 urgent suggestions"
6. User opens app → sees red stress card (88/100)
7. 3 event cards with "URGENT — Same-Day Action" badge
8. Each card: explanation, estimated stress reduction, cancel/reschedule buttons
9. User cancels "Coffee Chat" → confirms → message sent to Designer
10. User reschedules "All-Hands" decline → marks as declined
11. Stress notification updates: still HIGH but improving
