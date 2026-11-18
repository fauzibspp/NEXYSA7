# AGENTS.md — Nexys A7 FPGA Cryptographic Companion (Android)

## Project Overview
Native Android application written in **Kotlin** (Jetpack Compose, MVVM, Kotlin Coroutines/Flow) that acts as a mobile client for a hardware encryption/decryption engine running on a **Digilent Nexys A7 (XC7A100T-1CSG324C)** FPGA board.

## Hardware & Protocol Target (Future Integration)
- **Target Board:** Digilent Nexys A7-100T (`XC7A100T-1CSG324C`).
- **Transports:**
  1. **USB Serial:** Digilent onboard FTDI FT2232HQ USB-UART bridge (default 115200 baud, 8N1).
  2. **BLE:** Digilent Pmod BLE (Microchip RN4870) UART Transparent Service (`49535343-FE7D-4AE5-8FA9-9FAFD205E455`).
- **Framing Protocol:** Design a clean packet framing layer (`[START_BYTE][CMD: ENCRYPT/DECRYPT][PAYLOAD_LEN][PAYLOAD][CRC16]`) so real hardware drivers can drop in later without changing UI or ViewModel code.

## Current Phase: Hardware Simulation Mode
- **We do NOT have the physical Nexys A7 board yet.**
- All transport layers (`BleFpgaTransport`, `UsbSerialFpgaTransport`) and the FPGA engine must run behind a clean `FpgaTransport` interface with a `SimulatedFpgaTransport` implementation enabled by default.
- Simulation must realistically mimic connection handshake, transmission latency, chunked progress for files, and deterministic mock encryption/decryption (e.g., reversible AES-128 or XOR-hex framing) so round-trip Encrypt -> Decrypt verifies cleanly.

## Architecture & Tech Stack
- **Language:** Kotlin 2.x, Java 17 target.
- **UI:** Jetpack Compose (Material 3) with a 2-tab layout: **Text Encryption** and **File Encryption**, plus a top connection status bar (Disconnected / Simulated / BLE / USB Serial).
- **DI:** Hilt or clean manual dependency container (`AppContainer`) so swapping `SimulatedFpgaTransport` for real `UsbSerialFpgaTransport` / `BleFpgaTransport` requires changing a single binding.
- **Libraries to prepare:** `usb-serial-for-android` (for FTDI UART) and Android `BluetoothGatt` / Nordic BLE abstractions (stubbed cleanly for now).

## Build & Verification Rules
- Always verify changes by running `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` before finishing a task.
- Keep UI stateless; hoist all state to ViewModels exposing `StateFlow`.
- Never hardcode mock logic inside Composables or ViewModels—isolate all simulation inside the data/transport layer.