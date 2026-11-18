#!/usr/bin/env bash
set -euo pipefail
node security/seed-voice-emulators.cjs
node voice-relay/fixture.mjs > stream-fixture.log 2>&1 &
VOICE_FIXTURE_PID=$!
trap 'kill "$VOICE_FIXTURE_PID" 2>/dev/null || true' EXIT
VOICE_FIXTURE_READY=""
for attempt in $(seq 1 30); do
  if curl --silent --fail http://127.0.0.1:8787/health >/dev/null; then
    VOICE_FIXTURE_READY=1
    break
  fi
  sleep 1
done
if [ -z "$VOICE_FIXTURE_READY" ]; then
  cat stream-fixture.log
  exit 1
fi
VOICE_DEVICE_RESULT=0
./gradlew connectedDebugAndroidTest -PcodecTest=1 -Pandroid.testInstrumentationRunnerArguments.class=com.tony.nosotros.voice.VoiceCodecDeviceTest,com.tony.nosotros.voice.HoldGestureTest,com.tony.nosotros.voice.PeerConnectionDeviceTest,com.tony.nosotros.voice.RelayDeviceTest,com.tony.nosotros.voice.SocketVoiceRelayDeviceTest || VOICE_DEVICE_RESULT=$?
node security/inspect-voice-emulator.cjs
adb logcat -d > emulator-logcat.txt
exit "$VOICE_DEVICE_RESULT"
