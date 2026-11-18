# Nexys A7 Crypto — Android Companion App

Kotlin / Jetpack Compose client for a hardware encryption engine on a
Digilent Nexys A7-100T (`XC7A100T-1CSG324C`). The current phase is **hardware
simulation**: the UI, ViewModels and repository layer are real, while the FPGA
link is a deterministic simulated transport behind a clean interface.

## What is implemented

- Material 3 Compose app with a top bar containing:
  - a connection selector (**Disconnected / Simulated USB / Simulated BLE**),
  - a Connect/Disconnect toggle,
  - a live badge for the target **XC7A100T-1CSG324C**.
- Two tabs — **Text Encryption** and **File Encryption** — with state preserved
  across tab switches (ViewModel state plus a `SaveableStateHolder` for
  rememberSaveable UI state such as scroll position).
- Text tab: a single editor that holds both plaintext and ciphertext, a
  **UTF-8 / Hex** format selector, Encrypt/Decrypt, Copy/Swap/Clear, and a
  simulated latency/throughput readout.
- File tab: Storage Access Framework `OpenDocument`/`CreateDocument`, file size
  and chunk-plan preview, chunked progress, Encrypt/Decrypt, and Save Output.
- Stateless UI driven by ViewModels exposing `StateFlow`.
- Hardware-agnostic `FpgaTransport`; `SimulatedTransportHub` switches between
  the USB and BLE simulation profiles.
- `SimulatedFpgaTransport` with connection-handshake delays, per-chunk transfer
  latency, chunked progress and a reversible simulated engine.
- Nexys packet framer with CRC-16/CCITT-FALSE validation.
- Compile-ready `UsbSerialFpgaTransport` (FTDI FT2232HQ, 115200 8N1) and
  `BleFpgaTransport` (RN4870 Transparent UART) stubs with `TODO(HARDWARE)`
  markers at every physical I/O point.
- Unit tests for framing, CRC, hex encoding, hub link switching, the
  single-editor ViewModel and simulated text/file round-trips.

## Architecture

```
ui/ (stateless Composables)
  └── ViewModels (StateFlow)
        └── domain/transport/FpgaTransport
              └── data/transport/SimulatedTransportHub
                    ├── SimulatedFpgaTransport (USB profile)
                    └── SimulatedFpgaTransport (BLE profile)
```

`di/AppContainer.kt` contains the simulation bindings:

```kotlin
private val simulatedTransportHub = SimulatedTransportHub()
val transport: FpgaTransport = simulatedTransportHub
val simulatedTransportControl: SimulatedTransportControl = simulatedTransportHub
```

The Text/File ViewModels depend only on `FpgaTransport`; the connection UI uses
`SimulatedTransportControl` for link selection. Hardware integration replaces
these bindings and the simulation-only selector, not the feature ViewModels.

## Wire protocol

Frame layout (all multi-byte fields are big-endian):

| Offset | Size | Field |
|---|---|---|
| 0 | 1 | `START_BYTE` = `0xAA` |
| 1 | 1 | `COMMAND`: `0x01` ENCRYPT, `0x02` DECRYPT |
| 2 | 4 | payload length (unsigned 32-bit, max 8 MiB) |
| 6 | N | payload |
| 6 + N | 2 | CRC-16/CCITT-FALSE (poly `0x1021`, init `0xFFFF`, no reflection, xor-out `0`) |

The CRC covers the command, length and payload bytes (frame offsets
`1 until 6 + N`); the start byte is not covered. `NexysPacketFramer.decode`
returns a typed failure (`TOO_SHORT`, `INVALID_START_BYTE`, `UNKNOWN_COMMAND`,
`PAYLOAD_TOO_LARGE`, `LENGTH_MISMATCH`, `CRC_MISMATCH`) instead of throwing.

## Simulation details

- **Link profiles** (`SimulatedLink`):
  - Simulated USB: 1024-byte chunks, 350 ms connect, 150 ms handshake,
    12 ms per chunk, 30 ms ack.
  - Simulated BLE: 244-byte chunks (RN4870 247-byte MTU minus the 3-byte ATT
    header), 700 ms connect, 300 ms handshake, 18 ms per chunk, 45 ms ack.
- **Connect**: `Disconnected → Connecting → Connected`; switching links
  disconnects the previous link first.
- **Transfers**: payloads are processed in chunks. To keep multi-megabyte UI
  transfers usable, progress and delay steps are capped at 64 while every
  chunk is still processed. Each step advances `TransferProgress`; the final
  callback reports 100 %.
- **Crypto core**: deterministic XOR keystream (`SimulatedCryptoEngine`).
  This is **not cryptography** and exists only to make Encrypt → Decrypt
  round-trips verifiable. It is isolated in the data layer.
- **Framing**: both the request and response pass through `NexysPacketFramer`,
  so the simulation exercises CRC validation on every operation.
- **Text UI**: one editor holds both forms. Encrypt writes hex ciphertext back
  into the editor and switches the format to Hex; Decrypt restores readable
  UTF-8 when possible (otherwise hex). Swap toggles the current content with the
  previous content/format.
- **File UI**: raw binary bytes, maximum 8 MiB (streamed read cap). The preview
  shows the first 16 bytes, the chunk count and the chunk size for the selected
  simulated link. Output is saved through SAF.
- **State**: ViewModels survive tab switches, and a `SaveableStateHolder`
  preserves each tab's `rememberSaveable` UI state.

## Build and test

Requirements: JDK 17+ (the project targets Java 17 bytecode), Android SDK with
platform 36 and build-tools 36.0.0. `local.properties` points at the local SDK
and is gitignored.

```powershell
# Windows
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

```bash
# macOS / Linux
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The first run downloads Gradle and the AndroidX/Compose dependencies. Test
results are written to `app/build/reports/tests/testDebugUnitTest/` and
`app/build/test-results/testDebugUnitTest/`.

The current suite contains **41 JVM unit tests** covering packet framing, CRC,
hex encoding, transport-hub link switching, simulated text/file round-trips and
the single-editor text ViewModel state transitions.

## Hardware integration checklist

Search the data layer for `TODO(HARDWARE)`:

- **USB serial** (`UsbSerialFpgaTransport`): USB permission flow, open the
  FT2232HQ through usb-serial-for-android, configure 115200 8N1, start a reader
  loop, decode frames with `NexysPacketFramer`, perform the FPGA handshake, and
  write framed requests chunk by chunk.
- **BLE** (`BleFpgaTransport`): runtime permissions, scan/connect to the
  RN4870, discover the Transparent UART service, enable notifications on the TX
  characteristic, chunk writes to `MTU - 3` bytes on RX, and feed notifications
  into the shared frame decoder.

Transparent UART UUIDs (verified against Microchip documentation):

| Purpose | UUID |
|---|---|
| Service | `49535343-FE7D-4AE5-8FA9-9FAFD205E455` |
| TX (module → phone, notify) | `49535343-1E4D-4BD9-BA61-23C647249616` |
| RX (phone → module, write) | `49535343-8841-43F4-A8D4-ECBE34729BB3` |
| Control point | `49535343-4C8A-39B3-2F49-511CFF073B7E` |

## Notes

- `AGENTS.md` remains the source of truth for project constraints.
- The simulated engine must never be used as a real security primitive.
- Release signing and minification are intentionally out of scope for this
  scaffold.
