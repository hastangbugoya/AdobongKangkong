# KusgangAliwas Wear OS Remote Plan

**Document purpose:** Preserve the planned Wear OS gym remote design so it can be implemented later without losing the original intent.

**Project:** KusgangAliwas / KA  
**Feature area:** Hands-free gym session control  
**Status:** Planned / deferred until a Wear OS test device is available  
**Core decision:** Wear OS remote control should use the same command model and reducer logic as the existing Bluetooth media-button remote.

---

## 1. Goal

The Wear OS app should act as a visual remote control for an active KA gym session on the phone.

The Wear OS remote should not become a separate workout logger in the first version. The phone remains the source of truth for the active session, current exercise, current set, current input field, timers, and saved logs.

The watch should provide a small, gym-friendly control surface that lets the user operate the same hands-free flow already used by the Bluetooth remote.

---

## 2. Main Design Rule

The Wear OS remote and the Bluetooth remote should behave the same at the command level.

The difference is only the feedback surface:

| Input Surface | User Action | Feedback |
|---|---|---|
| Bluetooth media remote | Media button press | Audible cue / TTS |
| Wear OS watch app | On-screen button tap | Visible text + haptics |

Both should send the same remote command intents into the same session-control logic.

---

## 3. Controls

The Wear OS remote should expose the same logical controls as the Bluetooth remote:

- `Up` / `+`
- `Down` / `-`
- `Previous`
- `Next`
- `Confirm`

Although this is sometimes casually described as a “four-button remote,” the actual useful control set is five commands because `Confirm` should remain separate from navigation.

Recommended Wear OS button layout:

```text
Current Exercise

Set 2
Weight: 120 lb
Reps: 10

[ - ]       [ + ]

[ Prev ] [ Confirm ] [ Next ]
```

Alternative compact layout for small round screens:

```text
      +
Prev  OK  Next
      -
```

Where:

- `+` / `Up` adjusts the selected value upward or moves selection upward depending on the current state.
- `-` / `Down` adjusts the selected value downward or moves selection downward depending on the current state.
- `Previous` moves backward through the flow.
- `Next` moves forward through the flow.
- `Confirm` accepts the current field, confirms a set, or performs the current highlighted action.

---

## 4. Existing Bluetooth Remote Mental Model

The existing Bluetooth remote already follows the correct concept:

```text
Exercise picker
→ Set 1 weight
→ Set 1 reps
→ Confirm set
→ Set 2 weight
→ Set 2 reps
→ Confirm set
→ Next exercise
```

Wear OS should reuse this exact state flow.

The watch should not introduce a second flow such as “watch-specific set logging.” It should only visualize and control the same active remote state.

---

## 5. Rationale

### 5.1 Avoid duplicate workout logic

If Bluetooth remote logic and Wear OS logic are implemented separately, the app can drift into two different behaviors:

- Bluetooth confirms one way.
- Wear OS confirms another way.
- Bugs get fixed in one control path but not the other.
- Future pace-profile changes need to be patched twice.

Using a single reducer/state machine prevents this.

### 5.2 Phone remains source of truth

The phone app already owns the database, active session state, actual exercise logs, set logs, cardio logs, pace profiles, and audible feedback system.

The watch should only display remote state and send commands. This keeps the first Wear OS version simpler and avoids premature sync complexity.

### 5.3 Wear OS is a visual remote, not a full KA clone

A full Wear OS version of KA would require screens for exercise picking, editing, logging, session recovery, sync, error handling, and persistence.

The first version should be intentionally smaller:

```text
Phone = session owner
Watch = visual remote
Bluetooth remote = blind/audible remote
```

### 5.4 Gym-friendly interaction

A watch remote is useful because it is always on the wrist. It avoids pulling the phone out during sets.

The watch should favor:

- Large buttons
- Short labels
- Minimal typing
- Strong visibility
- Haptics instead of sound
- Quick glance state

---

## 6. Shared Command Model

Create or formalize a shared remote intent enum.

Suggested model:

```kotlin
enum class GymRemoteIntent {
    Up,
    Down,
    Previous,
    Next,
    Confirm
}
```

Both Bluetooth and Wear OS should map into this enum.

Example mapping:

```kotlin
fun mediaKeyToRemoteIntent(keyCode: Int): GymRemoteIntent? {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> GymRemoteIntent.Up
        KeyEvent.KEYCODE_VOLUME_DOWN -> GymRemoteIntent.Down
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> GymRemoteIntent.Previous
        KeyEvent.KEYCODE_MEDIA_NEXT -> GymRemoteIntent.Next
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> GymRemoteIntent.Confirm
        else -> null
    }
}
```

The Wear OS app would map button taps directly:

```kotlin
onPlusClicked = { send(GymRemoteIntent.Up) }
onMinusClicked = { send(GymRemoteIntent.Down) }
onPreviousClicked = { send(GymRemoteIntent.Previous) }
onNextClicked = { send(GymRemoteIntent.Next) }
onConfirmClicked = { send(GymRemoteIntent.Confirm) }
```

---

## 7. Shared Reducer Concept

The existing or future reducer should remain the single place where command behavior is decided.

Conceptual model:

```kotlin
data class GymRemoteResult(
    val newState: GymRemoteState,
    val feedback: GymRemoteFeedback
)

object GymRemoteReducer {
    fun reduce(
        state: GymRemoteState,
        intent: GymRemoteIntent
    ): GymRemoteResult {
        // Existing Bluetooth remote state transition logic lives here.
    }
}
```

The reducer should not care whether the command came from:

- Bluetooth media button
- Wear OS button
- Phone screen test button
- Future accessibility input

That keeps the system testable.

---

## 8. Feedback Model

The reducer should return feedback in a neutral format. The output surface decides how to present it.

Possible feedback model:

```kotlin
sealed interface GymRemoteFeedback {
    data class Speak(val text: String) : GymRemoteFeedback
    data class ShowMessage(val text: String) : GymRemoteFeedback
    data class Error(val text: String) : GymRemoteFeedback
    data object Confirmed : GymRemoteFeedback
    data object InvalidAction : GymRemoteFeedback
}
```

Then each surface adapts it:

### Bluetooth surface

- Uses TTS
- May use audio ducking
- Prioritizes short spoken cues
- Examples:
  - “Bench press, set two, weight one twenty.”
  - “Reps ten.”
  - “Set saved.”
  - “Invalid.”

### Wear OS surface

- Uses visible text
- Uses haptics
- Avoids long paragraphs
- Examples:
  - `Bench Press`
  - `Set 2`
  - `Weight: 120 lb`
  - `Reps: 10`
  - `Set saved`
  - `Invalid action`

---

## 9. Wear OS Screen States

The watch app should show the current remote state in a compact visual form.

### 9.1 No active session

```text
No active KA session

Start or resume a session
on your phone.
```

Buttons may be disabled.

### 9.2 Exercise picker state

```text
Choose Exercise

Bench Press
2 of 5

[Prev] [OK] [Next]
```

`Up` / `Down` or `Previous` / `Next` can move through the list depending on the existing reducer behavior.

### 9.3 Weight input state

```text
Bench Press

Set 2
Weight
120 lb

[-] [+]
[Prev] [OK] [Next]
```

### 9.4 Reps input state

```text
Bench Press

Set 2
Reps
10

[-] [+]
[Prev] [OK] [Next]
```

### 9.5 Rest timer state

```text
Rest

Next: Bench Press Set 3
00:47

[Prev] [OK] [Next]
```

Possible behavior:

- `Confirm` may skip/accept the rest and move forward.
- `Next` may move to the next state.
- `Previous` may return to the previous input state.
- `+` / `-` may adjust rest time only if later desired.

### 9.6 Set saved state

```text
Set saved

Bench Press
120 x 10
```

This can be a short transient message before returning to the next state.

---

## 10. Haptic Feedback Plan

Wear OS should use haptics because the user may not want watch sounds in the gym.

Suggested haptic meanings:

| Event | Haptic |
|---|---|
| Button accepted | Light tick |
| Set confirmed | Stronger pulse |
| Invalid action | Double tick |
| Rest finished | Long pulse |
| Disconnected | Double/long warning pulse |

Do not overuse haptics. They should confirm important actions without becoming annoying.

---

## 11. Communication Between Phone and Watch

The first version should use the phone as the active session owner.

Possible implementation options:

### Option A: Wear OS Data Layer message commands

Use the Wear OS Data Layer APIs to send command messages from the watch to the phone.

Conceptual flow:

```text
Wear button tap
→ send GymRemoteIntent to phone
→ phone applies reducer
→ phone saves/updates session if needed
→ phone sends updated GymRemoteState back to watch
→ watch redraws visible state
```

This is likely the cleanest first real-device approach.

### Option B: Watch app as companion only, phone app foreground required

This is acceptable for MVP.

The first version can require:

- KA session is active on the phone.
- Phone is nearby.
- Watch is connected.
- The phone owns all session state.
- The watch only mirrors the current remote state.

### Option C: Standalone watch session

Do not implement this first.

Standalone logging would require local persistence, sync conflict handling, disconnected mode, and recovery behavior. That is a separate future feature.

---

## 12. Recommended Architecture

Recommended conceptual modules/classes:

```text
shared remote command model
    GymRemoteIntent
    GymRemoteState
    GymRemoteFeedback
    GymRemoteReducer

phone app
    BluetoothMediaButtonReceiver
    GymRemoteViewModel
    GymRemoteCommandSink
    GymRemoteFeedbackSpeaker
    WearRemoteMessageReceiver
    WearRemoteStatePublisher

wear app
    WearRemoteScreen
    WearRemoteViewModel
    WearRemoteCommandSender
    WearRemoteStateObserver
    WearHaptics
```

### Command sink interface

```kotlin
interface GymRemoteCommandSink {
    suspend fun send(intent: GymRemoteIntent)
}
```

Phone implementation:

```kotlin
class PhoneGymRemoteCommandSink(
    private val reducerController: GymRemoteController
) : GymRemoteCommandSink {
    override suspend fun send(intent: GymRemoteIntent) {
        reducerController.handle(intent)
    }
}
```

Bluetooth uses this sink directly inside the phone app.

Wear OS sends a message to the phone, and the phone-side receiver passes that command into the same sink.

---

## 13. Implementation Steps

### Step 1: Formalize the existing remote command enum

Create the shared `GymRemoteIntent` enum if it does not already exist.

Goal:

```text
No Bluetooth-specific command names should leak into the reducer.
```

Bad:

```text
MediaNextPressed
VolumeUpPressed
PlayPausePressed
```

Good:

```text
Next
Up
Confirm
```

### Step 2: Make Bluetooth mapping explicit

Refactor Bluetooth/media-button handling so it clearly maps hardware key codes into `GymRemoteIntent`.

This confirms that Bluetooth is just one input surface.

### Step 3: Confirm reducer purity

Keep as much state transition logic as possible inside a pure reducer:

```text
old state + intent = new state + feedback
```

This makes it easy to test without phone UI, Wear OS, Bluetooth, or audio.

### Step 4: Add state snapshot for remote surfaces

Define the compact state needed by remote displays.

Example:

```kotlin
data class GymRemoteDisplayState(
    val title: String,
    val subtitle: String?,
    val primaryValueLabel: String?,
    val primaryValue: String?,
    val secondaryInfo: String?,
    val message: String?,
    val canUp: Boolean,
    val canDown: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canConfirm: Boolean
)
```

The watch does not need the entire session database model. It only needs a display-ready snapshot.

### Step 5: Add phone-side state publisher

The phone app should expose the current remote display state to the watch.

Initial behavior can be simple:

- When watch connects, send current state.
- After every command, send updated state.
- When session changes on phone, send updated state.
- When session ends, send “No active session.”

### Step 6: Create Wear OS module

Add a Wear OS app module later when hardware is available.

Recommended early Wear OS MVP:

- One Activity
- Compose for Wear OS
- One remote screen
- Five controls
- Connection state text
- Haptics
- No database

### Step 7: Implement Wear OS command sender

Each watch button sends a `GymRemoteIntent` to the phone.

The watch should not decide business rules. For example, the watch should not decide whether `Confirm` saves a set. The phone reducer decides that.

### Step 8: Implement Wear OS state receiver

The watch receives display state from the phone and redraws.

The watch should show helpful fallback states:

- `Connecting to phone...`
- `No active KA session`
- `Session ended`
- `Command failed`
- `Phone not reachable`

### Step 9: Add haptic feedback

Add haptics after the core command loop works.

Suggested order:

1. Button tap haptic
2. Confirm/save haptic
3. Invalid action haptic
4. Rest complete haptic

### Step 10: Test with emulator first, real watch later

The Wear OS emulator can be used for basic UI layout and button tests.

A real watch is still needed for:

- Wrist usability
- Button size
- Haptics
- Connection reliability
- Gym practicality
- Screen timeout behavior

---

## 14. Testing Plan

### 14.1 Reducer tests

These should not require Wear OS or Bluetooth.

Test examples:

- `Up` increases weight in weight-entry state.
- `Down` decreases reps in reps-entry state.
- `Confirm` saves a set when weight and reps are valid.
- `Next` moves from weight to reps.
- `Previous` moves backward correctly.
- Invalid actions return invalid feedback instead of crashing.
- Set confirmation still works the same after the Wear OS command path is added.

### 14.2 Bluetooth regression tests

Make sure existing Bluetooth behavior does not change.

Important checks:

- Media next still maps to `Next`.
- Media previous still maps to `Previous`.
- Volume up/down still map to `Up` / `Down`.
- Play/pause still maps to `Confirm`.
- TTS cues still work.
- Music-control behavior is not worsened.

### 14.3 Wear OS UI tests

Basic checks:

- All five buttons render on round and square watch layouts.
- Text is readable.
- Buttons are large enough.
- Disabled actions look disabled.
- Long exercise names do not break the screen.
- Rest timer state is readable at a glance.

### 14.4 Phone-watch command tests

- Watch `+` changes the same state as Bluetooth `Volume Up`.
- Watch `-` changes the same state as Bluetooth `Volume Down`.
- Watch `Next` changes the same state as Bluetooth media next.
- Watch `Previous` changes the same state as Bluetooth media previous.
- Watch `Confirm` changes the same state as Bluetooth play/pause.
- Phone state updates after watch command.
- Watch state updates after phone-side session change.

### 14.5 Real gym testing

Use only after basic correctness is confirmed.

Check:

- Can the user operate it while tired?
- Are buttons too small?
- Is visible text enough without audio?
- Are haptics helpful?
- Does the screen sleep too aggressively?
- Does accidental touch cause problems?
- Is `Confirm` easy to hit without hitting `Next`?
- Does the watch stay connected during a full workout?

---

## 15. UI Guidelines for the Watch

Keep the Wear OS screen simple.

Use:

- Very short labels
- Large tap targets
- High contrast
- One main value at a time
- No dense lists unless absolutely needed
- Haptics for confirmation
- Clear disconnected state

Avoid:

- Full workout editing
- Food-style forms
- Tiny text
- Multi-screen navigation for MVP
- Typing
- Complex charts
- Recreating the entire phone UI

The user should be able to glance at the watch and understand:

```text
What exercise am I on?
What set am I on?
What value am I editing?
What happens if I press confirm?
```

---

## 16. MVP Definition

The first useful Wear OS MVP is complete when:

- The phone has an active KA session.
- The watch shows the current remote state.
- The watch has `+`, `-`, `Previous`, `Next`, and `Confirm`.
- Watch commands update the phone session through the same reducer as Bluetooth.
- The watch gives visible feedback.
- The watch gives basic haptic feedback.
- Existing Bluetooth remote behavior still works.

Not required for MVP:

- Standalone watch logging
- Watch-side database
- Offline mode
- Full exercise browser
- Full split browser
- Full session editor
- Charts
- Complications
- Tiles
- Play Store polish

---

## 17. Future Enhancements

After MVP, possible enhancements:

### 17.1 Wear OS tile

A tile could show:

```text
KA Session
Bench Press
Rest 00:47
```

With a tap opening the remote screen.

### 17.2 Watch complication

Possible complication data:

- Active rest timer
- Current exercise
- Session active indicator
- Next set countdown

### 17.3 Rest timer haptics

The watch can become the best place for rest timer reminders because haptics are discreet.

Possible behavior:

- Light buzz when almost time
- Strong buzz when rest is over
- Optional repeated reminder if user does not start next set

### 17.4 Quick session start

Future version might allow:

- Start last split
- Resume active session
- Start next cycle step

But this should wait until the remote MVP is stable.

### 17.5 Disconnected mode

Only consider later.

Disconnected watch logging would require:

- Local watch persistence
- Sync conflict rules
- Recovery after reconnect
- Duplicate prevention
- Merge strategy with phone logs

This is out of scope for the first version.

---

## 18. Open Questions

These do not block the plan, but should be decided during implementation:

1. Should `+` / `-` always adjust values, or sometimes move through lists?
2. Should `Previous` and `Next` handle exercise selection while `+` / `-` only adjust numeric values?
3. Should `Confirm` during rest skip rest or only acknowledge the current state?
4. Should the watch screen stay awake during active rest timers?
5. Should the phone still speak TTS when the Wear OS remote is being used?
6. Should Wear OS mode be silent by default?
7. Should Wear OS have a setting for haptic strength?
8. Should invalid actions show a visible message only, or haptic + message?

Initial recommendation:

- Keep Bluetooth mode audible.
- Keep Wear OS mode visual + haptic.
- Do not make the phone speak every Wear OS action unless the user explicitly enables that later.

---

## 19. Final Design Summary

The Wear OS remote should be a visual version of the existing Bluetooth audible remote.

The correct architecture is:

```text
Bluetooth remote
        ↓
GymRemoteIntent
        ↓
GymRemoteReducer
        ↓
Phone session state
        ↓
TTS feedback

Wear OS remote
        ↓
GymRemoteIntent
        ↓
GymRemoteReducer
        ↓
Phone session state
        ↓
Visible text + haptics
```

This keeps KA consistent, testable, and easier to expand later.

The implementation should prioritize shared command logic first, then add Wear OS as another input/output surface.
